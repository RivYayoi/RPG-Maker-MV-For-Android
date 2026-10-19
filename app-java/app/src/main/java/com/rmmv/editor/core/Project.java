package com.rmmv.editor.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An open RPG Maker MV project: map list, current map, tileset cache, undo.
 *
 * <p>Mirrors the desktop editor's open-project state machine
 * (File &gt; Open/Save/Close) without any of its UI.
 */
public class Project {

    /** Desktop editor allows 20 undo steps; we keep more for touch editing. */
    private static final int UNDO_LIMIT = 60;

    private final ProjectStore store;
    private File dir;
    private String name;

    private MapInfos mapInfos;
    private MvMap map;
    private int mapId;
    private Tileset tileset;
    private int tilesetId = -1;

    private boolean dirty;

    private final List<Snapshot> undo = new ArrayList<>();
    private final List<Snapshot> redo = new ArrayList<>();

    private static class Snapshot {
        final int[] data;
        final JSONArray events;
        Snapshot(int[] d, JSONArray e) { data = d; events = e; }
    }

    public Project(ProjectStore store) {
        this.store = store;
    }

    // ------------------------------------------------------------------ open

    public void open(String projectName) throws IOException {
        File d = store.projectDir(projectName);
        File mi = new File(d, "data/MapInfos.json");
        if (!mi.exists()) throw new IOException("Project not found: " + projectName);
        this.dir = d;
        this.name = d.getName();
        this.mapInfos = MapInfos.parse(store.readText(mi));
        this.undo.clear();
        this.redo.clear();
        this.dirty = false;
        int first = 1;
        for (int i = 1; i < mapInfos.size(); i++) { if (mapInfos.get(i) != null) { first = i; break; } }
        openMap(first);
    }

    public void close() {
        if (tileset != null) { tileset.recycle(); tileset = null; }
        tilesetId = -1;
        dir = null; name = null; map = null; mapInfos = null; mapId = -1;
        dirty = false;
        undo.clear(); redo.clear();
    }

    // ------------------------------------------------------------------- map

    public void openMap(int id) throws IOException {
        File f = new File(dir, "data/" + MapInfos.fileName(id));
        if (f.exists()) {
            map = MvMap.parse(store.readText(f));
        } else {
            map = MvMap.createDefault(17, 13, 1);
        }
        mapId = id;
        dirty = false;
    }

    public int addMap(String mapName) throws IOException {
        int id = mapInfos.add(mapName, 0);
        MvMap m = MvMap.createDefault(17, 13, 1);
        store.writeText(new File(dir, "data/" + MapInfos.fileName(id)), m.toJson());
        saveMapInfos();
        openMap(id);
        dirty = true;
        return id;
    }

    public void renameMap(int id, String newName) {
        mapInfos.rename(id, newName);
        dirty = true;
    }

    public boolean removeMap(int id) {
        if (id <= 0 || mapInfos.size() < 2) return false;
        mapInfos.remove(id);
        File f = new File(dir, "data/" + MapInfos.fileName(id));
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        dirty = true;
        return true;
    }

    public void saveMapInfos() throws IOException {
        store.writeText(new File(dir, "data/MapInfos.json"), mapInfos.toJson());
    }

    public void save() throws IOException {
        if (dir == null || map == null) return;
        store.writeText(new File(dir, "data/" + MapInfos.fileName(mapId)), map.toJson());
        saveMapInfos();
        dirty = false;
        undo.clear();
        redo.clear();
    }

    // --------------------------------------------------------------- tileset

    /** Compose (and cache) the tileset for the current map. */
    public Tileset tileset() {
        if (map == null) return null;
        if (tileset != null && tilesetId == map.tilesetId) return tileset;
        if (tileset != null) tileset.recycle();
        JSONObject def = null;
        File tf = new File(dir, "data/Tilesets.json");
        if (tf.exists()) {
            try {
                JSONArray arr = new JSONArray(store.readText(tf));
                int idx = map.tilesetId;
                if (idx > 0 && idx < arr.length()) def = arr.optJSONObject(idx);
            } catch (Exception ignored) { }
        }
        tileset = new Tileset(map.tilesetId, def);
        tileset.compose(dir);
        tilesetId = map.tilesetId;
        return tileset;
    }

    /** Change the map's tileset. */
    public void setTileset(int newId) {
        map.tilesetId = newId;
        dirty = true;
    }

    // ------------------------------------------------------------------ undo

    /** Call once at the start of an edit stroke so a drag is one undo step. */
    public void beginEdit() {
        if (map == null) return;
        Snapshot s = new Snapshot(map.data.clone(), copyEvents());
        undo.add(s);
        if (undo.size() > UNDO_LIMIT) undo.remove(0);
        redo.clear();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public void undo() {
        if (map == null || undo.isEmpty()) return;
        redo.add(new Snapshot(map.data.clone(), copyEvents()));
        Snapshot s = undo.remove(undo.size() - 1);
        restore(s);
        dirty = true;
    }

    public void redo() {
        if (map == null || redo.isEmpty()) return;
        undo.add(new Snapshot(map.data.clone(), copyEvents()));
        Snapshot s = redo.remove(redo.size() - 1);
        restore(s);
        dirty = true;
    }

    private void restore(Snapshot s) {
        map.data = s.data.clone();
        try {
            map.events = new JSONArray(s.events.toString());
        } catch (JSONException ignored) { }
    }

    private JSONArray copyEvents() {
        try { return new JSONArray(map.events.toString()); } catch (JSONException e) { return new JSONArray(); }
    }

    // -------------------------------------------------------------- database

    /** Read a data/*.json database file. Returns null if absent. */
    public JSONObject readDatabase(String fileName) {
        try {
            File f = new File(dir, "data/" + fileName);
            if (!f.exists()) return null;
            return new JSONObject(store.readText(f));
        } catch (Exception e) { return null; }
    }

    public JSONArray readDatabaseArray(String fileName) {
        try {
            File f = new File(dir, "data/" + fileName);
            if (!f.exists()) return null;
            return new JSONArray(store.readText(f));
        } catch (Exception e) { return null; }
    }

    public void writeDatabase(String fileName, Object json) throws IOException {
        store.writeText(new File(dir, "data/" + fileName), json.toString());
        dirty = true;
    }

    // -------------------------------------------------------------- accessors

    public File dir() { return dir; }
    public String name() { return name; }
    public MapInfos mapInfos() { return mapInfos; }
    public MvMap map() { return map; }
    public int mapId() { return mapId; }
    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }
    public ProjectStore store() { return store; }
}
