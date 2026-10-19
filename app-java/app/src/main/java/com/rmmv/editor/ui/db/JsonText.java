package com.rmmv.editor.ui.db;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Iterator;

/**
 * JSON value &lt;-&gt; text conversions used by {@link RecordForm}.
 *
 * <p>Framework-only (org.json + java.*) so it compiles standalone against android.jar.
 */
public final class JsonText {

    private JsonText() { }

    // ------------------------------------------------------- array <-> lines

    /**
     * Render a {@link JSONArray} as one JSON element per line.
     *
     * <p>Strings are emitted quoted via {@link JSONObject#quote(String)} so that a
     * string containing a newline survives the round trip as a single line.
     */
    public static String formatArray(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        if (a == null) return "";
        for (int i = 0; i < a.length(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(elementToLine(a.opt(i)));
        }
        return sb.toString();
    }

    /** One JSON element as a single line of text. */
    public static String elementToLine(Object v) {
        if (v == null || v == JSONObject.NULL) return "null";
        if (v instanceof String) return JSONObject.quote((String) v);
        if (v instanceof JSONObject || v instanceof JSONArray) return v.toString();
        return String.valueOf(v);
    }

    /** Parse one JSON element per line back into an array. Blank lines are dropped. */
    public static JSONArray parseArray(String text) {
        JSONArray out = new JSONArray();
        if (text == null) return out;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            out.put(parseElement(t));
        }
        return out;
    }

    /**
     * Parse a single JSON element.
     *
     * <p>Objects, arrays and quoted strings are decoded properly; bare
     * {@code true}/{@code false}/{@code null} become their JSON counterparts;
     * anything numeric becomes a Long or Double; anything else is kept as a raw
     * string so a typo never destroys the record.
     */
    public static Object parseElement(String t) {
        try {
            if (t.startsWith("{")) return new JSONObject(t);
            if (t.startsWith("[")) return new JSONArray(t);
            if (t.startsWith("\"")) return new JSONTokener(t).nextValue();
        } catch (Exception ignored) { }
        if ("true".equals(t)) return Boolean.TRUE;
        if ("false".equals(t)) return Boolean.FALSE;
        if ("null".equals(t)) return JSONObject.NULL;
        boolean integral = t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0;
        try {
            return integral ? (Object) Long.valueOf(t) : (Object) Double.valueOf(t);
        } catch (NumberFormatException ignored) { }
        return t;
    }

    // --------------------------------------------------------------- numbers

    /** Display text for a stored number, keeping decimals visible. */
    public static String numberToText(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                // 1.0 was stored as a double: keep ".0" so the user sees the type.
                return String.valueOf(d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(n.longValue());
    }

    /**
     * Coerce edited text back to the numeric type the record originally held.
     * Unparseable text falls back to 0 rather than throwing away the save.
     */
    public static Object coerceNumber(String text, Object original) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) t = "0";
        if (original instanceof Double || original instanceof Float) {
            try { return Double.valueOf(t); } catch (NumberFormatException e) { return Double.valueOf(0); }
        }
        if (original instanceof Long) {
            try { return Long.valueOf(t); } catch (NumberFormatException e) { return Long.valueOf(0L); }
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            try { return Integer.valueOf((int) Double.parseDouble(t)); }
            catch (NumberFormatException e2) { return Integer.valueOf(0); }
        }
    }

    // ---------------------------------------------------------------- labels

    /**
     * List label for a record.
     *
     * <p>Objects become {@code "id  name"}, or {@code "key: value"} of the first
     * scalar field when there is no {@code name}. Arrays (Types.json entries) are
     * shown as their joined elements.
     */
    public static String recordLabel(Object rec) {
        if (rec == null || rec == JSONObject.NULL) return "(empty)";
        if (rec instanceof JSONArray) {
            JSONArray a = (JSONArray) rec;
            if (a.length() == 0) return "(empty)";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(elementToLine(a.opt(i)));
            }
            return sb.toString();
        }
        if (!(rec instanceof JSONObject)) return String.valueOf(rec);

        JSONObject o = (JSONObject) rec;
        String name = optString(o, "name");
        String id = scalarToText(o.opt("id"));
        if (name != null && !name.isEmpty()) {
            return (id == null) ? name : id + "   " + name;
        }
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v == null || v == JSONObject.NULL || v instanceof JSONObject || v instanceof JSONArray) continue;
            return k + ": " + elementToLine(v);
        }
        return "(unnamed)";
    }

    /** Text for a scalar JSON value, or null when the value is missing or complex. */
    public static String scalarToText(Object v) {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof Number) return numberToText((Number) v);
        if (v instanceof Boolean) return String.valueOf(v);
        if (v instanceof String) {
            String s = (String) v;
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    /** {@code optString} that yields null instead of "" for absent/empty values. */
    public static String optString(JSONObject o, String key) {
        if (!o.has(key)) return null;
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) return null;
        if (!(v instanceof String)) return String.valueOf(v);
        return (String) v;
    }

    /** JSONObject.put that never throws; JSONException is checked in org.json. */
    public static void put(JSONObject o, String key, Object value) {
        try { o.put(key, value); } catch (Exception ignored) { }
    }

    /** JSONArray.put(Object) that never throws. */
    public static void put(JSONArray a, Object value) {
        try { a.put(value); } catch (Exception ignored) { }
    }

    /** JSONArray.put(int, Object) that never throws. */
    public static void put(JSONArray a, int index, Object value) {
        try { a.put(index, value); } catch (Exception ignored) { }
    }
}