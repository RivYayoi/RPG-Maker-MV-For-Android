package com.rmmv.editor;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rmmv.editor.core.Project;
import com.rmmv.editor.core.ProjectStore;
import com.rmmv.editor.core.Session;
import com.rmmv.editor.ui.MapEditorActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Project list -- the PC editor's [File] &gt; Open Project entry point.
 *
 * <p>Native Views only: RecyclerView + AlertDialog. No WebView, no HTML.
 */
public class MainActivity extends AppCompatActivity {

    private ProjectStore store;
    private final List<ProjectStore.ProjectInfo> data = new ArrayList<>();
    private Adapter adapter;
    private TextView storageNote;
    private View empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = Session.store(this);

        storageNote = findViewById(R.id.storageNote);
        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        storageNote.setText(getString(R.string.storage_note,
                store.projectsRoot().getAbsolutePath()));

        findViewById(R.id.btnRefresh).setOnClickListener(v -> refresh());
        findViewById(R.id.btnNew).setOnClickListener(v -> newProjectDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        data.clear();
        data.addAll(store.listProjects());
        adapter.notifyDataSetChanged();
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ File>New

    private void newProjectDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        box.setPadding(pad, dp(8), pad, 0);

        final EditText nameIn = field(box, getString(R.string.file_new_project), "MyGame");
        final EditText titleIn = field(box, getString(R.string.help_about), "我的游戏");
        final EditText wIn = field(box, "Width (tiles)", "20");
        final EditText hIn = field(box, "Height (tiles)", "15");
        wIn.setInputType(InputType.TYPE_CLASS_NUMBER);
        hIn.setInputType(InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle(R.string.file_new_project)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = nameIn.getText().toString().trim();
                    if (name.isEmpty()) { toast("Enter a project name"); return; }
                    if (store.exists(name)) { toast("That project already exists"); return; }
                    try {
                        ProjectStore.ProjectInfo pi = store.createProject(
                                name,
                                titleIn.getText().toString().trim(),
                                parseInt(wIn.getText().toString(), 20),
                                parseInt(hIn.getText().toString(), 15));
                        refresh();
                        openProject(pi.name);
                    } catch (Exception e) {
                        toast("Create failed: " + e.getMessage());
                    }
                })
                .show();
    }

    private EditText field(LinearLayout parent, String label, String value) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getResources().getColor(R.color.fg_dim));
        l.setTextSize(12f);
        l.setPadding(0, dp(10), 0, dp(4));
        parent.addView(l);

        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(getResources().getColor(R.color.fg));
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        parent.addView(e);
        return e;
    }

    private void openProject(String name) {
        try {
            Session.open(this, name);
            startActivity(new Intent(this, MapEditorActivity.class)
                    .putExtra("project", name));
        } catch (Exception e) {
            toast("Open failed: " + e.getMessage());
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // --------------------------------------------------------------- adapter

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView name, meta;
            ImageButton del;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                meta = v.findViewById(R.id.meta);
                del = v.findViewById(R.id.delete);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int vt) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_project, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            final ProjectStore.ProjectInfo p = data.get(pos);
            h.name.setText(p.name);
            String t = (p.title != null && !p.title.isEmpty()) ? p.title + " · " : "";
            h.meta.setText(t + p.mapCount + " maps");
            h.itemView.setOnClickListener(v -> openProject(p.name));
            h.del.setOnClickListener(v -> confirmDelete(p));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    private void confirmDelete(final ProjectStore.ProjectInfo p) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage("Permanently delete \"" + p.name + "\"?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.deleteProject(p.name);
                    refresh();
                })
                .show();
    }
}
