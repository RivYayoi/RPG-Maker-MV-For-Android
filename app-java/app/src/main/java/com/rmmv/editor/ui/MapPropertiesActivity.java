package com.rmmv.editor.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.rmmv.editor.R;
import com.rmmv.editor.core.MvMap;
import com.rmmv.editor.core.Project;
import com.rmmv.editor.core.Session;
import com.rmmv.editor.core.Tileset;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PC 01_07_03 "Map Properties".
 *
 * <p>Edits the map document's own fields -- display name, parallax, encounter
 * rate, BGM/BGS, scroll type and dashing -- plus the map list entry's
 * name/order/parentId. Fields are read from and written back to the ORIGINAL
 * JSON object so anything this screen does not model survives the round trip.
 */
public class MapPropertiesActivity extends AppCompatActivity {

    private Project project;
    private int mapId;
    private MvMap map;

    private LinearLayout box;
    private EditText displayName, parallaxName, note, bgsName;
    private EditText encounterStep, parallaxSx, parallaxSy;
    private CheckBox parallaxShow, parallaxLoopX, parallaxLoopY;
    private CheckBox autoplayBgm, autoplayBgs, specifyBattleback, disableDashing;
    private Spinner scrollType;
    private Spinner tilesetSpinner;
    private EditText mapNameEntry;

    private List<Integer> tilesetIds = new ArrayList<>();
    private List<String> tilesetLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            project = Session.require(this, getIntent().getStringExtra("project"));
            mapId = getIntent().getIntExtra("mapId", 1);
            map = project.map();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        box.setPadding(pad, dp(14), pad, pad);

        buildTilesetSpinner();
        mapNameEntry = text("Map list name", project.mapInfos().name(mapId));
        displayName = text("Display name", map.raw != null ? map.raw.optString("displayName", "") : "");
        note = text("Note", map.raw != null ? map.raw.optString("note", "") : "");

        header("Parallax");
        parallaxName = text("Image name", map.raw != null ? map.raw.optString("parallaxName", "") : "");
        parallaxSx = number("Scroll X", map.raw != null ? map.raw.optInt("parallaxSx", 0) : 0);
        parallaxSy = number("Scroll Y", map.raw != null ? map.raw.optInt("parallaxSy", 0) : 0);
        parallaxShow = check("Show", map.raw == null || map.raw.optBoolean("parallaxShow", true));
        parallaxLoopX = check("Loop X", map.raw != null && map.raw.optBoolean("parallaxLoopX", false));
        parallaxLoopY = check("Loop Y", map.raw != null && map.raw.optBoolean("parallaxLoopY", false));

        header("Encounters");
        encounterStep = number("Encounter step", map.raw != null ? map.raw.optInt("encounterStep", 30) : 30);

        header("Audio");
        JSONObject bgm = map.raw != null ? map.raw.optJSONObject("bgm") : null;
        bgsName = text("BGM name", bgm != null ? bgm.optString("name", "") : "");
        autoplayBgm = check("Autoplay BGM", map.raw != null && map.raw.optBoolean("autoplayBgm", false));
        autoplayBgs = check("Autoplay BGS", map.raw != null && map.raw.optBoolean("autoplayBgs", false));

        header("Scroll");
        scrollType = spinner(new String[]{"None", "Vertical", "Horizontal", "Both"},
                map.raw != null ? map.raw.optInt("scrollType", 0) : 0);
        disableDashing = check("Disable dashing", map.raw != null && map.raw.optBoolean("disableDashing", false));
        specifyBattleback = check("Specify battlebacks",
                map.raw != null && map.raw.optBoolean("specifyBattleback", false));

        new AlertDialog.Builder(this)
                .setTitle(R.string.map_properties)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> apply())
                .show();
    }

    // ------------------------------------------------------------- builders

    private void buildTilesetSpinner() {
        JSONArray arr = project.readDatabaseArray("Tilesets.json");
        tilesetIds.clear();
        tilesetLabels.clear();
        int sel = 0;
        if (arr != null) {
            for (int i = 1; i < arr.length(); i++) {
                JSONObject t = arr.optJSONObject(i);
                if (t == null) continue;
                tilesetIds.add(i);
                tilesetLabels.add(i + " · " + t.optString("name", "Tileset" + i));
                if (i == map.tilesetId) sel = tilesetIds.size() - 1;
            }
        }
        if (tilesetLabels.isEmpty()) tilesetLabels.add("(no tilesets)");
        header("Tileset");
        Spinner s = spinner(tilesetLabels.toArray(new String[0]), sel);
        tilesetSpinner = s;
    }

    private void header(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(getResources().getColor(R.color.accent));
        v.setTextSize(12f);
        v.setPadding(0, dp(14), 0, dp(4));
        box.addView(v);
    }

    private EditText text(String label, String value) {
        TextView l = label(label);
        box.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(getResources().getColor(R.color.fg));
        box.addView(e);
        return e;
    }

    private EditText number(String label, int value) {
        TextView l = label(label);
        box.addView(l);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        e.setTextColor(getResources().getColor(R.color.fg));
        box.addView(e);
        return e;
    }

    private CheckBox check(String label, boolean value) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(value);
        c.setTextColor(getResources().getColor(R.color.fg));
        box.addView(c);
        return c;
    }

    private Spinner spinner(String[] items, int sel) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        if (sel >= 0 && sel < items.length) s.setSelection(sel);
        box.addView(s);
        return s;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(getResources().getColor(R.color.fg_dim));
        v.setTextSize(12f);
        v.setPadding(0, dp(8), 0, dp(2));
        return v;
    }

    // --------------------------------------------------------------- apply

    private void apply() {
        try {
            project.beginEdit();

            JSONObject raw = (map.raw != null) ? map.raw : new JSONObject();
            raw.put("displayName", displayName.getText().toString());
            raw.put("note", note.getText().toString());
            raw.put("parallaxName", parallaxName.getText().toString());
            raw.put("parallaxSx", parseInt(parallaxSx.getText().toString(), 0));
            raw.put("parallaxSy", parseInt(parallaxSy.getText().toString(), 0));
            raw.put("parallaxShow", parallaxShow.isChecked());
            raw.put("parallaxLoopX", parallaxLoopX.isChecked());
            raw.put("parallaxLoopY", parallaxLoopY.isChecked());
            raw.put("encounterStep", parseInt(encounterStep.getText().toString(), 30));
            raw.put("autoplayBgm", autoplayBgm.isChecked());
            raw.put("autoplayBgs", autoplayBgs.isChecked());
            raw.put("scrollType", scrollType.getSelectedItemPosition());
            raw.put("disableDashing", disableDashing.isChecked());
            raw.put("specifyBattleback", specifyBattleback.isChecked());

            JSONObject bgm = raw.optJSONObject("bgm");
            if (bgm == null) bgm = new JSONObject();
            bgm.put("name", bgsName.getText().toString());
            raw.put("bgm", bgm);

            int tsIdx = tilesetSpinner.getSelectedItemPosition();
            if (tsIdx >= 0 && tsIdx < tilesetIds.size()) {
                map.tilesetId = tilesetIds.get(tsIdx);
            }

            map.raw = raw;
            project.renameMap(mapId, mapNameEntry.getText().toString());
            project.markDirty();
            project.save();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
