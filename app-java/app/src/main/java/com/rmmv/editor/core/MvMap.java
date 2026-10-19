package com.rmmv.editor.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * RPG Maker MV map document (data/MapXXX.json).
 *
 * <p>Schema verified against the editor's own shipped
 * {@code NewData/data/Map001.json}, and the tile array indexing verified against
 * {@code NewData/js/rpg_objects.js:5929}:
 *
 * <pre>Game_Map.prototype.tileId = function(x, y, z) {
 *      var width = $dataMap.width;
 *      var height = $dataMap.height;
 *      return $dataMap.data[(z * height + y) * width + x] || 0;
 *   };</pre>
 *
 * <p>So: 4 layers (z = 0..3), row-major within each layer, total length
 * {@code width * height * 4}, 0 meaning empty.
 */
public class MvMap {

    public int width = 17;
    public int height = 13;
    public int tilesetId = 1;
    public int[] data;            // width*height*4
    public JSONArray events;      // indexed by event id, index 0 is null
    public JSONObject raw;        // the original document, for round-tripping

    public static MvMap createDefault(int w, int h, int tilesetId) {
        MvMap m = new MvMap();
        m.width = clamp(w, 5, 500);
        m.height = clamp(h, 5, 500);
        m.tilesetId = tilesetId <= 0 ? 1 : tilesetId;
        m.data = new int[m.dataLength()];
        m.events = new JSONArray();
        m.events.put(JSONObject.NULL);   // single-arg put does not throw
        m.raw = new JSONObject();
        fillDefaults(m.raw, m.width, m.height, m.tilesetId);
        return m;
    }

    /**
     * Write the full default MV map document.
     *
     * <p>Every key the desktop editor itself writes must be present, otherwise a
     * project created here is schema-drifted from one created on the PC. Verified
     * against the editor's shipped NewData/data/Map001.json.
     */
    private static void fillDefaults(JSONObject o, int w, int h, int tilesetId) {
        try {
            o.put("autoplayBgm", false);
            o.put("autoplayBgs", false);
            o.put("battleback1Name", "");
            o.put("battleback2Name", "");
            o.put("bgm", audioDefaults());
            o.put("bgs", audioDefaults());
            o.put("disableDashing", false);
            o.put("displayName", "");
            o.put("encounterList", new JSONArray());
            o.put("encounterStep", 30);
            o.put("height", h);
            o.put("note", "");
            o.put("parallaxLoopX", false);
            o.put("parallaxLoopY", false);
            o.put("parallaxName", "");
            o.put("parallaxShow", true);
            o.put("parallaxSx", 0);
            o.put("parallaxSy", 0);
            o.put("scrollType", 0);
            o.put("specifyBattleback", false);
            o.put("tilesetId", tilesetId);
            o.put("width", w);
        } catch (JSONException ignored) { }
    }

    private static JSONObject audioDefaults() {
        JSONObject a = new JSONObject();
        try {
            a.put("name", "");
            a.put("pan", 0);
            a.put("pitch", 100);
            a.put("volume", 90);
        } catch (JSONException ignored) { }
        return a;
    }

    /** Repair a document that is missing keys the desktop editor expects. */
    private static void ensureKeys(JSONObject o, int w, int h, int tilesetId) {
        try {
            if (!o.has("autoplayBgm")) o.put("autoplayBgm", false);
            if (!o.has("autoplayBgs")) o.put("autoplayBgs", false);
            if (!o.has("battleback1Name")) o.put("battleback1Name", "");
            if (!o.has("battleback2Name")) o.put("battleback2Name", "");
            if (!o.has("bgm") || !(o.opt("bgm") instanceof JSONObject)) o.put("bgm", audioDefaults());
            if (!o.has("bgs") || !(o.opt("bgs") instanceof JSONObject)) o.put("bgs", audioDefaults());
            if (!o.has("disableDashing")) o.put("disableDashing", false);
            if (!o.has("displayName")) o.put("displayName", "");
            if (!o.has("encounterList")) o.put("encounterList", new JSONArray());
            if (!o.has("encounterStep")) o.put("encounterStep", 30);
            if (!o.has("note")) o.put("note", "");
            if (!o.has("parallaxLoopX")) o.put("parallaxLoopX", false);
            if (!o.has("parallaxLoopY")) o.put("parallaxLoopY", false);
            if (!o.has("parallaxName")) o.put("parallaxName", "");
            if (!o.has("parallaxShow")) o.put("parallaxShow", true);
            if (!o.has("parallaxSx")) o.put("parallaxSx", 0);
            if (!o.has("parallaxSy")) o.put("parallaxSy", 0);
            if (!o.has("scrollType")) o.put("scrollType", 0);
            if (!o.has("specifyBattleback")) o.put("specifyBattleback", false);
        } catch (JSONException ignored) { }
    }

