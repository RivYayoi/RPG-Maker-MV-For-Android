package com.rmmv.editor.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.rmmv.editor.MainActivity;
import com.rmmv.editor.R;
import com.rmmv.editor.core.MvMap;
import com.rmmv.editor.core.Project;
import com.rmmv.editor.core.Session;
import com.rmmv.editor.core.TileId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The PC editor's <b>Main Window</b>, ported to native Android Views.
 *
 * <p>Component mapping, from the editor's own manual
 * (Help/page/01_03.html + the screenshot Help/inc/img/01_03_img01.png):
 * <ol>
 *   <li>(1) Menu Bar  -> horizontal row of menu titles that open dropdowns,
 *       exactly like the desktop File/Edit/Mode/Draw/Scale/Tools/Game/Help bar</li>
 *   <li>(2) Toolbar  -> horizontal row of icon buttons</li>
 *   <li>(3) Tile Palette -> {@link TilePaletteView}, a vertical grid in the
 *       left column, with the A / B / R tabs under it</li>
 *   <li>(4) Map List -> {@link MapListPanel}, a tree in the same left column</li>
 *   <li>(5) Map View -> {@link MapCanvasView}, filling the right side</li>
 *   <li>(6) Status Bar -> the bottom readout, split into panels like the PC one</li>
 * </ol>
 *
 * <p>Layout adapts by orientation: the left column width comes from
 * {@code @dimen/pc_left_panel_width}, which is narrow in portrait and the full
 * desktop width in landscape ({@code values-land}) and on tablets
 * ({@code values-sw600dp}).
 *
 * <p>System bars are handled with edge-to-edge + {@link WindowInsetsCompat}, so
 * the menu bar and the status bar are never hidden behind the status bar /
 * navigation bar.
 */
public class MapEditorActivity extends AppCompatActivity {

