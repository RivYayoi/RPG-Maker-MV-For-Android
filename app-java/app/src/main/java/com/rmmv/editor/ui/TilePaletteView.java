package com.rmmv.editor.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.rmmv.editor.R;
import com.rmmv.editor.core.TileId;
import com.rmmv.editor.core.Tileset;

import java.util.ArrayList;
import java.util.List;

/**
 * Native Tile Palette -- the PC editor's (3) Tile Palette.
 *
 * <p>Reproduces the PC layout: a <b>vertical</b> grid of tile thumbnails filling a
 * narrow left-hand panel, with the A / B / R tabs underneath (see the editor's
 * own manual screenshot Help/inc/img/01_03_img01.png, component (3)).
 *
 * <p>Only auto-tiles (A1..A4) are offered. The set-number -&gt; source-image
 * binding for the non-auto tiles (B/C/D/E/A5) lives in RPGMV.exe's Qt resources
 * and is not readable from this host, so emitting those tileIds would produce
 * projects that render correctly here but wrong on the desktop. The tileIds this
 * palette writes come from {@link TileId}'s verified algebra.
 */
public class TilePaletteView extends View {

    /** PC palette tabs: A = auto tiles, B = shapes, R = region editor. */
    public enum Tab { A, B, R }

    private Tileset tileset;
    private Tab tab = Tab.A;
    private int shape = 0;
    private int selected = 0;

    private final List<Integer> entries = new ArrayList<>();
    private final List<Bitmap> thumbs = new ArrayList<>();

    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final Rect src = new Rect();

    private float cell = 44f;
    private float gap = 3f;
    private float pad = 4f;
    private int columns = 4;
    private float scrollY = 0f;
    private float maxScroll = 0f;

    private GestureDetector gd;

    public interface OnTileSelected {
        void onTileSelected(int tileId);
    }

    private OnTileSelected listener;

    public TilePaletteView(Context c) { super(c); init(); }
    public TilePaletteView(Context c, AttributeSet a) { super(c, a); init(); }
    public TilePaletteView(Context c, AttributeSet a, int b) { super(c, a, b); init(); }

