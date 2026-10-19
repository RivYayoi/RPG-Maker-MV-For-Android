package com.rmmv.editor.ui.resource;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Flattened, expandable view of the project directory behind the PC editor's
 * Resource Manager.
 *
 * <p>The editor always shows the same fixed set of category roots in the same
 * order, whether or not the folder exists yet, so the tree is driven by
 * {@link #CATEGORIES} rather than by whatever happens to be on disk.
 *
 * <p>Expansion state is a set of relative paths; {@link #rebuild()} recomputes
 * the flat row list (directories before files, each alphabetically) from it.
 */
public final class ResourceTree {

    /** Category roots, in the PC editor's order. */
    public static final String[] CATEGORIES = {
            "img/tilesets",
            "img/characters",
            "img/enemies",
            "img/faces",
            "img/battlebacks1",
            "img/battlebacks2",
            "img/parallaxes",
            "img/system",
            "img/titles1",
            "img/titles2",
            "img/animations",
            "img/sv_actors",
            "img/sv_enemies",
            "audio/bgm",
            "audio/bgs",
            "audio/me",
            "audio/se",
            "fonts",
            "icon",
            "data",
            "js"
    };

    /** One visible row. */
    public static final class Node {
        /** Absolute path on disk (null for a category that does not exist). */
        public final File file;
        /** '/'-separated path relative to the project root. */
        public final String rel;
        public final int depth;
        public final boolean dir;
        public final boolean exists;
        public boolean expanded;
        /** Byte length; 0 for directories and missing paths. */
        public long size;

        Node(File file, String rel, int depth, boolean dir) {
            this.file = file;
            this.rel = rel;
            this.depth = depth;
            this.dir = dir;
            this.exists = file != null && file.exists();
        }
    }

    private final File root;
    private final List<Node> rows = new ArrayList<>();
    private final Set<String> expanded = new LinkedHashSet<>();

    public ResourceTree(File root) {
        this.root = root;
        rebuild();
    }

    // ------------------------------------------------------------------ rows

    public List<Node> rows() { return rows; }

    /** Expand or collapse a directory row, then rebuild. */
    public void toggle(Node node) {
        if (node == null || !node.dir) return;
        if (!expanded.remove(node.rel)) expanded.add(node.rel);
        rebuild();
    }

    /** Collapse every directory (long-press on a category root). */
    public void collapseAll() {
        expanded.clear();
        rebuild();
    }

    // --------------------------------------------------------------- rebuild

    public void rebuild() {
        rows.clear();
        if (root == null) return;
        for (String cat : CATEGORIES) {
            File d = new File(root, cat);
            Node n = new Node(d, cat, 0, true);
            n.expanded = expanded.contains(cat);
            rows.add(n);
            if (n.expanded) appendChildren(d, cat, 1);
        }
    }

    private void appendChildren(File dir, String rel, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        sort(kids);
        for (File k : kids) {
            String childRel = rel + "/" + k.getName();
            if (k.isDirectory()) {
                Node n = new Node(k, childRel, depth, true);
                n.expanded = expanded.contains(childRel);
                rows.add(n);
                if (n.expanded) appendChildren(k, childRel, depth + 1);
            } else {
                Node n = new Node(k, childRel, depth, false);
                n.size = k.length();
                rows.add(n);
            }
        }
    }

    private static void sort(File[] files) {
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
    }

    // ------------------------------------------------------------------ utils

    public static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    /** Short label of a path for a breadcrumb / tooltip. */
    public static String baseName(String rel) {
        int slash = rel.lastIndexOf('/');
        return slash >= 0 ? rel.substring(slash + 1) : rel;
    }
}