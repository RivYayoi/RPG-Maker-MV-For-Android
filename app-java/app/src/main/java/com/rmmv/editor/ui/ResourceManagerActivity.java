package com.rmmv.editor.ui;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rmmv.editor.R;
import com.rmmv.editor.core.Project;
import com.rmmv.editor.core.Session;
import com.rmmv.editor.ui.resource.ResourceTree;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tool &gt; Resource Manager -- a read-only tree browser over the project folder.
 *
 * <p>Shows the same fixed category roots as the PC editor (img/tilesets ...
 * js), always, whether or not the folder exists. Tapping a directory expands
 * it; tapping an image opens it full screen; other files simply report that
 * there is no preview.
 *
 * <p>Deleting, renaming and replacing game assets is deliberately not supported
 * here -- those operations need the editor's own import pipeline.
 */
public class ResourceManagerActivity extends AppCompatActivity {

    /** Decode target for the preview; larger images are subsampled down. */
    private static final int PREVIEW_MAX_PX = 2048;

    private RecyclerView list;
    private TextView subtitle;
    private Ui.Palette pal;
    private Project project;
    private ResourceTree tree;
    private NodeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resource_manager);

        pal = new Ui.Palette(
                getColor(R.color.bg), getColor(R.color.bg2), getColor(R.color.bg3),
                getColor(R.color.line), getColor(R.color.fg), getColor(R.color.fg_dim),
                getColor(R.color.accent), getColor(R.color.danger));

        list = findViewById(R.id.rvResList);
        subtitle = findViewById(R.id.tvResSubtitle);

        String projectName = getIntent().getStringExtra("project");
        try {
            project = Session.require(this, projectName);
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.db_open_failed, String.valueOf(e.getMessage())));
            finish();
            return;
        }

        tree = new ResourceTree(project.dir());
        adapter = new NodeAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        updateSubtitle();
    }

    private void updateSubtitle() {
        subtitle.setText(getString(R.string.res_root_label, project.dir().getName())
                + "  \u00b7  " + getString(R.string.res_readonly));
    }

    // ---------------------------------------------------------------- actions

    private void onNode(ResourceTree.Node node) {
        if (node.file == null) return;
        if (node.dir) {
            if (!node.exists) {
                Ui.toast(this, getString(R.string.res_missing_dir, node.rel));
                return;
            }
            tree.toggle(node);
            adapter.notifyDataSetChanged();
            return;
        }
        if (ResourceTree.isImage(node.file.getName())) {
            showImage(node);
        } else {
            Ui.toast(this, getString(R.string.res_no_preview, node.rel));
        }
    }

    private void showImage(ResourceTree.Node node) {
        Bitmap bmp = decode(node.file);
        if (bmp == null) {
            Ui.toast(this, getString(R.string.res_decode_failed, node.file.getName()));
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_image, null);
        final ImageView iv = (ImageView) view.findViewById(R.id.ivResPreview);
        final TextView hint = (TextView) view.findViewById(R.id.tvResPreviewHint);
        iv.setImageBitmap(bmp);
        hint.setText(getString(R.string.res_preview_hint, node.rel,
                Ui.fileSize(node.file.length())));

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(view);
        dialog.setCanceledOnTouchOutside(true);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        View.OnClickListener dismiss = new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        };
        view.setOnClickListener(dismiss);
        hint.setOnClickListener(dismiss);
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) {
                // The ImageView still references the bitmap; drop it explicitly.
                iv.setImageDrawable(null);
            }
        });
        dialog.show();
    }

    /** Decode with a power-of-two sample so huge tilesets do not blow up memory. */
    private static Bitmap decode(File f) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= PREVIEW_MAX_PX
                    && bounds.outHeight / (sample * 2) >= PREVIEW_MAX_PX) {
                sample *= 2;
            }
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    // -------------------------------------------------------------- list view

    private final class NodeAdapter extends RecyclerView.Adapter<NodeAdapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final TextView caret;
            final TextView name;
            final TextView size;

            VH(View v) {
                super(v);
                caret = (TextView) v.findViewById(R.id.tvResCaret);
                name = (TextView) v.findViewById(R.id.tvResName);
                size = (TextView) v.findViewById(R.id.tvResSize);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        int pos = getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;
                        onNode(tree.rows().get(pos));
                    }
                });
                v.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View view) {
                        int pos = getBindingAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION
                                && tree.rows().get(pos).depth == 0
                                && tree.rows().get(pos).expanded) {
                            tree.collapseAll();
                            adapter.notifyDataSetChanged();
                            return true;
                        }
                        return false;
                    }
                });
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_resource, parent, false));
        }

        @Override public void onBindViewHolder(VH h, int position) {
            ResourceTree.Node n = tree.rows().get(position);
            h.name.setText(n.dir ? ResourceTree.baseName(n.rel) : n.file.getName());
            h.name.setTextColor(n.exists ? pal.fg : pal.fgDim);
            h.name.setPadding(
                    Ui.dp(ResourceManagerActivity.this, 8 + 14 * n.depth),
                    Ui.dp(ResourceManagerActivity.this, 10),
                    Ui.dp(ResourceManagerActivity.this, 8),
                    Ui.dp(ResourceManagerActivity.this, 10));

            if (n.dir) {
                h.caret.setText(n.expanded ? "\u25be" : "\u25b8");
                h.size.setText(n.exists ? "" : getString(R.string.res_absent));
                h.size.setTextColor(pal.fgDim);
            } else {
                h.caret.setText("");
                h.size.setText(Ui.fileSize(n.size));
                h.size.setTextColor(pal.fgDim);
            }
            h.itemView.setBackgroundColor(n.dir ? pal.bg2 : pal.bg);
        }

        @Override public int getItemCount() {
            List<ResourceTree.Node> rows = tree.rows();
            return rows == null ? 0 : rows.size();
        }
    }
}