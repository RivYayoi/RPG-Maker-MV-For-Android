package com.rmmv.editor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.rmmv.editor.core.MvMap;
import com.rmmv.editor.core.Project;
import com.rmmv.editor.core.TileId;
import com.rmmv.editor.core.Tileset;

import org.json.JSONObject;

/**
 * Native Map View -- the PC editor's (5) Map View.
 *
 * <p>Replaces the WebView+canvas pair of the previous build with a real Android
 * {@link View}: hardware-accelerated {@link Canvas}, {@link ScaleGestureDetector}
 * for pinch-zoom and a {@link GestureDetector} for tap / long-press / pan.
 *
 * <p>Desktop behaviour preserved:
 * <ul>
 *   <li>4 map layers drawn bottom-up (z = 0..3), exactly as MV stacks them</li>
 *   <li>Tile grid overlay (PC: Map View grid)</li>
 *   <li>Event markers drawn on top, sized to the tile</li>
 *   <li>Status readout (x, y, layer, tileId) surfaced through {@link #statusText()}</li>
 * </ul>
 */
public class MapCanvasView extends View {

    public static final int MODE_MAP = 0;      // PC [Mode] > Map
    public static final int MODE_EVENT = 1;    // PC [Mode] > Event
    public static final int MODE_REGION = 2;   // PC [Mode] > Region (the R tab)

    private Project project;
    private Tileset tileset;

    private float scale = 1f;
    private float offX, offY;
    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 5f;

    private int mode = MODE_MAP;
    private int layer = 0;                     // active map layer 0..3
    private int brush = 0;                     // selected tileId, 0 = erase
    private boolean showGrid = true;
    private int selectedEventId = -1;
    private boolean strokeStarted = false;

    // listener contract
    public interface Callbacks {
        void onTilePainted(int x, int y, int z);
        void onStatus(String status);
        void onEventTapped(int eventId, boolean created);
        void onEventLongPress(int eventId, float screenX, float screenY);
        void onScaleChanged(float scale);
    }

    private Callbacks cb;

    // paints
    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF dst = new RectF();

    // selection rectangle (PC rectangular map-data selection)
    private int selX0 = -1, selY0 = -1, selX1 = -1, selY1 = -1;

    public MapCanvasView(Context c) { super(c); init(); }
    public MapCanvasView(Context c, AttributeSet a) { super(c, a); init(); }
    public MapCanvasView(Context c, AttributeSet a, int b) { super(c, a, b); init(); }

    private void init() {
        gridPaint.setColor(0x22FFFFFF);
        gridPaint.setStrokeWidth(1f);
        borderPaint.setColor(0xFF4A8CFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        eventFill.setColor(0xD9E2564D);
        eventStroke.setColor(0x99000000);
        eventStroke.setStyle(Paint.Style.STROKE);
        eventText.setColor(Color.WHITE);
        eventText.setTextAlign(Paint.Align.CENTER);
        selFill.setColor(0x334A8CFF);
        selFill.setStyle(Paint.Style.FILL);
        eventText.setFakeBoldText(true);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new TapListener());
        gestureDetector.setIsLongpressEnabled(true);
    }

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // ------------------------------------------------------------- lifecycle

    public void setProject(Project p) {
        this.project = p;
        this.tileset = p != null ? p.tileset() : null;
        invalidate();
    }

    public void setCallbacks(Callbacks c) {
        this.cb = c;
    }

    public void refreshTileset() {
        this.tileset = project != null ? project.tileset() : null;
        invalidate();
    }

    public void setMode(int m) { mode = m; invalidate(); }
    public int mode() { return mode; }
    public void setLayer(int z) { layer = Math.max(0, Math.min(3, z)); invalidate(); }
    public int layer() { return layer; }
    public void setBrush(int tileId) { brush = tileId; invalidate(); }
    public int brush() { return brush; }
    public void setShowGrid(boolean g) { showGrid = g; invalidate(); }
    public boolean showGrid() { return showGrid; }
    public float scale() { return scale; }
    public void setSelectedEvent(int id) { selectedEventId = id; invalidate(); }
    public int selectedEvent() { return selectedEventId; }
    public Tileset tileset() { return tileset; }

