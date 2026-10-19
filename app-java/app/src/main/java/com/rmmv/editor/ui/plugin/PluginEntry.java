package com.rmmv.editor.ui.plugin;

import org.json.JSONObject;

/**
 * One row of the Plugin Manager: either a plugin registered in
 * {@code js/plugins.js}, or a {@code .js} file that exists under
 * {@code js/plugins/} but was never registered (and is therefore never loaded
 * by the game).
 *
 * <p>Registered entries wrap the original {@link JSONObject}; setters mutate it
 * in place so keys this screen does not expose ({@code parameters},
 * {@code screenWidth}, plugin-specific extras, ...) survive a save untouched.
 */
public final class PluginEntry {

    /** Registered plugin entry; null for an unregistered file. */
    private final JSONObject raw;
    /** Bare file name such as {@code SR_AircraftAttachment.js}; "" when unknown. */
    private final String fileName;

    private PluginEntry(JSONObject raw, String fileName) {
        this.raw = raw;
        this.fileName = fileName;
    }

    public static PluginEntry registered(JSONObject raw) {
        return new PluginEntry(raw, fileNameOf(raw));
    }

    public static PluginEntry unregistered(String fileName) {
        return new PluginEntry(null, fileName);
    }

    /** Build the entry object the editor writes for a freshly registered plugin. */
    public static JSONObject newRecord(String fileName) {
        String stem = stripJs(fileName);
        JSONObject o = new JSONObject();
        Json.put(o, "status", Boolean.TRUE);
        Json.put(o, "parameters", new JSONObject());
        Json.put(o, "name", stem);
        Json.put(o, "pluginName", "js/plugins/" + fileName);
        Json.put(o, "description", "");
        Json.put(o, "author", "");
        Json.put(o, "version", "1.0.0");
        return o;
    }

    // -------------------------------------------------------------- accessors

    public boolean isRegistered() { return raw != null; }

    public String fileName() { return fileName; }

    public String name() {
        String n = text("name");
        return (n != null && !n.isEmpty()) ? n : stripJs(fileName);
    }

    public String author() { return orEmpty(text("author")); }
    public String version() { return orEmpty(text("version")); }
    public String description() { return orEmpty(text("description")); }

    public boolean status() {
        return raw == null || raw.optBoolean("status", true);
    }

    /** Path the game would load, e.g. {@code js/plugins/Foo.js}. */
    public String pluginName() {
        if (raw == null) return "js/plugins/" + fileName;
        String p = text("pluginName");
        return (p != null && !p.isEmpty()) ? p : "js/plugins/" + stripJs(fileName) + ".js";
    }

    public int parameterCount() {
        if (raw == null) return 0;
        JSONObject p = raw.optJSONObject("parameters");
        return p == null ? 0 : p.length();
    }

    // ---------------------------------------------------------------- setters

    public void setStatus(boolean on) { Json.put(raw, "status", Boolean.valueOf(on)); }
    public void setName(String v) { Json.put(raw, "name", v == null ? "" : v); }
    public void setAuthor(String v) { Json.put(raw, "author", v == null ? "" : v); }
    public void setVersion(String v) { Json.put(raw, "version", v == null ? "" : v); }
    public void setDescription(String v) { Json.put(raw, "description", v == null ? "" : v); }

    // ------------------------------------------------------------------ utils

    private String text(String key) {
        if (raw == null) return null;
        Object v = raw.opt(key);
        if (v == null || v == JSONObject.NULL) return null;
        return String.valueOf(v);
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    static String stripJs(String name) {
        if (name == null) return "";
        String n = name;
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        if (n.toLowerCase(java.util.Locale.US).endsWith(".js")) n = n.substring(0, n.length() - 3);
        return n;
    }

    /** File name of a registered entry, derived from {@code pluginName} or {@code name}. */
    static String fileNameOf(JSONObject raw) {
        if (raw == null) return "";
        Object p = raw.opt("pluginName");
        if (p instanceof String && !((String) p).isEmpty()) {
            String s = (String) p;
            int slash = s.lastIndexOf('/');
            return slash >= 0 ? s.substring(slash + 1) : s;
        }
        Object n = raw.opt("name");
        if (n instanceof String && !((String) n).isEmpty()) {
            String s = (String) n;
            int slash = s.lastIndexOf('/');
            if (slash >= 0) s = s.substring(slash + 1);
            return s.endsWith(".js") ? s : s + ".js";
        }
        return "";
    }

    /** Tiny null-safe JSONObject.put (org.json declares JSONException). */
    static final class Json {
        static void put(JSONObject o, String key, Object value) {
            if (o == null) return;
            try { o.put(key, value); } catch (Exception ignored) { }
        }
    }
}