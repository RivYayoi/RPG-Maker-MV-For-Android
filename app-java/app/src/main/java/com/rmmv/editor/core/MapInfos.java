package com.rmmv.editor.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * data/MapInfos.json -- the map list.
 *
 * <p>Shape verified against the editor's shipped file:
 * <pre>[null, {"id":1,"expanded":false,"name":"MAP001","order":1,
 *              "parentId":0,"scrollX":411.5,"scrollY":334}]</pre>
 * Array is indexed by map id; index 0 is always null.
 */
public class MapInfos {

    private final JSONArray arr;

    public MapInfos(JSONArray arr) {
        this.arr = arr;
    }

    public static MapInfos parse(String text) {
        JSONArray a;
        try {
            a = new JSONArray(text);
        } catch (JSONException e) {
            a = new JSONArray();
        }
        if (a.length() == 0) a.put(JSONObject.NULL);
        if (!a.isNull(0)) {
            try { a.put(0, JSONObject.NULL); } catch (JSONException ignored) { }
        }
        return new MapInfos(a);
    }

    public JSONArray raw() {
        return arr;
    }

    public int size() {
        return arr.length();
    }

    public int mapCount() {
        int n = 0;
        for (int i = 1; i < arr.length(); i++) if (!arr.isNull(i)) n++;
        return n;
    }

    public JSONObject get(int mapId) {
        if (mapId <= 0 || mapId >= arr.length()) return null;
        return arr.isNull(mapId) ? null : arr.optJSONObject(mapId);
    }

    public String name(int mapId) {
        JSONObject o = get(mapId);
        return o == null ? ("MAP" + String.format("%03d", mapId)) : o.optString("name", "");
    }

    public int nextId() {
        for (int i = arr.length() - 1; i >= 1; i--) if (!arr.isNull(i)) return i + 1;
        return 1;
    }

    public int add(String name, int parentId) {
        int id = nextId();
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("expanded", false);
            o.put("name", name);
            o.put("order", id);
            o.put("parentId", parentId);
            o.put("scrollX", 0);
            o.put("scrollY", 0);
        } catch (JSONException ignored) { }
        fillTo(id);
        try { arr.put(id, o); } catch (JSONException ignored) { }
        return id;
    }

    public void rename(int mapId, String name) {
        JSONObject o = get(mapId);
        if (o == null) return;
        try { o.put("name", name); } catch (JSONException ignored) { }
    }

    public void remove(int mapId) {
        if (mapId > 0 && mapId < arr.length()) {
            try { arr.put(mapId, JSONObject.NULL); } catch (JSONException ignored) { }
        }
    }

    private void fillTo(int id) {
        while (arr.length() <= id) arr.put(JSONObject.NULL);
    }

    public String toJson() {
        return arr.toString();
    }

    /** File name for a map id: Map001.json (3-digit, zero padded). */
    public static String fileName(int mapId) {
        return "Map" + String.format("%03d", mapId) + ".json";
    }

    public static String stem(int mapId) {
        return "Map" + String.format("%03d", mapId);
    }
}
