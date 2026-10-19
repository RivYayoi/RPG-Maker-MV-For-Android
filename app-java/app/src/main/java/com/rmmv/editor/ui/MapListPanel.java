package com.rmmv.editor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.rmmv.editor.core.MapInfos;
import com.rmmv.editor.core.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * PC editor's (4) Map List -- a tree panel (see the manual screenshot
 * Help/inc/img/01_03_img01.png, component (4): a tree of
 * <pre>
 *   Project1
 *     MAP001
 *       World 1
 * </pre>
 * ).
 *
 * <p>Rendered directly onto a {@link Canvas} (no RecyclerView needed for a tree
 * of this size) so it can be dropped straight into the PC-style left column.
 */
public class MapListPanel extends View {

    public interface Callbacks {
        void onMapSelected(int mapId);
        void onMapLongPressed(int mapId, float x, float y);
    }

    private Project project;
    private Callbacks cb;

    /** Flattened tree rows. */
    private final List<Integer> mapIds = new ArrayList<>();
    private final List<Integer> depths = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<Boolean> expandable = new ArrayList<>();
    private final List<Boolean> expanded = new ArrayList<>();

    private int selectedMapId = -1;
    private boolean rootExpanded = true;

    private final Paint rowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private GestureDetector gd;

    private float rowH = 26f;

    public MapListPanel(Context c) { super(c); init(); }
    public MapListPanel(Context c, AttributeSet a) { super(c, a); init(); }
    public MapListPanel(Context c, AttributeSet a, int b) { super(c, a, b); init(); }

    private void init() {
        rowPaint.setColor(0xFF1A1F2B);
        textPaint.setColor(0xFFE8ECF5);
        textPaint.setTextSize(13f);
        selPaint.setColor(0xFF4A8CFF);
        iconPaint.setColor(0xFF6FA8FF);
        gd = new GestureDetector(getContext(), new G());
        gd.setIsLongpressEnabled(true);
    }

    public void setCallbacks(Callbacks c) { cb = c; }

    public void setProject(Project p) {
        this.project = p;
        refresh();
    }

    public void setSelectedMap(int id) {
        selectedMapId = id;
        invalidate();
    }

    public void refresh() {
        mapIds.clear(); depths.clear(); labels.clear(); expandable.clear(); expanded.clear();
        if (project == null) { invalidate(); return; }

        MapInfos infos = project.mapInfos();
        // root node
        mapIds.add(-1); depths.add(0);
        labels.add(project.name());
        expandable.add(true); expanded.add(rootExpanded);

        if (rootExpanded) {
            for (int id = 1; id < infos.size(); id++) {
                if (infos.get(id) == null) continue;
                mapIds.add(id); depths.add(1);
                labels.add(infos.name(id));
                expandable.add(false); expanded.add(false);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF141821);
        float y = 0;
        for (int i = 0; i < mapIds.size(); i++) {
            int id = mapIds.get(i);
            int d = depths.get(i);
            float indent = 8 + d * 16;
            r.set(0, y, getWidth(), y + rowH);
            if (id == selectedMapId) canvas.drawRect(r, selPaint);
            else if (d == 0) canvas.drawRect(r, rowPaint);

            float tx = indent;
            if (expandable.get(i)) {
                iconPaint.setColor(0xFF93A0B8);
                canvas.drawText(expanded.get(i) ? "▾" : "▸", tx, y + rowH * 0.68f, iconPaint);
                tx += 16;
            } else {
                iconPaint.setColor(0xFF6FA8FF);
                canvas.drawText("■", tx, y + rowH * 0.66f, iconPaint);
                tx += 16;
            }
            textPaint.setColor(id == selectedMapId ? Color.WHITE : 0xFFE8ECF5);
            canvas.drawText(labels.get(i), tx, y + rowH * 0.68f, textPaint);
            y += rowH;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);
        return true;
    }

    private void activate(float x, float y) {
        int i = (int) (y / rowH);
        if (i < 0 || i >= mapIds.size()) return;
        int id = mapIds.get(i);
        if (expandable.get(i)) {
            if (id == -1) {
                rootExpanded = !rootExpanded;
                refresh();
            } else {
                expanded.set(i, !expanded.get(i));
                invalidate();
            }
            return;
        }
        selectedMapId = id;
        invalidate();
        if (cb != null) cb.onMapSelected(id);
    }

    private class G extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(MotionEvent e) { return true; }

        @Override public boolean onSingleTapConfirmed(MotionEvent e) {
            activate(e.getX(), e.getY());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            int i = (int) (e.getY() / rowH);
            if (i < 0 || i >= mapIds.size()) return;
            int id = mapIds.get(i);
            if (id <= 0) return;
            if (cb != null) cb.onMapLongPressed(id, e.getX(), e.getY());
        }
    }
}