    public void fitToMap() {
        MvMap m = (project != null) ? project.map() : null;
        if (m == null || getWidth() == 0) return;
        float sx = getWidth() / (float) (m.width * Tileset.TILE);
        float sy = getHeight() / (float) (m.height * Tileset.TILE);
        scale = Math.max(MIN_SCALE, Math.min(sx, sy) * 0.94f);
        offX = (getWidth() - m.width * Tileset.TILE * scale) / 2f;
        offY = (getHeight() - m.height * Tileset.TILE * scale) / 2f;
        invalidate();
        notifyScale();
    }

    public void zoomBy(float factor, float focusX, float focusY) {
        float ns = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
        float wx = (focusX - offX) / (Tileset.TILE * scale);
        float wy = (focusY - offY) / (Tileset.TILE * scale);
        scale = ns;
        offX = focusX - wx * Tileset.TILE * scale;
        offY = focusY - wy * Tileset.TILE * scale;
        clampPan();
        invalidate();
        notifyScale();
    }

    /** PC [Scale] > Actual Size */
    public void actualSize() {
        scale = 1f;
        MvMap m = (project != null) ? project.map() : null;
        if (m != null) {
            offX = (getWidth() - m.width * Tileset.TILE) / 2f;
            offY = (getHeight() - m.height * Tileset.TILE) / 2f;
        }
        clampPan();
        invalidate();
        notifyScale();
    }

    private void clampPan() {
        MvMap m = (project != null) ? project.map() : null;
        if (m == null) return;
        float mw = m.width * Tileset.TILE * scale;
        float mh = m.height * Tileset.TILE * scale;
        float mx = Math.min(getWidth() * 0.4f, 160f);
        float my = Math.min(getHeight() * 0.4f, 160f);
        offX = clamp(offX, getWidth() - mw - mx, mx);
        offY = clamp(offY, getHeight() - mh - my, my);
    }

