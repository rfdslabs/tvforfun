package com.tvforfun.games;

import android.content.Context;
import android.content.SharedPreferences;

/** Cores e util de pontuação (estrelas salvas no aparelho). */
public final class Theme {
    public static final int BG      = 0xFF0A0A0F;
    public static final int CARD    = 0xFF15181E;
    public static final int CARD_HI = 0xFF232A36; // card em foco
    public static final int ACCENT  = 0xFF4FC3F7; // azul "vidro" do foco
    public static final int TEXT    = 0xFFFFFFFF;
    public static final int MUTED   = 0xFFB3B3B3;
    public static final int GOOD    = 0xFF34C759; // acerto
    public static final int BAD     = 0xFFFF3B30; // erro
    public static final int STAR    = 0xFFFFD60A;

    private static final String PREFS = "maya_games";
    private static final String KEY_STARS = "stars";

    private Theme() {}

    public static int getStars(Context c) {
        return prefs(c).getInt(KEY_STARS, 0);
    }

    public static void addStars(Context c, int n) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(KEY_STARS, p.getInt(KEY_STARS, 0) + n).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
