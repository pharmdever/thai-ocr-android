package com.thaio.ocr;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AppStyle {
    public static final int BACKGROUND = Color.rgb(244,247,248), INK = Color.rgb(22,45,51), MUTED = Color.rgb(104,124,129);
    public static final int TEAL = Color.rgb(16,113,111), LIGHT = Color.rgb(230,243,240), LINE = Color.rgb(224,233,233);
    private AppStyle() {}
    public static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable rounded(Context c, int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(c,radius));
        if (stroke != 0) drawable.setStroke(dp(c,1),stroke);
        return drawable;
    }
    public static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView view = new TextView(c); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern"); view.setLineSpacing(dp(c,2),1);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return view;
    }
    public static LinearLayout column(Context c) { LinearLayout box = new LinearLayout(c); box.setOrientation(LinearLayout.VERTICAL); return box; }
    public static LinearLayout row(Context c) { LinearLayout box = new LinearLayout(c); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(android.view.Gravity.CENTER_VERTICAL); return box; }
    public static LinearLayout card(Context c) {
        LinearLayout box = column(c); box.setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,20));
        box.setBackground(rounded(c,Color.WHITE,22,LINE)); return box;
    }
    public static void gap(LinearLayout parent, int height) { View gap = new View(parent.getContext()); parent.addView(gap,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height))); }
    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setMinHeight(dp(c,50));
        b.setMinimumHeight(dp(c,50)); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(rounded(c,primary ? TEAL : LIGHT,14,0));
        b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Color.rgb(232,238,238),primary?TEAL:LIGHT}));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Color.rgb(156,170,173),primary?Color.WHITE:TEAL}));
        b.setStateListAnimator(null); return b;
    }
}
