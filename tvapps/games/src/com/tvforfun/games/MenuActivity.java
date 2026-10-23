package com.tvforfun.games;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tela inicial: escolhe um dos minijogos. Navegação pelo controle. */
public class MenuActivity extends Activity {

    private TextView starsView;
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (starsView != null) starsView.setText("★ " + Theme.getStars(this));
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(dp(72), dp(56), dp(72), dp(56));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView hi = text("Let's play!", 46, Theme.TEXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hi.setLayoutParams(lp);
        starsView = text("★ " + Theme.getStars(this), 34, Theme.STAR, true);
        top.addView(hi);
        top.addView(starsView);
        root.addView(top);

        TextView sub = text("Pick a game", 28, Theme.MUTED, false);
        sub.setPadding(0, dp(8), 0, dp(40));
        root.addView(sub);

        LinearLayout rowCards = new LinearLayout(this);
        rowCards.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rowCards.setLayoutParams(lpRow);

        rowCards.addView(card("123", "Math", MathActivity.class, 0xFF1E88E5, true));
        rowCards.addView(card("ABC", "Words", WordsActivity.class, 0xFF8E24AA, false));
        rowCards.addView(card("●", "Colors", ColorsActivity.class, 0xFF43A047, false));
        root.addView(rowCards);

        setContentView(root);
    }

    private View card(String big, String label, final Class<?> target, int tint, boolean focusFirst) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(18), 0, dp(18), 0);
        card.setLayoutParams(lp);
        card.setBackground(bg(Theme.CARD, false, tint));
        card.setFocusable(true);
        card.setClickable(true);

        TextView icon = text(big, 92, tint, true);
        icon.setGravity(Gravity.CENTER);
        TextView name = text(label, 40, Theme.TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(16), 0, 0);
        card.addView(icon);
        card.addView(name);

        card.setOnFocusChangeListener((v, f) -> {
            v.setBackground(bg(f ? Theme.CARD_HI : Theme.CARD, f, tint));
            v.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });
        card.setOnClickListener(v -> startActivity(new Intent(this, target)));
        if (focusFirst) card.post(card::requestFocus);
        return card;
    }

    private GradientDrawable bg(int fill, boolean focused, int tint) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(28));
        g.setStroke(dp(focused ? 5 : 2), focused ? Theme.ACCENT : 0xFF2A2F3A);
        return g;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif-light",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }
}