    private Project project;
    private MapCanvasView canvas;
    private TilePaletteView palette;
    private MapListPanel mapList;
    private TextView status, zoom, statusMode, statusTileset;
    private Button[] tabButtons = new Button[3];
    private int lastEventId = -1;
    /** Preserved across the rotation recreation. */
    private int restoreMapId = -1;

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_editor);

        final String name = getIntent().getStringExtra("project");
        try {
            project = Session.require(this, name);
        } catch (Exception e) {
            Toast.makeText(this, "Open failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // The activity is recreated on rotation (configChanges deliberately
        // omits orientation|screenSize so that values-land dimens and the
        // landscape layout are re-resolved). Restore the map we were on.
        if (savedInstanceState != null) {
            restoreMapId = savedInstanceState.getInt("mapId", -1);
        }
        if (restoreMapId > 0 && restoreMapId != project.mapId()) {
            try { project.openMap(restoreMapId); } catch (Exception ignored) { }
        }

        // ---- edge to edge + system bar insets (status bar adaptation) ------
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        // The window extends behind the system bars, so the area we inset away
        // (e.g. the navigation bar strip on the right in landscape) must show
        // the app's own background instead of a black frame.
        getWindow().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.bg)));
        applyInsets();

        canvas = findViewById(R.id.canvas);
        palette = findViewById(R.id.palette);
        mapList = findViewById(R.id.mapList);
        status = findViewById(R.id.status);
        zoom = findViewById(R.id.zoom);
        statusMode = findViewById(R.id.statusMode);
        statusTileset = findViewById(R.id.statusTileset);

        buildMenuBar();
        buildToolBar();

        canvas.setProject(project);
        canvas.setCallbacks(new MapCanvasView.Callbacks() {
            @Override public void onTilePainted(int x, int y, int z) {
                updateStatus(x, y);
                markDirtyTitle();
            }
            @Override public void onStatus(String s) { status.setText(s); }
            @Override public void onEventTapped(int eventId, boolean created) {
                lastEventId = eventId;
                if (eventId > 0) showEventDialog(eventId, created);
            }
            @Override public void onEventLongPress(int eventId, float x, float y) {
                showEventDialog(eventId, eventId > 0);
            }
            @Override public void onScaleChanged(float s) {
                zoom.setText(Math.round(s * 100) + "%");
                updateStatusLabel();
            }
        });

        palette.setOnTileSelected(tileId -> {
            canvas.setBrush(tileId);
            if (tileId != 0 && canvas.mode() != MapCanvasView.MODE_EVENT) {
                setMode(MapCanvasView.MODE_MAP);
            }
        });

        mapList.setCallbacks(new MapListPanel.Callbacks() {
            @Override public void onMapSelected(int mapId) {
                switchToMap(mapId);
            }
            @Override public void onMapLongPressed(int mapId, float x, float y) {
                mapContextDialog(mapId);
            }
        });

        tabButtons[0] = findViewById(R.id.tabA);
        tabButtons[1] = findViewById(R.id.tabB);
        tabButtons[2] = findViewById(R.id.tabR);
        tabButtons[0].setOnClickListener(v -> {
            palette.setTab(TilePaletteView.Tab.A);
            setMode(MapCanvasView.MODE_MAP);
            syncTabs();
        });
        tabButtons[1].setOnClickListener(v -> {
            palette.setTab(TilePaletteView.Tab.B);
            syncTabs();
        });
        tabButtons[2].setOnClickListener(v -> {
            palette.setTab(TilePaletteView.Tab.R);
            setMode(MapCanvasView.MODE_REGION);
            syncTabs();
        });

        palette.setTileset(project.tileset());
        // Preselect the default ground tile (autotile kind 16) so a fresh map is
        // immediately drawable.
        int defaultTile = TileId.makeAutotileId(16, 0);
        palette.setSelectedTile(defaultTile);
        canvas.setBrush(defaultTile);
        canvas.setSelectedEvent(-1);
        mapList.setProject(project);
        mapList.setSelectedMap(project.mapId());
        syncTabs();

        canvas.fitToMap();
        updateStatusLabel();
        updateTilesetStatus();
    }

    /**
     * Status-bar / navigation-bar adaptation: pad the root so the PC menu bar
     * clears the system status bar and the PC status bar clears the navigation
     * bar, instead of being drawn underneath them.
     */
    private void applyInsets() {
        final View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    // ------------------------------------------------------- (1) MENU BAR

    /** PC (1) Menu Bar: a horizontal row whose entries open dropdown menus. */
    private void buildMenuBar() {
        LinearLayout bar = findViewById(R.id.menuBar);
        bar.removeAllViews();
        menu(bar, R.string.menu_file, R.drawable.ic_save, new Item[]{
                new Item(R.string.file_save_project, this::doSave),
                new Item(R.string.file_close_project, this::finish),
                new Item("Map Properties", this::openMapProperties),
                new Item(R.string.file_deployment, () -> unsupported("Deployment")),
                new Item(R.string.file_exit, this::finish),
        });
        menu(bar, R.string.menu_edit, R.drawable.ic_undo, new Item[]{
                new Item(R.string.edit_undo, () -> { project.undo(); canvas.invalidate(); toast("Undo"); }),
                new Item(R.string.edit_redo, () -> { project.redo(); canvas.invalidate(); toast("Redo"); }),
                new Item(R.string.edit_copy, this::copySelection),
                new Item(R.string.edit_paste, this::pasteClipboard),
                new Item(R.string.edit_delete, this::deleteSelection),
        });
        menu(bar, R.string.menu_mode, R.drawable.ic_mapmode, new Item[]{
                new Item(R.string.mode_map, () -> setMode(MapCanvasView.MODE_MAP)),
                new Item(R.string.mode_event, () -> setMode(MapCanvasView.MODE_EVENT)),
                new Item(R.string.mode_region, () -> setMode(MapCanvasView.MODE_REGION)),
        });
        menu(bar, R.string.menu_draw, R.drawable.ic_redo, new Item[]{
                new Item("Autotile (A)", () -> { palette.setTab(TilePaletteView.Tab.A); syncTabs(); }),
                new Item("Shape (B)", () -> { palette.setTab(TilePaletteView.Tab.B); syncTabs(); }),
                new Item("Region (R)", () -> { palette.setTab(TilePaletteView.Tab.R); syncTabs(); }),
                new Item(R.string.eraser, () -> { canvas.setBrush(0); palette.setSelectedTile(0); }),
        });
        menu(bar, R.string.menu_scale, R.drawable.ic_zoom_in, new Item[]{
                new Item(R.string.scale_zoom_in, () -> canvas.zoomBy(1.25f, canvas.getWidth() / 2f, canvas.getHeight() / 2f)),
                new Item(R.string.scale_zoom_out, () -> canvas.zoomBy(0.8f, canvas.getWidth() / 2f, canvas.getHeight() / 2f)),
                new Item(R.string.scale_actual, () -> canvas.actualSize()),
                new Item(R.string.scale_fit, () -> canvas.fitToMap()),
        });
        menu(bar, R.string.menu_tool, R.drawable.ic_database, new Item[]{
                new Item(R.string.tool_database, () -> openSub(DatabaseActivity.class)),
                new Item(R.string.tool_plugin, () -> openSub(PluginManagerActivity.class)),
                new Item(R.string.tool_resource, () -> openSub(ResourceManagerActivity.class)),
                new Item(R.string.tool_sound, () -> unsupported("Sound Test")),
                new Item(R.string.tool_event_search, () -> unsupported("Event Searcher")),
                new Item(R.string.tool_chargen, () -> unsupported("Character Generator")),
        });
        menu(bar, R.string.menu_game, R.drawable.ic_props, new Item[]{
                new Item(R.string.game_playtest, () -> unsupported("Playtest")),
                new Item(R.string.game_open_folder, () -> openSub(ResourceManagerActivity.class)),
        });
        menu(bar, R.string.menu_help, R.drawable.ic_resource, new Item[]{
                new Item(R.string.help_about, this::showAbout),
                new Item(R.string.help_contents, () -> unsupported("Help contents")),
        });
    }

    private interface Action { void run(); }

    private static class Item {
        final int resId;
        final String literal;
        final Action a;
        Item(int resId, Action a) { this.resId = resId; this.literal = null; this.a = a; }
        Item(String literal, Action a) { this.resId = 0; this.literal = literal; this.a = a; }
        String text() { return literal != null ? literal : ""; }
    }

    private void menu(LinearLayout bar, int titleRes, int iconRes, Item[] items) {
        final Button b = new Button(this);
        b.setText(titleRes);
        // The style carries android:textSize in sp, which the framework scales
        // correctly. setAllCaps(false) afterwards overrides the theme's
        // ALL-CAPS so the titles read "File Edit Mode" like the PC menu.
        b.setTextAppearance(this, R.style.PcMenuBtn);
        b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.bg_button);
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, b);
            for (int i = 0; i < items.length; i++) {
                pm.getMenu().add(0, i, i, items[i].resId != 0
                        ? getString(items[i].resId) : items[i].text());
            }
            pm.setOnMenuItemClickListener(item -> {
                items[item.getItemId()].a.run();
                return true;
            });
            pm.show();
        });
        bar.addView(b);
    }

    // ------------------------------------------------------- (2) TOOLBAR

    /** PC (2) Toolbar: a horizontal row of icon buttons. */
    private void buildToolBar() {
        LinearLayout bar = findViewById(R.id.toolBar);
        bar.removeAllViews();
        tool(bar, R.drawable.ic_new, "New Project", this::newProjectDialog);
        tool(bar, R.drawable.ic_open, "Open Project", () -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        });
        tool(bar, R.drawable.ic_save, "Save Project", this::doSave);
        sep(bar);
        tool(bar, R.drawable.ic_undo, "Undo", () -> { project.undo(); canvas.invalidate(); });
        tool(bar, R.drawable.ic_redo, "Redo", () -> { project.redo(); canvas.invalidate(); });
        sep(bar);
        tool(bar, R.drawable.ic_zoom_in, "Zoom In", () -> canvas.zoomBy(1.25f, canvas.getWidth() / 2f, canvas.getHeight() / 2f));
        tool(bar, R.drawable.ic_zoom_out, "Zoom Out", () -> canvas.zoomBy(0.8f, canvas.getWidth() / 2f, canvas.getHeight() / 2f));
        tool(bar, R.drawable.ic_actual, "Actual Size", () -> canvas.actualSize());
        tool(bar, R.drawable.ic_fit, "Fit to Map", () -> canvas.fitToMap());
        sep(bar);
        tool(bar, R.drawable.ic_mapmode, "Map Mode", () -> setMode(MapCanvasView.MODE_MAP));
        tool(bar, R.drawable.ic_eventmode, "Event Mode", () -> setMode(MapCanvasView.MODE_EVENT));
        sep(bar);
        tool(bar, R.drawable.ic_grid, "Grid", () -> {
            boolean on = !canvas.showGrid();
            canvas.setShowGrid(on);
            toast("Grid " + (on ? "on" : "off"));
        });
        tool(bar, R.drawable.ic_newmap, "New Map", this::addMapDialog);
        tool(bar, R.drawable.ic_props, "Map Properties", this::openMapProperties);
        sep(bar);
        tool(bar, R.drawable.ic_database, "Database", () -> openSub(DatabaseActivity.class));
        tool(bar, R.drawable.ic_plugin, "Plugin Manager", () -> openSub(PluginManagerActivity.class));
        tool(bar, R.drawable.ic_resource, "Resource Manager", () -> openSub(ResourceManagerActivity.class));
    }

    private void tool(LinearLayout bar, int iconRes, String desc, Action a) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(iconRes);
        b.setContentDescription(desc);
        b.setBackgroundResource(R.drawable.bg_button);
        b.setPadding(dp(5), dp(5), dp(5), dp(5));
        b.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        b.setOnClickListener(v -> a.run());
        int size = getResources().getDimensionPixelSize(R.dimen.pc_tool_icon_size);
        bar.addView(b, new LinearLayout.LayoutParams(size, size));
    }

    private void sep(LinearLayout bar) {
        View v = new View(this);
        v.setBackgroundColor(getResources().getColor(R.color.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(20));
        lp.leftMargin = dp(4); lp.rightMargin = dp(4);
        bar.addView(v, lp);
    }

    // ------------------------------------------------------------- status

    private void updateStatus(int x, int y) {
        MvMap m = project.map();
        status.setText(String.format("%d,%d  z%d  tile %d", x, y, canvas.layer(),
                m.getTile(x, y, canvas.layer())));
    }

    private void updateStatusLabel() {
        MvMap m = project.map();
        String name = (project.mapInfos() != null) ? project.mapInfos().name(project.mapId()) : "";
        String id = String.format("%03d", project.mapId());
        status.setText(id + ":" + name + " (" + m.width + "x" + m.height + ")");
        statusMode.setText(canvas.mode() == MapCanvasView.MODE_EVENT ? "Event"
                : canvas.mode() == MapCanvasView.MODE_REGION ? "Region" : "Map");
        zoom.setText(Math.round(canvas.scale() * 100) + "%");
        updateTilesetStatus();
    }

    private void updateTilesetStatus() {
        TilesetName t = currentTilesetName();
        statusTileset.setText(t.text);
    }

    private static class TilesetName { String text = ""; }

    private TilesetName currentTilesetName() {
        TilesetName r = new TilesetName();
        try {
            JSONArray arr = project.readDatabaseArray("Tilesets.json");
            int idx = project.map().tilesetId;
            if (arr != null && idx > 0 && idx < arr.length()) {
                JSONObject o = arr.optJSONObject(idx);
                if (o != null) r.text = "Tileset " + idx + " · " + o.optString("name", "");
            }
        } catch (Exception ignored) { }
        return r;
    }

    private void markDirtyTitle() {
        setTitle(project.name() + (project.isDirty() ? " *" : ""));
    }

    private void setMode(int m) {
        canvas.setMode(m);
        syncTabs();
        updateStatusLabel();
    }

    private void syncTabs() {
        TilePaletteView.Tab t = palette.tab();
        for (int i = 0; i < 3; i++) {
            boolean on = (i == t.ordinal());
            tabButtons[i].setTextColor(getResources().getColor(on ? R.color.on_accent : R.color.fg_dim));
            tabButtons[i].setBackgroundResource(on ? R.drawable.bg_button_active : R.drawable.bg_button);
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void unsupported(String what) {
        new AlertDialog.Builder(this)
                .setTitle(what)
                .setMessage(what + " is not available in this native Android port.")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_about)
                .setMessage(R.string.about_body)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void openSub(Class<?> cls) {
        startActivity(new Intent(this, cls).putExtra("project", project.name()));
    }

    // ------------------------------------------------------------ file ops

    private void doSave() {
        try {
            project.save();
            markDirtyTitle();
            toast("Saved " + project.name());
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    private void newProjectDialog() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setText("NewGame");
        LinearLayout box = new LinearLayout(this);
        int pad = dp(18);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(in);
        new AlertDialog.Builder(this)
                .setTitle(R.string.file_new_project)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty()) { toast("Enter a project name"); return; }
                    try {
                        if (project.store().exists(n)) { toast("That project already exists"); return; }
                        project.store().createProject(n, n, 20, 15);
                        Intent i = new Intent(this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                        toast("Created " + n);
                    } catch (Exception e) { toast("Failed: " + e.getMessage()); }
                })
                .show();
    }

    private void addMapDialog() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setText("MAP" + String.format("%03d", project.mapInfos().nextId()));
        LinearLayout box = new LinearLayout(this);
        int pad = dp(18);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(in);
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_map)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (d, w) -> {
                    try {
                        int id = project.addMap(in.getText().toString().trim());
                        mapList.refresh();
                        mapList.setSelectedMap(id);
                        canvas.refreshTileset();
                        palette.setTileset(project.tileset());
                        canvas.fitToMap();
                        updateStatusLabel();
                        toast("Created map " + id);
                    } catch (Exception e) { toast("Failed: " + e.getMessage()); }
                })
                .show();
    }

    private void openMapProperties() {
        try {
            startActivity(new Intent(this, MapPropertiesActivity.class)
                    .putExtra("project", project.name())
                    .putExtra("mapId", project.mapId()));
        } catch (Exception e) { toast("Map Properties failed: " + e.getMessage()); }
    }

    private void switchToMap(int mapId) {
        if (mapId == project.mapId()) return;
        try {
            if (project.isDirty()) project.save();
            project.openMap(mapId);
            canvas.refreshTileset();
            palette.setTileset(project.tileset());
            canvas.setSelectedEvent(-1);
            mapList.setSelectedMap(mapId);
            canvas.fitToMap();
            updateStatusLabel();
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void mapContextDialog(final int mapId) {
        String[] items = {"Open", "Rename", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(project.mapInfos().name(mapId))
                .setItems(items, (d, which) -> {
                    if (which == 0) switchToMap(mapId);
                    else if (which == 1) renameMapDialog(mapId);
                    else deleteMapDialog(mapId);
                })
                .show();
    }

    private void renameMapDialog(final int mapId) {
        final EditText in = new EditText(this);
        in.setText(project.mapInfos().name(mapId));
        LinearLayout box = new LinearLayout(this);
        int pad = dp(18);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(in);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    project.renameMap(mapId, in.getText().toString().trim());
                    mapList.refresh();
                    updateStatusLabel();
                })
                .show();
    }

    private void deleteMapDialog(final int mapId) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage("Delete map " + mapId + "?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (project.removeMap(mapId)) {
                        mapList.refresh();
                        try { project.openMap(1); } catch (Exception ignored) { }
                        mapList.setSelectedMap(project.mapId());
                        canvas.refreshTileset();
                        palette.setTileset(project.tileset());
                        canvas.fitToMap();
                        updateStatusLabel();
                    }
                })
                .show();
    }

    // ------------------------------------------------------- edit menu ops

    private int[] clipboard;
    private int[] clipboardData;

    private void copySelection() {
        int[] sel = canvas.selection();
        if (sel == null) { toast("No selection"); return; }
        int w = sel[2] - sel[0] + 1, h = sel[3] - sel[1] + 1;
        int[] buf = new int[w * h];
        MvMap m = project.map();
        int i = 0;
        for (int y = sel[1]; y <= sel[3]; y++)
            for (int x = sel[0]; x <= sel[2]; x++) buf[i++] = m.getTile(x, y, canvas.layer());
        clipboard = new int[]{w, h};
        clipboardData = buf;
        toast("Copied " + w + "x" + h);
    }

    private void pasteClipboard() {
        if (clipboard == null || clipboardData == null) { toast("Clipboard empty"); return; }
        MvMap m = project.map();
        int w = clipboard[0], h = clipboard[1];
        project.beginEdit();
        int i = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (m.inBounds(x, y)) m.setTile(x, y, canvas.layer(), clipboardData[i]);
                i++;
            }
        project.markDirty();
        canvas.invalidate();
        markDirtyTitle();
    }

    private void deleteSelection() {
        int[] sel = canvas.selection();
        if (sel == null) { toast("No selection"); return; }
        MvMap m = project.map();
        project.beginEdit();
        for (int y = sel[1]; y <= sel[3]; y++)
            for (int x = sel[0]; x <= sel[2]; x++) m.setTile(x, y, canvas.layer(), 0);
        project.markDirty();
        canvas.invalidate();
        markDirtyTitle();
    }

    // -------------------------------------------------------------- events

    private void showEventDialog(final int eventId, boolean created) {
        final MvMap m = project.map();
        JSONObject ev = (eventId > 0 && eventId < m.events.length() && !m.events.isNull(eventId))
                ? m.events.optJSONObject(eventId) : null;
        if (ev == null) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        box.setPadding(pad, dp(6), pad, 0);

        final EditText nameIn = new EditText(this);
        nameIn.setText(ev.optString("name", ""));
        nameIn.setHint("Event name");
        box.addView(nameIn);

        String[] posNames = {"x", "y"};
        final EditText[] posIn = new EditText[2];
        for (int i = 0; i < 2; i++) {
            EditText e = new EditText(this);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setText(String.valueOf(ev.optInt(posNames[i], 0)));
            e.setHint(posNames[i]);
            box.addView(e);
            posIn[i] = e;
        }

        final SpinnerAdapter trigger = spinner(box, new String[]{"Action Button", "Player Touch", "Event Touch", "Autorun"},
                ev.optInt("trigger", 0));
        final SpinnerAdapter direction = spinner(box, new String[]{"Down", "Left", "Right", "Up"},
                Math.max(0, (ev.optInt("direction", 2) / 2) - 1));
        final SpinnerAdapter move = spinner(box, new String[]{"Fixed", "Random", "Approach", "Route"},
                ev.optInt("moveType", 0));

        new AlertDialog.Builder(this)
                .setTitle("Event " + eventId + (created ? " (new)" : ""))
                .setView(box)
                .setNeutralButton(R.string.delete, (d, w) -> {
                    try { m.events.put(eventId, JSONObject.NULL); } catch (JSONException ignored) { }
                    project.markDirty();
                    canvas.setSelectedEvent(-1);
                    canvas.invalidate();
                    markDirtyTitle();
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, (d, w) -> {
                    try {
                        project.beginEdit();
                        ev.put("name", nameIn.getText().toString());
                        ev.put("x", parseInt(posIn[0].getText().toString(), ev.optInt("x", 0)));
                        ev.put("y", parseInt(posIn[1].getText().toString(), ev.optInt("y", 0)));
                        JSONArray pages = ev.optJSONArray("pages");
                        if (pages != null && pages.length() > 0 && !pages.isNull(0)) {
                            JSONObject pg = pages.optJSONObject(0);
                            if (pg != null) {
                                pg.put("trigger", trigger.getSelectedItemPosition());
                                pg.put("direction", (direction.getSelectedItemPosition() + 1) * 2);
                                pg.put("moveType", move.getSelectedItemPosition());
                            }
                        }
                        project.markDirty();
                        canvas.invalidate();
                        markDirtyTitle();
                        toast("Event updated");
                    } catch (JSONException e) { toast("Update failed: " + e.getMessage()); }
                })
                .show();
    }

    private static class SpinnerAdapter {
        final android.widget.Spinner s;
        SpinnerAdapter(android.widget.Spinner s) { this.s = s; }
        int getSelectedItemPosition() { return s.getSelectedItemPosition(); }
    }

    private SpinnerAdapter spinner(LinearLayout parent, String[] items, int sel) {
        android.widget.Spinner s = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        if (sel >= 0 && sel < items.length) s.setSelection(sel);
        parent.addView(s);
        return new SpinnerAdapter(s);
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (project != null) outState.putInt("mapId", project.mapId());
    }

    @Override
    public void onBackPressed() {
        if (project != null && project.isDirty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.file_close_project)
                    .setMessage("Save changes to " + project.name() + "?")
                    .setPositiveButton("Save", (d, w) -> { doSave(); finish(); })
                    .setNegativeButton("Discard", (d, w) -> finish())
                    .setNeutralButton(R.string.cancel, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