    public static MvMap parse(String text) {
        MvMap m = new MvMap();
        try {
            JSONObject o = new JSONObject(text);
            m.raw = o;
            m.width = clamp(o.optInt("width", 17), 1, 500);
            m.height = clamp(o.optInt("height", 13), 1, 500);
            m.tilesetId = Math.max(1, o.optInt("tilesetId", 1));

            int need = m.dataLength();
            JSONArray da = o.optJSONArray("data");
            m.data = new int[need];
            if (da != null) {
                int n = Math.min(need, da.length());
                for (int i = 0; i < n; i++) {
                    int v = da.optInt(i, 0);
                    m.data[i] = (v > 0 && v < TileId.TILE_ID_MAX) ? v : 0;
                }
            }

            JSONArray ev = o.optJSONArray("events");
            m.events = (ev != null) ? ev : new JSONArray();
            if (m.events.length() == 0) {
                m.events.put(JSONObject.NULL);
            }
            if (!m.events.isNull(0)) {
                try { m.events.put(0, JSONObject.NULL); } catch (JSONException ignored) { }
            }

            // Fill in any key a hand-edited or older file is missing, so the
            // document we write back matches the desktop editor's schema.
            ensureKeys(m.raw, m.width, m.height, m.tilesetId);
        } catch (JSONException e) {
            return createDefault(17, 13, 1);
        }
        return m;
    }

    public int dataLength() {
        return width * height * 4;
    }

    public static int index(int width, int height, int x, int y, int z) {
        return (z * height + y) * width + x;
    }

    public int index(int x, int y, int z) {
        return index(width, height, x, y, z);
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public int getTile(int x, int y, int z) {
        if (!inBounds(x, y) || z < 0 || z > 3) return 0;
        return data[index(x, y, z)];
    }

    public void setTile(int x, int y, int z, int tileId) {
        if (!inBounds(x, y) || z < 0 || z > 3) return;
        data[index(x, y, z)] = tileId;
    }

    public int eventCount() {
        int n = 0;
        for (int i = 1; i < events.length(); i++) if (!events.isNull(i)) n++;
        return n;
    }

    public int nextEventId() {
        for (int i = events.length() - 1; i >= 1; i--) {
            if (!events.isNull(i)) return i + 1;
        }
        return 1;
    }

    /**
     * Serialise back to MV JSON.
     *
     * <p>Only the keys the desktop editor writes are emitted; anything else the
     * document carried is preserved from {@link #raw} so an open/save round trip
     * does not silently drop fields we do not model (bgm, parallax, encounter...).
     */
    public String toJson() {
        JSONObject out;
        try {
            out = (raw != null) ? new JSONObject(raw.toString()) : new JSONObject();
        } catch (JSONException e) {
            out = new JSONObject();
        }
        try {
            out.put("width", width);
            out.put("height", height);
            out.put("tilesetId", tilesetId);
            JSONArray da = new JSONArray();
            for (int v : data) da.put(v);
            out.put("data", da);
            JSONArray ev = new JSONArray();
            for (int i = 0; i < events.length(); i++) {
                ev.put(events.isNull(i) ? JSONObject.NULL : events.opt(i));
            }
            out.put("events", ev);
        } catch (JSONException ignored) { }
        return out.toString();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
