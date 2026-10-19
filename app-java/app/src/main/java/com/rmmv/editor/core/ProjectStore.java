package com.rmmv.editor.core;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Plain {@code java.io} project storage. No Capacitor, no WebView, no plugin
 * bridge -- just the app-private files directory.
 *
 * <pre>
 *   /data/data/com.rmmv.editor/files/Projects/&lt;Project&gt;/
 *       data/*.json   js/*.js   img/   audio/   fonts/   icon/   package.json
 * </pre>
 *
 * App-private storage is exempt from Scoped Storage on every API level, so the
 * MVP needs no runtime permission at all.
 */
public class ProjectStore {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    /** Files copied verbatim from the editor's own NewData scaffold (assets/template). */
    private static final String[] TEMPLATE = {
            "index.html", "package.json",
            "js/main.js", "js/plugins.js", "js/rpg_core.js", "js/rpg_managers.js",
            "js/rpg_objects.js", "js/rpg_scenes.js", "js/rpg_sprites.js", "js/rpg_windows.js",
            "js/libs/pixi.js", "js/libs/pixi-tilemap.js", "js/libs/pixi-picture.js",
            "js/libs/fpsmeter.js", "js/libs/lz-string.js", "js/libs/iphone-inline-video.browser.js",
            "data/System.json", "data/Actors.json", "data/Classes.json", "data/Skills.json",
            "data/Items.json", "data/Weapons.json", "data/Armors.json", "data/Enemies.json",
            "data/Troops.json", "data/States.json", "data/Animations.json", "data/Tilesets.json",
            "data/CommonEvents.json",
            "fonts/gamefont.css", "fonts/mplus-1m-regular.ttf",
            "icon/icon.png",
            "img/tilesets/World_A1.png", "img/tilesets/World_A2.png",
            "img/tilesets/World_B.png", "img/tilesets/World_C.png"
    };

    private final Context ctx;
    private final List<String> log = new ArrayList<>();

    public ProjectStore(Context context) {
        this.ctx = context.getApplicationContext();
    }

    public File projectsRoot() {
        File root = new File(ctx.getFilesDir(), "Projects");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    public File projectDir(String name) {
        return new File(projectsRoot(), sanitize(name));
    }

    public static String sanitize(String name) {
        if (name == null) return "Untitled";
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ");
        if (s.isEmpty()) s = "Untitled";
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    public List<String> log() { return log; }

    private void log(String msg) {
        log.add(msg);
        if (log.size() > 300) log.remove(0);
    }

    // ------------------------------------------------------------------ read

    public String readText(File f) throws IOException {
        if (!f.exists()) throw new IOException("no such file: " + f.getName());
        byte[] b = readAll(f);
        return new String(b, UTF8);
    }

    public byte[] readAll(File f) throws IOException {
        InputStream in = new java.io.FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(1024, f.length()));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            closeQuietly(in);
        }
    }

    // ----------------------------------------------------------------- write

    public void writeText(File f, String text) throws IOException {
        write(f, text.getBytes(UTF8));
    }

    public void write(File f, byte[] data) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
            out.flush();
        } finally {
            closeQuietly(out);
        }
    }

    // -------------------------------------------------------------- projects

    public static class ProjectInfo {
        public String name;
        public String title;
        public int mapCount;
        public File dir;
    }

    public List<ProjectInfo> listProjects() {
        List<ProjectInfo> out = new ArrayList<>();
        File[] kids = projectsRoot().listFiles();
        if (kids == null) return out;
        for (File d : kids) {
            if (!d.isDirectory()) continue;
            File mi = new File(d, "data/MapInfos.json");
            if (!mi.exists()) continue;
            ProjectInfo p = new ProjectInfo();
            p.name = d.getName();
            p.dir = d;
            try {
                p.mapCount = MapInfos.parse(readText(mi)).mapCount();
                File sys = new File(d, "data/System.json");
                if (sys.exists()) {
                    p.title = new org.json.JSONObject(readText(sys)).optString("gameTitle", "");
                }
            } catch (Exception ignored) { }
            out.add(p);
        }
        // newest first
        out.sort(new Comparator<ProjectInfo>() {
            @Override public int compare(ProjectInfo a, ProjectInfo b) {
                return Long.compare(b.dir.lastModified(), a.dir.lastModified());
            }
        });
        return out;
    }

    public boolean exists(String name) {
        return new File(projectDir(name), "data/MapInfos.json").exists();
    }

    /** Create a new MV project from the bundled scaffold. */
    public ProjectInfo createProject(String name, String gameTitle, int w, int h) throws IOException {
        String projName = sanitize(name);
        File dir = projectDir(projName);
        if (new File(dir, "data/MapInfos.json").exists()) {
            throw new IOException("A project named \"" + projName + "\" already exists.");
        }
        AssetManager am = ctx.getAssets();
        for (String rel : TEMPLATE) {
            try {
                InputStream in = am.open("template/" + rel);
                if (in == null) continue;
                File dst = new File(dir, rel);
                File parent = dst.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                OutputStream out = new FileOutputStream(dst);
                try {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    closeQuietly(in);
                    closeQuietly(out);
                }
            } catch (IOException e) {
                log("template miss: " + rel);
            }
        }

        // First map
        MvMap map = MvMap.createDefault(w, h, 1);
        writeText(new File(dir, "data/" + MapInfos.fileName(1)), map.toJson());

        MapInfos infos = new MapInfos(new org.json.JSONArray());
        infos.add("MAP001", 0);
        writeText(new File(dir, "data/MapInfos.json"), infos.toJson());

        // Stamp the game title
        File sys = new File(dir, "data/System.json");
        if (sys.exists()) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(readText(sys));
                o.put("gameTitle", gameTitle != null && !gameTitle.isEmpty() ? gameTitle : projName);
                o.put("advanced", false);
                writeText(sys, o.toString());
            } catch (Exception ignored) { }
        }

        ProjectInfo pi = new ProjectInfo();
        pi.name = projName;
        pi.dir = dir;
        pi.title = gameTitle;
        pi.mapCount = 1;
        log("created project " + projName);
        return pi;
    }

    public boolean deleteProject(String name) {
        return deleteRec(projectDir(name));
    }

    public boolean renameProject(String from, String to) {
        File src = projectDir(from), dst = projectDir(to);
        if (!src.exists() || dst.exists()) return false;
        return src.renameTo(dst);
    }

    // ------------------------------------------------------------------ util

    public static void copyDir(File src, File dst) throws IOException {
        File[] kids = src.listFiles();
        if (kids == null) return;
        if (!dst.exists()) dst.mkdirs();
        for (File k : kids) {
            File target = new File(dst, k.getName());
            if (k.isDirectory()) copyDir(k, target);
            else copy(k, target);
        }
    }

    public static void copy(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        InputStream in = new java.io.FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static boolean deleteRec(File f) {
        if (f == null || !f.exists()) return false;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        return f.delete();
    }

    public static List<File> listFilesRecursive(File dir, String extension) {
        List<File> out = new ArrayList<>();
        collect(dir, extension, out);
        return out;
    }

    private static void collect(File dir, String ext, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids, new Comparator<File>() {
            @Override public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
        });
        for (File k : kids) {
            if (k.isDirectory()) collect(k, ext, out);
            else if (ext == null || k.getName().toLowerCase().endsWith(ext)) out.add(k);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) try { c.close(); } catch (IOException ignored) { }
    }
}
