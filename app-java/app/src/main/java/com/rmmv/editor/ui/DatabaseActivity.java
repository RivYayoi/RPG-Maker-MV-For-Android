package com.rmmv.editor.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rmmv.editor.R;
import com.rmmv.editor.core.Project;
import com.rmmv.editor.core.Session;
import com.rmmv.editor.ui.db.EntityCatalog;
import com.rmmv.editor.ui.db.JsonText;
import com.rmmv.editor.ui.db.RecordForm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Tool &gt; Database -- the PC editor's Database window.
 *
 * <p>Master/detail on one screen: a spinner picks the entity page, the list shows
 * that page's records, and the form below is generated from the keys of the
 * selected record (see {@link RecordForm}).
 *
 * <p>Saving pushes the form back into the record it was built from, so keys this
 * screen never renders are written through unchanged, then writes the whole
 * array/object back with {@link Project#writeDatabase} followed by
 * {@link Project#save()} so the rest of the project state stays consistent.
 */
public class DatabaseActivity extends AppCompatActivity {

    private Spinner typeSpinner;
    private TextView subtitle;
    private RecyclerView list;
    private ScrollView formScroll;
    private LinearLayout formContainer;
    private Button btnAdd;
    private Button btnDelete;
    private Button btnSave;

    private Project project;
    private Ui.Palette pal;

    private EntityCatalog.Entity entity;
    /** Working copy of the current page's array (array pages only). */
    private JSONArray array;
    /** Working copy of the current page's object (System / Terms pages only). */
    private JSONObject single;
    private int selected = -1;
    private RecordForm form;

    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database);

        pal = new Ui.Palette(
                getColor(R.color.bg), getColor(R.color.bg2), getColor(R.color.bg3),
                getColor(R.color.line), getColor(R.color.fg), getColor(R.color.fg_dim),
                getColor(R.color.accent), getColor(R.color.danger));

        typeSpinner = findViewById(R.id.spDbType);
        subtitle = findViewById(R.id.tvDbSubtitle);
        list = findViewById(R.id.rvDbList);
        formScroll = findViewById(R.id.svDbForm);
        formContainer = findViewById(R.id.llDbForm);
        btnAdd = findViewById(R.id.btnDbAdd);
        btnDelete = findViewById(R.id.btnDbDelete);
        btnSave = findViewById(R.id.btnDbSave);

        String projectName = getIntent().getStringExtra("project");
        try {
            project = Session.require(this, projectName);
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.db_open_failed, String.valueOf(e.getMessage())));
            finish();
            return;
        }

        adapter = new RecordAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        setUpSpinner();
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onAdd(); }
        });
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onDelete(); }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { commit(true); }
        });

        loadPage(typeSpinner.getSelectedItemPosition());
    }

    // ------------------------------------------------------------ entity tabs

    private void setUpSpinner() {
        String[] labels = getResources().getStringArray(R.array.db_entity_labels);
        int n = Math.min(labels.length, EntityCatalog.COUNT);
        String[] shown = new String[n];
        System.arraycopy(labels, 0, shown, 0, n);

        ArrayAdapter<String> a = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, shown) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(pal.fg);
                t.setTextSize(14f);
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(a);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                loadPage(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }

    // -------------------------------------------------------------- page load

    private void loadPage(int pos) {
        if (pos < 0 || pos >= EntityCatalog.COUNT) return;
        if (entity != null) applyPendingEdits();
        entity = EntityCatalog.ENTITIES[pos];
        array = null;
        single = null;
        selected = -1;
        form = null;

        if (entity.single) {
            single = project.readDatabase(entity.file);
            setListVisible(false);
            formScroll.setVisibility(View.VISIBLE);
            clearForm();
            btnAdd.setEnabled(false);
            btnDelete.setEnabled(false);
            btnSave.setEnabled(single != null);
            if (single == null) {
                subtitle.setText(getString(R.string.db_missing_file, entity.file));
            } else {
                subtitle.setText(entity.file);
                form = RecordForm.buildObject(this, formContainer, single, pal);
            }
            return;
        }

        formScroll.setVisibility(View.VISIBLE);
        btnAdd.setEnabled(true);
        btnDelete.setEnabled(false);

        boolean missing = false;
        array = project.readDatabaseArray(entity.file);
        if (array == null) {
            // New projects ship without Types.json / Terms.json. Leave the file
            // alone until Add actually creates it.
            missing = true;
            btnSave.setEnabled(false);
            array = new JSONArray();
        } else {
            btnSave.setEnabled(true);
        }
        adapter.notifyDataSetChanged();
        clearForm();
        setListVisible(true);
        selectFirst();
        if (!missing) subtitle.setText(getString(R.string.db_records, Integer.valueOf(countRecords())));
    }

    private int countRecords() {
        if (array == null) return 0;
        int n = 0;
        for (int i = 1; i < array.length(); i++) if (!array.isNull(i)) n++;
        return n;
    }

    private void selectFirst() {
        if (array == null) return;
        for (int i = 1; i < array.length(); i++) {
            if (!array.isNull(i)) { select(i); return; }
        }
        selected = -1;
        btnDelete.setEnabled(false);
    }

    private void select(int index) {
        if (array == null || index < 0 || index >= array.length()) return;
        setListVisible(true);
        if (index == 0 || array.isNull(index)) {
            selected = -1;
            btnDelete.setEnabled(false);
            clearForm();
            Ui.toast(this, getString(R.string.db_empty_slot));
            adapter.notifyDataSetChanged();
            return;
        }
        selected = index;
        btnDelete.setEnabled(true);
        subtitle.setText(getString(R.string.db_records, Integer.valueOf(countRecords())));
        buildForm(array.opt(index));
        adapter.notifyDataSetChanged();
        list.scrollToPosition(index);
    }

    private void buildForm(Object rec) {
        clearForm();
        if (entity.arrayRecords && rec instanceof JSONArray) {
            form = RecordForm.buildArray(this, formContainer, (JSONArray) rec, pal);
        } else if (rec instanceof JSONObject) {
            form = RecordForm.buildObject(this, formContainer, (JSONObject) rec, pal);
        } else {
            subtitle.setText(getString(R.string.db_unexpected_record, entity.file));
        }
    }

    private void clearForm() {
        form = null;
        formContainer.removeAllViews();
    }

    private void setListVisible(boolean visible) {
        list.setVisibility(visible ? View.VISIBLE : View.GONE);
        View divider = findViewById(R.id.vDbDivider);
        if (divider != null) divider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- editing

    /**
     * Push the open form back into its record.
     *
     * <p>Called before every navigation and before every write. Idempotent:
     * untouched widgets read back exactly the value they were built from, with
     * the original JSON type.
     */
    private void applyPendingEdits() {
        if (form != null) form.apply();
    }

    private void onAdd() {
        if (array == null) return;
        applyPendingEdits();

        Object fresh;
        if (entity.arrayRecords) {
            fresh = new JSONArray();
        } else {
            JSONObject o = new JSONObject();
            JsonText.put(o, "id", Integer.valueOf(nextId()));
            JsonText.put(o, "name", getString(R.string.db_new_record));
            fresh = o;
        }
        JsonText.put(array, fresh);
        // Index 0 is the reserved null slot in every MV database array.
        if (array.length() == 1 && !array.isNull(0)) {
            JSONArray fixed = new JSONArray();
            JsonText.put(fixed, JSONObject.NULL);
            JsonText.put(fixed, array.opt(0));
            array = fixed;
        }
        int index = array.length() - 1;
        commit(false);
        select(index);
    }

    private int nextId() {
        int max = 0;
        for (int i = 1; i < array.length(); i++) {
            if (array.isNull(i)) continue;
            Object rec = array.opt(i);
            if (!(rec instanceof JSONObject)) continue;
            Object id = ((JSONObject) rec).opt("id");
            if (id instanceof Number) max = Math.max(max, ((Number) id).intValue());
        }
        return max + 1;
    }

    private void onDelete() {
        if (array == null || selected < 0 || selected >= array.length()) return;
        final int index = selected;
        new AlertDialog.Builder(this)
                .setTitle(R.string.db_delete_title)
                .setMessage(getString(R.string.db_delete_msg, Integer.valueOf(index)))
                .setPositiveButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        applyPendingEdits();
                        JsonText.put(array, index, JSONObject.NULL);
                        selected = -1;
                        btnDelete.setEnabled(false);
                        commit(false);
                        selectFirst();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Write the current page to disk. */
    private void commit(boolean notify) {
        if (array == null && single == null) return;
        applyPendingEdits();
        try {
            Object payload = (array != null) ? (Object) array : (Object) single;
            project.writeDatabase(entity.file, payload);
            project.save();
            if (notify) {
                Ui.toast(this, getString(R.string.db_saved, entity.file));
                adapter.notifyDataSetChanged();
            }
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.db_save_failed,
                    entity.file + ": " + e.getMessage()));
        }
    }

    // -------------------------------------------------------------- list view

    private final class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final TextView index;
            final TextView label;

            VH(View v) {
                super(v);
                index = (TextView) v.findViewById(R.id.tvDbRowIndex);
                label = (TextView) v.findViewById(R.id.tvDbRowText);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        int pos = getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;
                        applyPendingEdits();
                        select(pos);
                    }
                });
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_db_record, parent, false));
        }

        @Override public void onBindViewHolder(VH h, int position) {
            Object rec = array == null ? null : array.opt(position);
            boolean empty = rec == null || rec == JSONObject.NULL;
            h.index.setText(position == 0 ? "--" : String.valueOf(position));
            h.label.setText(JsonText.recordLabel(rec));
            h.label.setTextColor(empty ? pal.fgDim : pal.fg);
            h.label.setTypeface(position == selected
                    ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            h.itemView.setBackground(Ui.box(DatabaseActivity.this,
                    position == selected ? pal.bg3 : pal.bg, pal.line, 0f));
            h.itemView.setAlpha(empty ? 0.5f : 1f);
        }

        @Override public int getItemCount() {
            return array == null ? 0 : array.length();
        }
    }
}