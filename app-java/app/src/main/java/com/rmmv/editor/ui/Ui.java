package com.rmmv.editor.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Small view/formatting helpers shared by the Database, Plugin Manager and
 * Resource Manager screens.
 *
 * <p>This class deliberately touches <b>framework classes only</b> (android.view,
 * android.widget, android.graphics) and takes its colours as plain ints via
 * {@link Palette}, so it can be compiled outside Gradle against android.jar.
 * Colours are resolved from {@code R.color.*} by the calling Activity.
 */
public final class Ui {

    private Ui() { }

    // ------------------------------------------------------------ dimensions

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics()));
    }

    // --------------------------------------------------------------- palette

    /** Flat colour bundle; avoids leaking {@code R} into the framework-only helpers. */
    public static final class Palette {
        public final int bg;
        public final int bg2;
        public final int bg3;
        public final int line;
        public final int fg;
        public final int fgDim;
        public final int accent;
        public final int danger;

        public Palette(int bg, int bg2, int bg3, int line,
                       int fg, int fgDim, int accent, int danger) {
            this.bg = bg;
            this.bg2 = bg2;
            this.bg3 = bg3;
            this.line = line;
            this.fg = fg;
            this.fgDim = fgDim;
            this.accent = accent;
            this.danger = danger;
        }
    }

    // ------------------------------------------------------------ backgrounds

    /** Rounded, stroked background used for text fields and pills. */
    public static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(Math.max(1, dp(c, 1)), stroke);
        return d;
    }

    // ------------------------------------------------------------------ text

    public static TextView text(Context c, String s, int color, float sizeSp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static LinearLayout row(Context c, int gravity) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(gravity);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ------------------------------------------------------------- formatting

    /** 1.2 KB / 340 B style size label. */
    public static String fileSize(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------ misc

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    /** Ensure the view has MarginLayoutParams and apply the given margins (dp). */
    public static void margins(View v, float leftDp, float topDp, float rightDp, float bottomDp, Context c) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            v.setLayoutParams(lp);
        }
        lp.leftMargin = dp(c, leftDp);
        lp.topMargin = dp(c, topDp);
        lp.rightMargin = dp(c, rightDp);
        lp.bottomMargin = dp(c, bottomDp);
    }
}