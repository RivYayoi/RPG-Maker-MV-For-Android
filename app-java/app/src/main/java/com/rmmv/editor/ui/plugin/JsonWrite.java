package com.rmmv.editor.ui.plugin;

/**
 * {@code JsonWrite} -- pretty-printer that keeps RPG Maker's generated JS files
 * readable (4-space indent, short scalar arrays kept on one line).
 *
 * <p>Framework-only; no dependency on the Android toolchain.
 */
public final class JsonWrite {

    private static final String INDENT = "    ";
    /** Scalar arrays longer than this stay on one line. */
    private static final int INLINE_LIMIT = 96;

    private JsonWrite() { }

    public static String pretty(org.json.JSONArray a) {
        StringBuilder sb = new StringBuilder();
        array(a, 0, sb);
        return sb.toString();
    }

    // -------------------------------------------------------------- internals

    private static void array(org.json.JSONArray a, int depth, StringBuilder sb) {
        if (a == null || a.length() == 0) { sb.append("[]"); return; }
        if (isScalarArray(a) && a.toString().length() <= INLINE_LIMIT) {
            sb.append(a.toString());
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < a.length(); i++) {
            pad(depth + 1, sb);
            value(a.opt(i), depth + 1, sb);
            sb.append(i < a.length() - 1 ? ",\n" : "\n");
        }
        pad(depth, sb);
        sb.append(']');
    }

    private static void object(org.json.JSONObject o, int depth, StringBuilder sb) {
        if (o == null || o.length() == 0) { sb.append("{}"); return; }
        sb.append("{\n");
        java.util.Iterator<String> it = o.keys();
        int i = 0, n = o.length();
        while (it.hasNext()) {
            String k = it.next();
            pad(depth + 1, sb);
            sb.append(org.json.JSONObject.quote(k)).append(": ");
            value(o.opt(k), depth + 1, sb);
            sb.append(++i < n ? ",\n" : "\n");
        }
        pad(depth, sb);
        sb.append('}');
    }

    private static void value(Object v, int depth, StringBuilder sb) {
        if (v == null || v == org.json.JSONObject.NULL) { sb.append("null"); return; }
        if (v instanceof org.json.JSONObject) { object((org.json.JSONObject) v, depth, sb); return; }
        if (v instanceof org.json.JSONArray) { array((org.json.JSONArray) v, depth, sb); return; }
        if (v instanceof String) { sb.append(org.json.JSONObject.quote((String) v)); return; }
        if (v instanceof Boolean || v instanceof Number) { sb.append(v.toString()); return; }
        sb.append(org.json.JSONObject.quote(String.valueOf(v)));
    }

    private static boolean isScalarArray(org.json.JSONArray a) {
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            if (v instanceof org.json.JSONObject || v instanceof org.json.JSONArray) return false;
        }
        return true;
    }

    private static void pad(int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) sb.append(INDENT);
    }
}