    private void init() {
        bg.setColor(0xFF0A0C12);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f);
        eraserPaint.setColor(0xFFE2564D);
        eraserPaint.setTextAlign(Paint.Align.CENTER);
        selPaint.setColor(0x334A8CFF);
        gd = new GestureDetector(getContext(), new G());
        gd.setIsLongpressEnabled(false);
    }

    public void setOnTileSelected(OnTileSelected l) { listener = l; }

    public void setTileset(Tileset t) {
        this.tileset = t;
        rebuild();
    }

    public void setTab(Tab t) {
        this.tab = t;
        rebuild();
    }

    public Tab tab() { return tab; }

    public void setShape(int s) {
        this.shape = Math.max(0, Math.min(TileId.SHAPES - 1, s));
        rebuild();
    }

    public int shape() { return shape; }

    public int selectedTile() { return selected; }

    public void setSelectedTile(int tileId) {
        selected = tileId;
        invalidate();
    }

    private void rebuild() {
        for (Bitmap b : thumbs) if (b != null && !b.isRecycled()) b.recycle();
        thumbs.clear();
        entries.clear();

        if (tab == Tab.R) {
            // Region editor: no tile thumbnails, the region map is drawn on the
            // canvas itself (PC does the same -- the R tab paints region ids).
            invalidate();
            return;
        }
        if (tab == Tab.B) {
            // B: all 48 shapes of one autotile kind.
            for (int s = 0; s < TileId.SHAPES; s++) {
                int tileId = TileId.makeAutotileId(16, s);
                entries.add(tileId);
                thumbs.add(tileset != null ? tileset.renderTile(tileId) : null);
            }
        } else {
            // A: every auto-tile kind at the current shape.
            for (int k = 0; k < TileId.KINDS; k++) {
                int tileId = TileId.makeAutotileId(k, shape);
                entries.add(tileId);
                thumbs.add(tileset != null ? tileset.renderTile(tileId) : null);
            }
        }
        invalidate();
    }

    public int entryCount() { return entries.size(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // Thumbnail size follows the density so a tile reads at roughly the
        // 48px art size the PC editor shows, and is orientation-aware
        // (values/values-land/values-sw600dp) so the left column always fits
        // a sensible number of columns.
        float density = getResources().getDisplayMetrics().density;
        cell = getResources().getDimensionPixelSize(R.dimen.pc_palette_cell);
        gap = 3f * density;
        pad = 4f * density;
        recomputeColumns();
    }

    private void recomputeColumns() {
        float usable = getWidth() - pad * 2;
        columns = Math.max(1, (int) Math.floor((usable + gap) / (cell + gap)));
    }

    private int rows() {
        return (int) Math.ceil(entries.size() / (double) Math.max(1, columns));
    }

    private float contentHeight() {
        return pad * 2 + rows() * (cell + gap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bg);
        if (tab == Tab.R) {
            eraserPaint.setTextSize(13f);
            eraserPaint.setColor(0xFF93A0B8);
            canvas.drawText("Region Editor", getWidth() / 2f, getHeight() / 2f - 8, eraserPaint);
            eraserPaint.setTextSize(11f);
            canvas.drawText("(R)", getWidth() / 2f, getHeight() / 2f + 10, eraserPaint);
            return;
        }

        recomputeColumns();
        maxScroll = Math.max(0, contentHeight() - getHeight());
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;

        for (int i = 0; i < entries.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            float x = pad + col * (cell + gap);
            float y = pad + row * (cell + gap) - scrollY;
            if (y + cell < 0 || y > getHeight()) continue;

            dst.set(x, y, x + cell, y + cell);
            Bitmap b = thumbs.get(i);
            if (b != null && !b.isRecycled()) {
                src.set(0, 0, b.getWidth(), b.getHeight());
                canvas.drawBitmap(b, src, dst, bmpPaint);
            }
            int tileId = entries.get(i);
            boolean sel = (tileId == selected);
            border.setColor(sel ? 0xFF4A8CFF : 0xFF313A4D);
            border.setStrokeWidth(sel ? 3f : 1.5f);
            canvas.drawRect(dst, border);
            if (sel) {
                selPaint.setColor(0x554A8CFF);
                dst.inset(-3f, -3f);
                canvas.drawRect(dst, selPaint);
                dst.inset(3f, 3f);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);
        return true;
    }

    private void scrollBy(float dy) {
        scrollY = Math.max(0, Math.min(maxScroll, scrollY + dy));
        invalidate();
    }

    private void pick(float x, float y) {
        if (tab == Tab.R) return;
        int col = (int) Math.floor((x - pad) / (cell + gap));
        int row = (int) Math.floor((y - pad + scrollY) / (cell + gap));
        if (col < 0 || col >= columns || row < 0) return;
        int i = row * columns + col;
        if (i < 0 || i >= entries.size()) return;
        selected = entries.get(i);
        invalidate();
        if (listener != null) listener.onTileSelected(selected);
    }

    /** Scroll so the given tile is visible (used when a shape changes). */
    public void ensureVisible(int tileId) {
        int i = entries.indexOf(tileId);
        if (i < 0) return;
        int row = i / Math.max(1, columns);
        float top = pad + row * (cell + gap);
        if (top < scrollY) scrollY = top;
        else if (top + cell > scrollY + getHeight()) scrollY = top + cell - getHeight();
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));
        invalidate();
    }

    private class G extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(MotionEvent e) { return true; }

        @Override
        public boolean onScroll(MotionEvent d1, MotionEvent d2, float dx, float dy) {
            scrollBy(-dy);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            pick(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onFling(MotionEvent d1, MotionEvent d2, float vx, float vy) {
            scrollBy(-vy * 0.12f);
            return true;
        }
    }
}
