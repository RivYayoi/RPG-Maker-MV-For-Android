package com.rmmv.editor.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rmmv.editor.R;
import com.rmmv.editor.core.Project;
import com.rmmv.editor.core.Session;
import com.rmmv.editor.ui.db.JsonText;
import com.rmmv.editor.ui.plugin.PluginEntry;
import com.rmmv.editor.ui.plugin.PluginsJs;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Tool &gt; Plugin Manager -- the PC editor's Plugin Manager.
 *
 * <p>Reads {@code js/plugins.js}, toggles a plugin's {@code status}, and edits
 * name / description / author / version. Files that exist under
 * {@code js/plugins/} but are not listed in the array are shown as
 * "not registered" &mdash; the game never loads those.
 *
 * <p>Writes go through {@link PluginsJs.Doc#render()}, which splices the new
 * array literal back between the original opening and closing brackets, so the
 * generated header comment, the variable name and the trailing {@code ;} are
 * preserved exactly.
 */
public class PluginManagerActivity extends AppCompatActivity {

    private static final String PLUGINS_JS = "js/plugins.js";
    private static final String PLUGINS_DIR = "js/plugins";

    private RecyclerView list;
    private TextView subtitle;
    private Ui.Palette pal;
    private Project project;

    private File pluginsJs;
    private PluginsJs.Doc doc;
    private final List<PluginEntry> entries = new ArrayList<>();

    private PluginAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_manager);

        pal = new Ui.Palette(
                getColor(R.color.bg), getColor(R.color.bg2), getColor(R.color.bg3),
                getColor(R.color.line), getColor(R.color.fg), getColor(R.color.fg_dim),
                getColor(R.color.accent), getColor(R.color.danger));

        list = findViewById(R.id.rvPluginList);
        subtitle = findViewById(R.id.tvPluginSubtitle);

        String projectName = getIntent().getStringExtra("project");
        try {
            project = Session.require(this, projectName);
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.db_open_failed, String.valueOf(e.getMessage())));
            finish();
            return;
        }

        adapter = new PluginAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        reload();
    }

    // ------------------------------------------------------------------ load

    private void reload() {
        entries.clear();
        pluginsJs = new File(project.dir(), PLUGINS_JS);
        doc = null;

        if (!pluginsJs.exists()) {
            subtitle.setText(getString(R.string.plug_missing, PLUGINS_JS));
            adapter.notifyDataSetChanged();
            return;
        }
        try {
            String text = project.store().readText(pluginsJs);
            doc = PluginsJs.parse(text);
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.plug_read_failed, String.valueOf(e.getMessage())));
        }
        if (doc == null) {
            subtitle.setText(getString(R.string.plug_unparsable, PLUGINS_JS));
            adapter.notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < doc.array.length(); i++) {
            JSONObject o = doc.array.optJSONObject(i);
            if (o != null) entries.add(PluginEntry.registered(o));
        }
        entries.addAll(unregistered());
        subtitle.setText(getString(R.string.plug_count, Integer.valueOf(entries.size())));
        adapter.notifyDataSetChanged();
    }

    /** {@code .js} files under js/plugins/ that no entry in the array points at. */
    private List<PluginEntry> unregistered() {
        List<PluginEntry> out = new ArrayList<>();
        File dir = new File(project.dir(), PLUGINS_DIR);
        File[] kids = dir.listFiles();
        if (kids == null) return out;
        Arrays.sort(kids, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File f : kids) {
            if (!f.isFile()) continue;
            if (!f.getName().toLowerCase(java.util.Locale.US).endsWith(".js")) continue;
            boolean known = false;
            for (PluginEntry e : entries) {
                if (e.isRegistered() && f.getName().equalsIgnoreCase(e.fileName())) {
                    known = true;
                    break;
                }
            }
            if (!known) out.add(PluginEntry.unregistered(f.getName()));
        }
        return out;
    }

    // ------------------------------------------------------------------ save

    private void save() {
        if (doc == null || pluginsJs == null) return;
        try {
            project.store().writeText(pluginsJs, doc.render());
            Ui.toast(this, getString(R.string.plug_saved));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.plug_save_failed, String.valueOf(e.getMessage())));
        }
    }

    private void register(PluginEntry entry) {
        if (doc == null) return;
        JsonText.put(doc.array, PluginEntry.newRecord(entry.fileName()));
        save();
        reload();
    }

    // ---------------------------------------------------------------- dialog

    private void openDetail(final PluginEntry entry) {
        final View view = getLayoutInflater().inflate(R.layout.dialog_plugin_edit, null);
        final TextView title = (TextView) view.findViewById(R.id.tvPlugTitle);
        final View sectionEdit = view.findViewById(R.id.llPlugEdit);
        final View sectionUnreg = view.findViewById(R.id.llPlugUnreg);
        final EditText edName = (EditText) view.findViewById(R.id.edPlugName);
        final EditText edAuthor = (EditText) view.findViewById(R.id.edPlugAuthor);
        final EditText edVersion = (EditText) view.findViewById(R.id.edPlugVersion);
        final EditText edDescription = (EditText) view.findViewById(R.id.edPlugDescription);
        final Switch swStatus = (Switch) view.findViewById(R.id.swPlugStatus);
        final TextView tvPath = (TextView) view.findViewById(R.id.tvPlugPath);
        final TextView tvParams = (TextView) view.findViewById(R.id.tvPlugParams);
        final Button btnOk = (Button) view.findViewById(R.id.btnPlugOk);
        final Button btnRegister = (Button) view.findViewById(R.id.btnPlugRegister);

        title.setText(entry.isRegistered() ? entry.name() : entry.fileName());

        final Dialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (entry.isRegistered()) {
            sectionEdit.setVisibility(View.VISIBLE);
            sectionUnreg.setVisibility(View.GONE);
            edName.setText(entry.name());
            edAuthor.setText(entry.author());
            edVersion.setText(entry.version());
            edDescription.setText(entry.description());
            swStatus.setChecked(entry.status());
            tvPath.setText(getString(R.string.plug_path_label, entry.pluginName()));
            int params = entry.parameterCount();
            tvParams.setText(params == 0
                    ? getString(R.string.plug_no_params)
                    : getString(R.string.plug_params_count, Integer.valueOf(params)));
            tvParams.setVisibility(View.VISIBLE);
        } else {
            sectionEdit.setVisibility(View.GONE);
            sectionUnreg.setVisibility(View.VISIBLE);
            btnOk.setVisibility(View.GONE);
            btnRegister.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    register(entry);
                    Ui.toast(PluginManagerActivity.this,
                            getString(R.string.plug_registered, entry.fileName()));
                    dialog.dismiss();
                }
            });
        }

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                entry.setName(edName.getText().toString().trim());
                entry.setAuthor(edAuthor.getText().toString());
                entry.setVersion(edVersion.getText().toString().trim());
                entry.setDescription(edDescription.getText().toString());
                entry.setStatus(swStatus.isChecked());
                save();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.btnPlugCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        dialog.show();
    }

    // -------------------------------------------------------------- list view

    private final class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView meta;
            final TextView desc;
            final Switch toggle;
            final View toggleHolder;

            VH(View v) {
                super(v);
                name = (TextView) v.findViewById(R.id.tvPlugName);
                meta = (TextView) v.findViewById(R.id.tvPlugMeta);
                desc = (TextView) v.findViewById(R.id.tvPlugDesc);
                toggle = (Switch) v.findViewById(R.id.swPlugRow);
                toggleHolder = v.findViewById(R.id.llPlugRowToggle);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        int pos = getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;
                        openDetail(entries.get(pos));
                    }
                });
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_plugin, parent, false));
        }

        @Override public void onBindViewHolder(VH h, int position) {
            final PluginEntry e = entries.get(position);

            h.name.setText(e.name());
            h.name.setTextColor(pal.fg);

            StringBuilder meta = new StringBuilder();
            if (e.isRegistered()) {
                if (!e.version().isEmpty()) meta.append(getString(R.string.plug_version_prefix, e.version()));
                if (!e.author().isEmpty()) {
                    if (meta.length() > 0) meta.append("  \u00b7  ");
                    meta.append(e.author());
                }
                if (meta.length() == 0) meta.append(e.pluginName());
            } else {
                meta.append(getString(R.string.plug_unregistered));
            }
            h.meta.setText(meta.toString());
            h.meta.setTextColor(e.isRegistered() ? pal.fgDim : pal.accent);

            String d = e.description().replaceAll("\\s*\\n\\s*", " ");
            h.desc.setText(d);
            h.desc.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);

            h.toggleHolder.setVisibility(e.isRegistered() ? View.VISIBLE : View.INVISIBLE);
            h.toggle.setOnCheckedChangeListener(null);
            h.toggle.setChecked(e.status());
            h.toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                    e.setStatus(checked);
                    save();
                }
            });
        }

        @Override public int getItemCount() { return entries.size(); }
    }
}