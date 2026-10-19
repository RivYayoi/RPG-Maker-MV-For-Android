package com.rmmv.editor.core;

/**
 * RPG Maker MV tileId algebra.
 *
 * <p>Ported 1:1 from the editor's own shipped engine source, which is the
 * authoritative statement of the on-disk format:
 *
 * <pre>
 *   NewData/js/rpg_core.js:5273-5282   TILE_ID_B..TILE_ID_MAX constants
 *   NewData/js/rpg_core.js:5284-5302   isVisibleTile / isAutotile / kind / shape
 *   NewData/js/rpg_core.js:5012-5030   normal-tile source rect
 * </pre>
 *
 * <p>Cross-check: the editor's shipped {@code NewData/data/Map001.json} uses
 * tile 2816, which is exactly {@link #TILE_ID_A2}.
 */
public final class TileId {

    private TileId() { }

    public static final int TILE_ID_B = 0;
    public static final int TILE_ID_C = 256;
    public static final int TILE_ID_D = 512;
    public static final int TILE_ID_E = 768;
    public static final int TILE_ID_A5 = 1536;
    public static final int TILE_ID_A1 = 2048;
    public static final int TILE_ID_A2 = 2816;
    public static final int TILE_ID_A3 = 4352;
    public static final int TILE_ID_A4 = 5888;
    public static final int TILE_ID_MAX = 8192;

    /** Number of autotile variants ("shapes") per kind. */
    public static final int SHAPES = 48;
    /** Number of autotile kinds: A1..A4. */
    public static final int KINDS = 128;

    public static boolean isVisibleTile(int tileId) {
        return tileId > 0 && tileId < TILE_ID_MAX;
    }

    public static boolean isAutotile(int tileId) {
        return tileId >= TILE_ID_A1;
    }

    public static boolean isTileA1(int t) { return t >= TILE_ID_A1 && t < TILE_ID_A2; }
    public static boolean isTileA2(int t) { return t >= TILE_ID_A2 && t < TILE_ID_A3; }
    public static boolean isTileA3(int t) { return t >= TILE_ID_A3 && t < TILE_ID_A4; }
    public static boolean isTileA4(int t) { return t >= TILE_ID_A4 && t < TILE_ID_MAX; }
    public static boolean isTileA5(int t) { return t >= TILE_ID_A5 && t < TILE_ID_A1; }

    public static int autotileKind(int tileId) {
        return Math.floorDiv(tileId - TILE_ID_A1, SHAPES);
    }

    public static int autotileShape(int tileId) {
        return Math.floorMod(tileId - TILE_ID_A1, SHAPES);
    }

    public static int makeAutotileId(int kind, int shape) {
        return TILE_ID_A1 + kind * SHAPES + (shape % SHAPES);
    }

    /** Which source "set" bitmap a normal (non-autotile) tile comes from. */
    public static int normalTileSetNumber(int tileId) {
        return isTileA5(tileId) ? 4 : 5 + Math.floorDiv(tileId, 256);
    }

    /**
     * Source cell of a normal tile inside its set bitmap, in 48px cells.
     * Verified against Tilemap.prototype._drawNormalTile:
     * <pre>
     *   sx = (floor(tileId/128) % 2 * 8 + tileId % 8) * 48
     *   sy = (floor(tileId % 256 / 8) % 16) * 48
     * </pre>
     *
     * @return {gx, gy}
     */
    public static int[] normalTileCell(int tileId) {
        int gx = (Math.floorDiv(tileId, 128) % 2) * 8 + Math.floorMod(tileId, 8);
        int gy = (int) (Math.floor((tileId % 256) / 8.0) % 16);
        return new int[]{gx, gy};
    }

    /** Group label used by the palette tabs, mirroring the A1..A4 ranges. */
    public static String groupName(int kind) {
        if (kind < 16) return "A1";
        if (kind < 48) return "A2";
        if (kind < 80) return "A3";
        return "A4";
    }
}