    private void notifyScale() { if (cb != null) cb.onScaleChanged(scale); }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF0A0C12);
        MvMap m = (project != null) ? project.map() : null;
        if (m == null) return;

        float ts = Tileset.TILE * scale;
        int x0 = Math.max(0, (int) Math.floor(-offX / ts));
        int y0 = Math.max(0, (int) Math.floor(-offY / ts));
        int x1 = Math.min(m.width - 1, (int) Math.ceil((getWidth() - offX) / ts));
        int y1 = Math.min(m.height - 1, (int) Math.ceil((getHeight() - offY) / ts));
        if (x1 < x0 || y1 < y0) return;

        int maxZ = (mode == MODE_EVENT) ? 3 : Math.max(layer, 0);
        boolean hasTileset = (tileset != null);

        for (int z = 0; z <= maxZ; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int t = m.getTile(x, y, z);
                    if (t == 0) continue;
                    float dx = offX + x * ts, dy = offY + y * ts;
                    if (hasTileset) {
                        tileset.drawTile(canvas, t, dx, dy, ts, ts);
                    } else {
                        tilePaint.setColor(z == 0 ? 0xFF2B3242 : 0xFF3B465E);
                        canvas.drawRect(dx, dy, dx + ts, dy + ts, tilePaint);
                    }
                }
            }
        }

        if (showGrid) {
            gridPaint.setStrokeWidth(Math.max(1f, scale * 0.5f));
            int lines = 0;
            for (int gx = x0; gx <= x1 + 1; gx++) {
                float px = offX + gx * ts;
                canvas.drawLine(px, offY + y0 * ts, px, offY + (y1 + 1) * ts, gridPaint);
                lines++;
            }
            for (int gy = y0; gy <= y1 + 1; gy++) {
                float py = offY + gy * ts;
                canvas.drawLine(offX + x0 * ts, py, offX + (x1 + 1) * ts, py, gridPaint);
                lines++;
            }
        }

        // selection rectangle
        if (selX0 >= 0) {
            int lx = Math.min(selX0, selX1), rx = Math.max(selX0, selX1);
            int ty = Math.min(selY0, selY1), by = Math.max(selY0, selY1);
            dst.set(offX + lx * ts, offY + ty * ts, offX + (rx + 1) * ts, offY + (by + 1) * ts);
            canvas.drawRect(dst, selFill);
        }

        // events
        eventText.setTextSize(Math.max(8f, ts * 0.42f));
        eventStroke.setStrokeWidth(Math.max(1.5f, ts * 0.05f));
        for (int id = 1; id < m.events.length(); id++) {
            if (m.events.isNull(id)) continue;
            JSONObject ev = m.events.optJSONObject(id);
            if (ev == null) continue;
            float ex = offX + (ev.optInt("x", 0) + 0.5f) * ts;
            float ey = offY + (ev.optInt("y", 0) + 0.5f) * ts;
            float r = Math.max(5f, ts * 0.34f);
            boolean sel = (id == selectedEventId);
            eventFill.setColor(sel ? 0xEB4A8CFF : 0xD9E2564D);
            canvas.drawCircle(ex, ey, r, eventFill);
            eventStroke.setColor(sel ? Color.WHITE : 0x99000000);
            canvas.drawCircle(ex, ey, r, eventStroke);
            Paint.FontMetrics fm = eventText.getFontMetrics();
            canvas.drawText(String.valueOf(id), ex, ey - (fm.ascent + fm.descent) / 2f, eventText);
        }

        // map border
        dst.set(offX, offY, offX + m.width * ts, offY + m.height * ts);
        canvas.drawRect(dst, borderPaint);
    }

    // --------------------------------------------------------------- gestures

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        // Manual pan/paint while a single finger is down, so we keep the
        // "drag draws" behaviour of the PC map view.
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX(); lastY = e.getY();
                moved = false;
                strokeStarted = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && e.getPointerCount() == 1) {
                    float dx = e.getX() - lastX, dy = e.getY() - lastY;
                    if (Math.abs(dx) > 1.5f || Math.abs(dy) > 1.5f) moved = true;
                    if (moved && mode == MODE_MAP && brush != 0) {
                        paintAt(e.getX(), e.getY());
                    } else if (moved) {
                        offX += dx; offY += dy;
                        clampPan();
                        invalidate();
                    }
                    lastX = e.getX(); lastY = e.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                strokeStarted = false;
                break;
        }
        return true;
    }

    private float lastX, lastY;
    private boolean moved;

    private void paintAt(float px, float py) {
        MvMap m = project != null ? project.map() : null;
        if (m == null) return;
        int tx = (int) Math.floor((px - offX) / (Tileset.TILE * scale));
        int ty = (int) Math.floor((py - offY) / (Tileset.TILE * scale));
        if (!m.inBounds(tx, ty)) return;
        if (m.getTile(tx, ty, layer) == brush) return;
        if (!strokeStarted) {
            project.beginEdit();      // one undo snapshot per stroke
            strokeStarted = true;
        }
        m.setTile(tx, ty, layer, brush);
        project.markDirty();
        invalidate();
        if (cb != null) cb.onTilePainted(tx, ty, layer);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector d) {
            float ns = clamp(scale * d.getScaleFactor(), MIN_SCALE, MAX_SCALE);
            float fx = d.getFocusX(), fy = d.getFocusY();
            float wx = (fx - offX) / (Tileset.TILE * scale);
            float wy = (fy - offY) / (Tileset.TILE * scale);
            scale = ns;
            offX = fx - wx * Tileset.TILE * scale;
            offY = fy - wy * Tileset.TILE * scale;
            clampPan();
            invalidate();
            notifyScale();
            return true;
        }
    }

    private class TapListener extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(MotionEvent e) { return true; }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            MvMap m = project != null ? project.map() : null;
            if (m == null) return true;
            int tx = (int) Math.floor((e.getX() - offX) / (Tileset.TILE * scale));
            int ty = (int) Math.floor((e.getY() - offY) / (Tileset.TILE * scale));
            if (!m.inBounds(tx, ty)) return true;

            if (mode == MODE_MAP) {
                if (brush == 0) {
                    if (m.getTile(tx, ty, layer) != 0) {
                        project.beginEdit();
                        m.setTile(tx, ty, layer, 0);
                        project.markDirty();
                        invalidate();
                        if (cb != null) cb.onTilePainted(tx, ty, layer);
                    }
                } else {
                    paintAt(e.getX(), e.getY());
                }
            } else if (mode == MODE_EVENT) {
                int found = -1;
                for (int id = 1; id < m.events.length(); id++) {
                    if (m.events.isNull(id)) continue;
                    JSONObject ev = m.events.optJSONObject(id);
                    if (ev != null && ev.optInt("x", -1) == tx && ev.optInt("y", -1) == ty) { found = id; break; }
                }
                selectedEventId = found;
                invalidate();
                if (cb != null) cb.onEventTapped(found, false);
            }
            emitStatus(tx, ty);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            MvMap m = project != null ? project.map() : null;
            if (m == null) return;
            int tx = (int) Math.floor((e.getX() - offX) / (Tileset.TILE * scale));
            int ty = (int) Math.floor((e.getY() - offY) / (Tileset.TILE * scale));
            if (!m.inBounds(tx, ty)) return;
            if (mode == MODE_EVENT) {
                int found = -1;
                for (int id = 1; id < m.events.length(); id++) {
                    if (m.events.isNull(id)) continue;
                    JSONObject ev = m.events.optJSONObject(id);
                    if (ev != null && ev.optInt("x", -1) == tx && ev.optInt("y", -1) == ty) { found = id; break; }
                }
                selectedEventId = found;
                invalidate();
                if (cb != null) cb.onEventTapped(found, true);
            }
            if (cb != null) cb.onEventLongPress(selectedEventId, e.getX(), e.getY());
            emitStatus(tx, ty);
        }

        @Override
        public boolean onScroll(MotionEvent d1, MotionEvent d2, float dx, float dy) {
            // handled in onTouchEvent for single-finger precision; two-finger
            // panning comes free from ScaleGestureDetector's focus tracking.
            return true;
        }

        @Override
        public boolean onFling(MotionEvent d1, MotionEvent d2, float vx, float vy) {
            clampPan();
            invalidate();
            return true;
        }
    }

    private void emitStatus(int tx, int ty) {
        if (cb == null) return;
        MvMap m = project != null ? project.map() : null;
        if (m == null) return;
        int t = m.getTile(tx, ty, layer);
        cb.onStatus(String.format(java.util.Locale.US,
                "x %d   y %d   z %d   tile %d%s", tx, ty, layer, t,
                mode == MODE_EVENT ? ("   event " + selectedEventId) : ""));
    }

    public String statusText() {
        Project p = project;
        if (p == null || p.map() == null) return "";
        String mapName = (p.mapInfos() != null) ? p.mapInfos().name(p.mapId()) : ("MAP" + p.mapId());
        MvMap m = p.map();
        return mapName + "   " + m.width + " x " + m.height + "   " + (int) (scale * 100) + "%";
    }

    // ------------------------------------------------------------ selection

    public void setSelection(int x0, int y0, int x1, int y1) {
        selX0 = x0; selY0 = y0; selX1 = x1; selY1 = y1;
        invalidate();
    }

    public void clearSelection() {
        selX0 = -1; invalidate();
    }

    public int[] selection() {
        return (selX0 >= 0) ? new int[]{Math.min(selX0, selX1), Math.min(selY0, selY1),
                Math.max(selX0, selX1), Math.max(selY0, selY1)} : null;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
