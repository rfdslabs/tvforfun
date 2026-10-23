package com.tvforfun.games;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Base de todos os minijogos: mostra uma pergunta e 4 opções navegáveis pelo
 * controle. Subclasses só precisam dizer o título e gerar cada pergunta.
 */
public abstract class QuizActivity extends Activity {

    /** Título mostrado no topo. */
    protected abstract String title();

    /** Gera a próxima pergunta. */
    protected abstract Question nextQuestion();

    /** Quantas perguntas por rodada. */
    protected int rounds() { return 10; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator tone;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private int index;          // pergunta atual (0..rounds-1)
    private int score;          // acertos na rodada
    private Question current;
    private boolean locked;     // trava cliques durante o feedback

    private TextView starsView;
    private TextView promptView;
    private TextView feedbackView;
    private TextView progressView;
    private final FrameLayout[] cards = new FrameLayout[4];
    private final TextView[] cardLabels = new TextView[4];
    private final View[] cardSwatches = new View[4];

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80); } catch (Exception ignored) {}
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    tts.setLanguage(Locale.US);
                    ttsReady = true;
                    if (current != null && current.speak != null) speak(current.speak);
                }
            });
        } catch (Exception ignored) {}

        buildUi();
        index = 0;
        score = 0;
        showQuestion();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(dp(56), dp(36), dp(56), dp(36));

        // Topo: título + progresso + estrelas
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = text(title(), 30, Theme.TEXT, true);
        LinearLayout.LayoutParams lpTitle =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleView.setLayoutParams(lpTitle);

        progressView = text("", 24, Theme.MUTED, false);
        progressView.setPadding(0, 0, dp(28), 0);
        starsView = text("", 28, Theme.STAR, true);

        top.addView(titleView);
        top.addView(progressView);
        top.addView(starsView);
        root.addView(top);

        // Pergunta
        promptView = text("", 76, Theme.TEXT, true);
        promptView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpPrompt =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f);
        lpPrompt.gravity = Gravity.CENTER;
        promptView.setLayoutParams(lpPrompt);
        root.addView(promptView);

        // Grade 2x2 de opções
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpGrid =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.8f);
        grid.setLayoutParams(lpGrid);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpRow =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            row.setLayoutParams(lpRow);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                row.addView(makeCard(i));
            }
            grid.addView(row);
        }
        root.addView(grid);

        feedbackView = text("", 30, Theme.MUTED, true);
        feedbackView.setGravity(Gravity.CENTER);
        feedbackView.setPadding(0, dp(12), 0, 0);
        root.addView(feedbackView);

        setContentView(root);
    }

    private View makeCard(final int i) {
        FrameLayout card = new FrameLayout(this);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(14), dp(14), dp(14), dp(14));
        card.setLayoutParams(lp);
        card.setBackground(cardBg(Theme.CARD, false));
        card.setFocusable(true);
        card.setClickable(true);

        // rótulo (texto)
        TextView label = text("", 52, Theme.TEXT, true);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpLabel = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        label.setLayoutParams(lpLabel);
        card.addView(label);

        // quadrado colorido (para o jogo de cores)
        View swatch = new View(this);
        int s = dp(120);
        FrameLayout.LayoutParams lpSw = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
        swatch.setLayoutParams(lpSw);
        swatch.setVisibility(View.GONE);
        card.addView(swatch);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(cardBg(hasFocus ? Theme.CARD_HI : Theme.CARD, hasFocus));
            v.animate().scaleX(hasFocus ? 1.06f : 1f).scaleY(hasFocus ? 1.06f : 1f)
                    .setDuration(120).start();
        });
        card.setOnClickListener(v -> onAnswer(i));

        cards[i] = card;
        cardLabels[i] = label;
        cardSwatches[i] = swatch;
        return card;
    }

    private GradientDrawable cardBg(int fill, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(24));
        if (focused) g.setStroke(dp(4), Theme.ACCENT);
        return g;
    }

    private void showQuestion() {
        locked = false;
        current = nextQuestion();
        progressView.setText("Question " + (index + 1) + " / " + rounds());
        starsView.setText("★ " + Theme.getStars(this));
        feedbackView.setText("");
        promptView.setText(current.prompt);
        promptView.setTextColor(Theme.TEXT);

        for (int i = 0; i < 4; i++) {
            cards[i].setBackground(cardBg(Theme.CARD, false));
            cards[i].setAlpha(1f);
            if (current.optionColors != null) {
                cardLabels[i].setVisibility(View.GONE);
                cardSwatches[i].setVisibility(View.VISIBLE);
                GradientDrawable sw = new GradientDrawable();
                sw.setColor(current.optionColors[i]);
                sw.setCornerRadius(dp(16));
                cardSwatches[i].setBackground(sw);
            } else {
                cardSwatches[i].setVisibility(View.GONE);
                cardLabels[i].setVisibility(View.VISIBLE);
                cardLabels[i].setText(current.options[i]);
            }
        }
        cards[0].post(() -> cards[0].requestFocus());
        if (current.speak != null) speak(current.speak);
    }

    private void onAnswer(int i) {
        if (locked) return;
        locked = true;
        boolean correct = (i == current.correctIndex);
        cards[i].setBackground(cardBg(correct ? Theme.GOOD : Theme.BAD, true));
        if (!correct) {
            cards[current.correctIndex].setBackground(cardBg(Theme.GOOD, true));
        } else {
            Theme.addStars(this, 1);
            score++;
            starsView.setText("★ " + Theme.getStars(this));
        }
        beep(correct);
        String fb;
        if (correct) {
            fb = pick(new String[]{"Great job!", "Correct!", "Awesome!", "You got it!"});
        } else {
            fb = "Oops! " + (current.revealPrefix != null ? current.revealPrefix : "");
        }
        feedbackView.setText(fb.trim());
        feedbackView.setTextColor(correct ? Theme.GOOD : Theme.BAD);
        if (correct) speak("Great job"); else if (current.revealPrefix != null) speak(current.revealPrefix);

        handler.postDelayed(() -> {
            index++;
            if (index >= rounds()) showResults(); else showQuestion();
        }, correct ? 1100 : 1700);
    }

    private void showResults() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(56), dp(56), dp(56), dp(56));

        root.addView(text("You got", 40, Theme.MUTED, false, Gravity.CENTER));
        root.addView(text(score + " / " + rounds(), 110, Theme.TEXT, true, Gravity.CENTER));
        String stars = score >= 9 ? "★★★" : score >= 6 ? "★★" : "★";
        root.addView(text(stars, 70, Theme.STAR, true, Gravity.CENTER));
        TextView tot = text("Total stars: " + Theme.getStars(this), 30, Theme.MUTED, false, Gravity.CENTER);
        tot.setPadding(0, dp(10), 0, dp(40));
        root.addView(tot);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);
        btns.addView(resultButton("Play again", () -> { index = 0; score = 0; buildUi(); showQuestion(); }));
        btns.addView(resultButton("Menu", this::finish));
        root.addView(btns);
        setContentView(root);

        speak(score >= 6 ? "Well done Maya" : "Good try Maya");
    }

    private View resultButton(String label, final Runnable action) {
        TextView b = text(label, 34, Theme.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(48), dp(24), dp(48), dp(24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), 0, dp(16), 0);
        b.setLayoutParams(lp);
        b.setBackground(cardBg(Theme.CARD, false));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnFocusChangeListener((v, f) -> v.setBackground(cardBg(f ? Theme.CARD_HI : Theme.CARD, f)));
        b.setOnClickListener(v -> action.run());
        b.post(() -> { if ("Play again".equals(label)) b.requestFocus(); });
        return b;
    }

    // ---- helpers de texto / som / voz ----

    private TextView text(String s, int sp, int color, boolean bold) {
        return text(s, sp, color, bold, Gravity.START);
    }

    private TextView text(String s, int sp, int color, boolean bold, int gravity) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(gravity);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif-light",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private final java.util.Random rnd = new java.util.Random();
    protected java.util.Random rnd() { return rnd; }
    private String pick(String[] a) { return a[rnd.nextInt(a.length)]; }

    private void beep(boolean correct) {
        if (tone == null) return;
        try {
            tone.startTone(correct ? ToneGenerator.TONE_PROP_BEEP2
                    : ToneGenerator.TONE_SUP_ERROR, correct ? 150 : 300);
        } catch (Exception ignored) {}
    }

    private void speak(String s) {
        if (tts == null || !ttsReady || s == null) return;
        try { tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "q"); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tone != null) try { tone.release(); } catch (Exception ignored) {}
        if (tts != null) try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
