package com.rmmv.editor.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RPG Maker MV tileset: composition of the up-to-9 tileset sheets plus autotile
 * rendering, reproducing {@code Tilemap.prototype._drawAutotile}.
 *
 * <p>Geometry verified against the editor's own shipped assets:
 * <pre>
 *   World_A1.png = 768x576   World_A2.png = 768x576
 *   World_B.png  = 768x768   World_C.png  = 768x768
 * </pre>
 * Sheets are laid out as a 3x3 grid of 768x768 blocks (2304x2304) and drawn at
 * NATIVE size. Rescaling them to 512 shears the 48px tile grid and every
 * autotile sample lands on transparent pixels.
 *
 * <p>Autotile source region is the top-left 768x768 block: the maximum source
 * coordinate is (14*2+3)*24+24 = 768.
 *
 * <p><b>Block binding is probed, not assumed.</b> MV's {@code Tileset} class is
 * not present in the shipped {@code NewData/js/} (it is compiled into
 * RPGMV.exe's Qt resources and is not readable here), so which block holds each
 * of the A1..A4 groups cannot be read from source. We therefore scan the alpha
 * channel once at compose time and pick, per autotile kind, the first block
 * that actually has ink at the computed origin.
 *
 * <p>This affects PREVIEW ONLY. The tileId written to disk comes from
 * {@link TileId}'s verified algebra and is unaffected; the desktop editor
 * renders projects with its own correct mapping.
 */
public class Tileset {

    public static final int TILE = 48;
    public static final int QUAD = 24;
    public static final int BLOCK = 768;
    public static final int COLS = 3;
    public static final int CANVAS = BLOCK * COLS;   // 2304

    private final int tilesetId;
    private final String name;
    private final String[] tilesetNames = new String[9];
    private Bitmap sheet;               // composed 2304x2304
    private final List<Integer> blocks = new ArrayList<>();
    private boolean[] ink;              // per 24px quadrant occupancy
    private static final int QUAD_COLS = CANVAS / QUAD;  // 96

    /** Rendered 48x48 autotile cache. */
    private final Map<Integer, Bitmap> tileCache = new LinkedHashMap<>();
    private static final int TILE_CACHE_MAX = 512;

    private static final Paint SRC_PAINT = new Paint(
            Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private static final Rect SRC = new Rect();
    private static final RectF DST = new RectF();

    public Tileset(int tilesetId, JSONObject def) {
        this.tilesetId = tilesetId;
        this.name = (def != null) ? def.optString("name", "Tileset" + tilesetId) : ("Tileset" + tilesetId);
        if (def != null) {
            org.json.JSONArray n = def.optJSONArray("tilesetNames");
            if (n != null) {
                for (int i = 0; i < 9 && i < n.length(); i++) {
                    String s = n.optString(i, "");
                    if (s != null && !s.isEmpty()) tilesetNames[i] = s;
                }
            }
        }
    }

    public int id() { return tilesetId; }
    public String name() { return name; }
    public String[] sheetNames() { return tilesetNames.clone(); }

    /** Compose the 2304x2304 sheet from the project's img/tilesets/*.png. */
    public void compose(File projectDir) {
        if (sheet != null && !sheet.isRecycled()) sheet.recycle();
        sheet = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sheet);
        blocks.clear();

        File tsDir = new File(projectDir, "img/tilesets");
        for (int i = 0; i < 9; i++) {
            if (tilesetNames[i] == null) continue;
            File f = new File(tsDir, tilesetNames[i] + ".png");
            if (!f.exists()) continue;
            Bitmap b;
            try {
                b = BitmapFactory.decodeFile(f.getAbsolutePath());
            } catch (Throwable t) {
                b = null;
            }
            if (b == null) continue;
            int bx = (i % COLS) * BLOCK;
            int by = (i / COLS) * BLOCK;
            c.drawBitmap(b, new Rect(0, 0, b.getWidth(), b.getHeight()),
                    new Rect(bx, by, bx + b.getWidth(), by + b.getHeight()), SRC_PAINT);
            b.recycle();
            blocks.add(i);
        }
        buildInkMask();
        tileCache.clear();
    }

    /** One boolean per 24px quadrant: is there anything painted there? */
    private void buildInkMask() {
        ink = new boolean[QUAD_COLS * QUAD_COLS];
        if (sheet == null) return;
        int step = QUAD;
        for (int qy = 0; qy < QUAD_COLS; qy++) {
            for (int qx = 0; qx < QUAD_COLS; qx++) {
                // sample a small grid inside the quadrant rather than all pixels
                boolean any = false;
                for (int sy = 0; sy < step && !any; sy += 6) {
                    for (int sx = 0; sx < step && !any; sx += 6) {
                        try {
                            if (sheet.getPixel(qx * step + sx, qy * step + sy) >>> 24 != 0) any = true;
                        } catch (Throwable ignored) { }
                    }
                }
                ink[qy * QUAD_COLS + qx] = any;
            }
        }
    }

    private boolean hasInk(int qx, int qy) {
        if (ink == null) return true;
        if (qx < 0 || qy < 0 || qx >= QUAD_COLS || qy >= QUAD_COLS) return false;
        return ink[qy * QUAD_COLS + qx];
    }

    /** Which 768x768 block holds this autotile kind's art. */
    private int pickBlock(int bx, int by) {
        for (int b : blocks) {
            int ox = (b % COLS) * BLOCK + bx * 2 * QUAD;
            int oy = (b / COLS) * BLOCK + by * 2 * QUAD;
            if (hasInk(ox / QUAD, oy / QUAD)) return b;
        }
        return blocks.isEmpty() ? 0 : blocks.get(0);
    }

    /** bx/by source offset for an autotile kind. Ported from rpg_core.js:5040-5095. */
    public static int[] autotileOrigin(int kind) {
        int tx = kind % 8;
        int ty = kind / 8;
        if (kind < 16) {                       // A1: animated water
            switch (kind) {
                case 0: return new int[]{0, 0};
                case 1: return new int[]{0, 3};
                case 2: return new int[]{6, 0};
                case 3: return new int[]{6, 3};
                default: return new int[]{(tx / 4) * 8, ty * 6 + (tx / 2) % 2 * 3};
            }
        }
        if (kind < 48) return new int[]{tx * 2, (ty - 2) * 3};   // A2: ground
        if (kind < 80) return new int[]{tx * 2, (ty - 6) * 2};   // A3
        return new int[]{tx * 2, (int) Math.floor((ty - 10) * 2.5 + (ty % 2 == 1 ? 0.5 : 0))};
    }

    private static int[][] tableFor(int kind) {
        if (kind >= 48 && kind < 80) return WALL;
        if (kind >= 80 && (kind % 8) >= 2) return WALL;
        return FLOOR;
    }

    /** Render (and cache) one autotile as a 48x48 bitmap. */
    public Bitmap renderTile(int tileId) {
        if (!TileId.isAutotile(tileId)) return null;
        Bitmap cached = tileCache.get(tileId);
        if (cached != null && !cached.isRecycled()) return cached;

        int kind = TileId.autotileKind(tileId);
        int shape = TileId.autotileShape(tileId);
        if (kind < 0 || kind >= TileId.KINDS) return null;
        if (sheet == null || sheet.isRecycled()) return null;

        int[] org = autotileOrigin(kind);
        int[][] table = tableFor(kind);
        int[] row = table[shape % table.length];
        int block = pickBlock(org[0], org[1]);
        int sx0 = (block % COLS) * BLOCK;
        int sy0 = (block / COLS) * BLOCK;

        Bitmap out = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        for (int i = 0; i < 4; i++) {
            int qx = row[i * 2], qy = row[i * 2 + 1];
            SRC.set(sx0 + (org[0] * 2 + qx) * QUAD, sy0 + (org[1] * 2 + qy) * QUAD,
                    sx0 + (org[0] * 2 + qx + 1) * QUAD, sy0 + (org[1] * 2 + qy + 1) * QUAD);
            int dx = (i % 2) * QUAD;
            int dy = (i / 2) * QUAD;
            DST.set(dx, dy, dx + QUAD, dy + QUAD);
            try {
                oc.drawBitmap(sheet, SRC, DST, SRC_PAINT);
            } catch (Throwable ignored) { }
        }
        if (tileCache.size() > TILE_CACHE_MAX) evictOldest(tileCache);
        tileCache.put(tileId, out);
        return out;
    }

    private static void evictOldest(Map<Integer, Bitmap> cache) {
        Integer first = null;
        for (Integer k : cache.keySet()) { first = k; break; }
        if (first != null) {
            Bitmap b = cache.remove(first);
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }

    /** Draw a tileId into a destination rect. Returns false for empty/unknown. */
    public boolean drawTile(Canvas canvas, int tileId, float dx, float dy, float dw, float dh) {
        if (!TileId.isVisibleTile(tileId)) return false;
        if (!TileId.isAutotile(tileId)) return false;
        Bitmap b = renderTile(tileId);
        if (b == null || b.isRecycled()) return false;
        DST.set(dx, dy, dx + dw, dy + dh);
        canvas.drawBitmap(b, null, DST, SRC_PAINT);
        return true;
    }

    /** Direct source blit helper used by the composer. */
    private void blitInto(Canvas canvas, Bitmap b, int dx, int dy) {
        canvas.drawBitmap(b, new Rect(0, 0, b.getWidth(), b.getHeight()),
                new Rect(dx, dy, dx + b.getWidth(), dy + b.getHeight()), SRC_PAINT);
    }

    public void recycle() {
        if (sheet != null && !sheet.isRecycled()) sheet.recycle();
        sheet = null;
        for (Bitmap b : tileCache.values()) if (b != null && !b.isRecycled()) b.recycle();
        tileCache.clear();
    }

    // ---------------------------------------------------------------- tables

    /** Tilemap.FLOOR_AUTOTILE_TABLE -- verbatim from rpg_core.js:5389-5412. */
    static final int[][] FLOOR = {
            {2,4,1,4,2,3,1,3}, {2,0,1,4,2,3,1,3},
            {2,4,3,0,2,3,1,3}, {2,0,3,0,2,3,1,3},
            {2,4,1,4,2,3,3,1}, {2,0,1,4,2,3,3,1},
            {2,4,3,0,2,3,3,1}, {2,0,3,0,2,3,3,1},
            {2,4,1,4,2,1,1,3}, {2,0,1,4,2,1,1,3},
            {2,4,3,0,2,1,1,3}, {2,0,3,0,2,1,1,3},
            {2,4,1,4,2,1,3,1}, {2,0,1,4,2,1,3,1},
            {2,4,3,0,2,1,3,1}, {2,0,3,0,2,1,3,1},
            {0,4,1,4,0,3,1,3}, {0,4,3,0,0,3,1,3},
            {0,4,1,4,0,3,3,1}, {0,4,3,0,0,3,3,1},
            {2,2,1,2,2,3,1,3}, {2,2,1,2,2,3,3,1},
            {2,2,1,2,2,1,1,3}, {2,2,1,2,2,1,3,1},
            {2,4,3,4,2,3,3,3}, {2,4,3,4,2,1,3,3},
            {2,0,3,4,2,3,3,3}, {2,0,3,4,2,1,3,3},
            {2,4,1,4,2,5,1,5}, {2,0,1,4,2,5,1,5},
            {2,4,3,0,2,5,1,5}, {2,0,3,0,2,5,1,5},
            {0,4,3,4,0,3,3,3}, {2,2,1,2,2,5,1,5},
            {0,2,1,2,0,3,1,3}, {0,2,1,2,0,3,3,1},
            {2,2,3,2,2,3,3,3}, {2,2,3,2,2,1,3,3},
            {2,4,3,4,2,5,3,5}, {2,0,3,4,2,5,3,5},
            {0,4,1,4,0,5,1,5}, {0,4,3,0,0,5,1,5},
            {0,2,3,2,0,3,3,3}, {0,2,1,2,0,5,1,5},
            {0,4,3,4,0,5,3,5}, {2,2,3,2,2,5,3,5},
            {0,2,3,2,0,5,3,5}, {0,0,1,0,0,1,1,1},
    };

    /** Tilemap.WALL_AUTOTILE_TABLE -- verbatim from rpg_core.js:5416-5423. */
    static final int[][] WALL = {
            {2,2,1,2,2,1,1,1}, {0,2,1,2,0,1,1,1},
            {2,0,1,0,2,1,1,1}, {0,0,1,0,0,1,1,1},
            {2,2,3,2,2,1,3,1}, {0,2,3,2,0,1,3,1},
            {2,0,3,0,2,1,3,1}, {0,0,3,0,0,1,3,1},
            {2,2,1,2,2,3,1,3}, {0,2,1,2,0,3,1,3},
            {2,0,1,0,2,3,1,3}, {0,0,1,0,0,3,3,3},
            {2,2,3,2,2,3,3,3}, {0,2,3,2,0,3,3,3},
            {2,0,3,0,2,3,3,3}, {0,0,3,0,0,3,3,3},
    };
}
