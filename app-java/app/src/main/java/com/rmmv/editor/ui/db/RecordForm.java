package com.rmmv.editor.ui.db;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.rmmv.editor.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Builds an edit form from the keys of a database record.
 *
 * <p>One row per key, widget chosen from the value's JSON type:
 * <ul>
 *   <li>{@code boolean} &rarr; {@link Switch}</li>
 *   <li>number &rarr; numeric {@link EditText}, coerced back to the stored type</li>
 *   <li>string &rarr; single-line {@link EditText} (multi-line for long/embedded-newline text)</li>
 *   <li>array &rarr; compact multi-line editor, one JSON element per line</li>
 *   <li>object &rarr; collapsed header {@code "key  nested (N keys)"}, read-only,
 *       tap to peek at the raw JSON</li>
 *   <li>{@code null} &rarr; text field that round-trips back to {@code null} when emptied</li>
 * </ul>
 *
 * <p>The record object passed in is <b>mutated in place</b> by {@link #apply()},
 * so keys the form does not know about survive a save untouched.
 *
 * <p>Framework-only: no androidx, so it compiles against android.jar alone.
 */
public final class RecordForm {

    public static final int K_TEXT = 0;
    public static final int K_NUMBER = 1;
    public static final int K_MULTILINE = 2;
    public static final int K_BOOL = 3;
    public static final int K_NESTED = 4;
    public static final int K_ARRAY = 5;
    public static final int K_NULL = 6;

    /** One key of the record plus the widget that edits it. */
    public static final class Field {
        /** Key in the parent object; null for whole-array (Types.json) records. */
        public final String key;
        /** Value as loaded; drives widget choice and numeric coercion. */
        public final Object original;
        public final int kind;

        EditText text;
        Switch toggle;
        TextView nestedHeader;
        EditText nestedBody;

        Field(String key, Object original, int kind) {
            this.key = key;
            this.original = original;
            this.kind = kind;
        }

        /** Current value of this field, typed like the original. */
        public Object read() {
            switch (kind) {
                case K_BOOL:
                    return toggle.isChecked();
                case K_NESTED:
                    return original;                       // read-only, never changes
                case K_ARRAY:
                    return JsonText.parseArray(text.getText().toString());
                case K_NUMBER:
                    return JsonText.coerceNumber(text.getText().toString(), original);
                case K_NULL: {
                    String s = text.getText().toString();
                    return s.isEmpty() ? JSONObject.NULL : s;
                }
                default:
                    return text.getText().toString();
            }
        }
    }

    private final List<Field> fields = new ArrayList<>();
    /** Non-null when this form edits a bare JSONArray (Types.json entries). */
    private final boolean wholeArray;
    /** The record being edited; mutated in place by {@link #apply()}. */
    private JSONObject targetObject;
    private JSONArray targetArray;

    private RecordForm(boolean wholeArray) {
        this.wholeArray = wholeArray;
    }

    // ------------------------------------------------------------------ build

    /** Build a form for a JSON object record. Keys keep the order stored in the file. */
    public static RecordForm buildObject(Context ctx, ViewGroup parent, JSONObject record, Ui.Palette pal) {
        RecordForm f = new RecordForm(false);
        f.targetObject = record;
        List<String> keys = new ArrayList<>();
        Iterator<String> it = record.keys();
        while (it.hasNext()) keys.add(it.next());
        for (String k : keys) f.addKeyRow(ctx, parent, record, k, pal);
        return f;
    }

    /** Build a form for a bare JSONArray record (one Types.json element). */
    public static RecordForm buildArray(Context ctx, ViewGroup parent, JSONArray record, Ui.Palette pal) {
        RecordForm f = new RecordForm(true);
        f.targetArray = record;
        f.addArrayRow(ctx, parent, null, record, pal);
        return f;
    }

    private void addKeyRow(Context ctx, ViewGroup parent, JSONObject record, String key, Ui.Palette pal) {
        Object v = record.opt(key);
        if (v == null || v == JSONObject.NULL) {
            addTextRow(ctx, parent, new Field(key, v, K_NULL), "", "null", false, pal);
        } else if (v instanceof JSONObject) {
            addNestedRow(ctx, parent, new Field(key, v, K_NESTED), (JSONObject) v, pal);
        } else if (v instanceof JSONArray) {
            addArrayRow(ctx, parent, new Field(key, v, K_ARRAY), (JSONArray) v, pal);
        } else if (v instanceof Boolean) {
            addBoolRow(ctx, parent, new Field(key, v, K_BOOL), (Boolean) v, pal);
        } else if (v instanceof Number) {
            addTextRow(ctx, parent, new Field(key, v, K_NUMBER),
                    JsonText.numberToText((Number) v), null, true, pal);
        } else {
            String s = String.valueOf(v);
            addTextRow(ctx, parent, new Field(key, v, K_MULTILINE), s, null,
                    s.length() > 100 || s.indexOf('\n') >= 0, pal);
        }
    }

    // ------------------------------------------------------------------- rows

    private void addTextRow(Context ctx, ViewGroup parent, Field f, String value,
                            String hint, boolean numeric, Ui.Palette pal) {
        ViewGroup row = Ui.column(ctx);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Ui.margins(row, 0, 2, 0, 8, ctx);

        row.addView(keyLabel(ctx, f.key, hint, pal));

        EditText e = new EditText(ctx);
        e.setText(value);
        e.setTextColor(pal.fg);
        e.setHintTextColor(pal.fgDim);
        e.setTextSize(14f);
        e.setBackground(Ui.box(ctx, pal.bg2, pal.line, 6));
        e.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6));
        if (hint != null) e.setHint(hint);
        if (numeric) {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else if (f.kind == K_MULTILINE) {
            e.setSingleLine(false);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setMinLines(3);
            e.setMaxLines(12);
            e.setHorizontallyScrolling(false);
            e.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        f.text = e;
        row.addView(e);
        parent.addView(row);
        fields.add(f);
    }

    private void addBoolRow(Context ctx, ViewGroup parent, Field f, boolean value, Ui.Palette pal) {
        LinearLayout row = Ui.row(ctx, Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Ui.margins(row, 0, 2, 0, 8, ctx);

        TextView label = keyLabel(ctx, f.key, null, pal);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, lp);

        Switch s = new Switch(ctx);
        s.setChecked(value);
        f.toggle = s;
        row.addView(s);
        parent.addView(row);
        fields.add(f);
    }

    private void addArrayRow(Context ctx, ViewGroup parent, Field f, JSONArray value, Ui.Palette pal) {
        if (f == null) {
            f = new Field(null, value, K_ARRAY);
        }
        ViewGroup row = Ui.column(ctx);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Ui.margins(row, 0, 2, 0, 8, ctx);

        String title = (f.key == null) ? "values" : f.key;
        row.addView(keyLabel(ctx, title,
                value.length() + " item" + (value.length() == 1 ? "" : "s"), pal));

        EditText e = new EditText(ctx);
        e.setText(JsonText.formatArray(value));
        e.setTextColor(pal.fg);
        e.setHintTextColor(pal.fgDim);
        e.setTextSize(13f);
        e.setTypeface(android.graphics.Typeface.MONOSPACE);
        e.setBackground(Ui.box(ctx, pal.bg2, pal.line, 6));
        e.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6));
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setMinLines(2);
        e.setMaxLines(10);
        e.setHorizontallyScrolling(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setHint("one JSON element per line");
        f.text = e;
        row.addView(e);
        parent.addView(row);
        fields.add(f);
    }

    private void addNestedRow(Context ctx, ViewGroup parent, Field f, JSONObject value, Ui.Palette pal) {
        ViewGroup row = Ui.column(ctx);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Ui.margins(row, 0, 2, 0, 8, ctx);

        final TextView header = Ui.text(ctx,
                f.key + "   \u00b7   nested (" + value.length() + " keys)",
                pal.fgDim, 12f, false);
        header.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 7), Ui.dp(ctx, 10), Ui.dp(ctx, 7));
        header.setBackground(Ui.box(ctx, pal.bg3, pal.line, 6));
        header.setClickable(true);

        final EditText body = new EditText(ctx);
        body.setText(value.toString());
        body.setTextColor(pal.fgDim);
        body.setTextSize(11f);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setBackground(Ui.box(ctx, pal.bg2, pal.line, 6));
        body.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6));
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setMinLines(2);
        body.setMaxLines(14);
        body.setHorizontallyScrolling(false);
        body.setFocusable(false);
        body.setVisibility(View.GONE);

        final boolean[] open = { false };
        header.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                open[0] = !open[0];
                body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            }
        });

        row.addView(header);
        row.addView(body);
        parent.addView(row);
        f.nestedHeader = header;
        f.nestedBody = body;
        fields.add(f);
    }

    private TextView keyLabel(Context ctx, String key, String hint, Ui.Palette pal) {
        TextView t = Ui.text(ctx, key + (hint == null ? "" : "   \u00b7   " + hint), pal.fgDim, 12f, false);
        t.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 3));
        return t;
    }

    // ------------------------------------------------------------------ apply

    /** Write every edited field back into the record. Unknown keys are preserved. */
    public void apply() {
        if (wholeArray) {
            if (fields.isEmpty()) return;
            Object v = fields.get(0).read();
            if (v instanceof JSONArray && targetArray != null) {
                replace(targetArray, (JSONArray) v);
            }
            return;
        }
        if (targetObject == null) return;
        for (Field f : fields) {
            if (f.key == null || f.kind == K_NESTED) continue;
            JsonText.put(targetObject, f.key, f.read());
        }
    }

    /** Replace the contents of {@code target} with {@code replacement}, keeping identity. */
    private static void replace(JSONArray target, JSONArray replacement) {
        while (target.length() > 0) target.remove(target.length() - 1);
        for (int i = 0; i < replacement.length(); i++) target.put(replacement.opt(i));
    }
}