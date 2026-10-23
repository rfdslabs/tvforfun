package com.tvforfun.games;

import java.util.HashSet;
import java.util.Set;

/** Reconhecer cores: ache a cor pedida entre 4 quadrados. */
public class ColorsActivity extends QuizActivity {

    private static final String[] NAMES = {
        "RED", "BLUE", "GREEN", "YELLOW", "ORANGE", "PURPLE", "PINK", "BROWN", "GRAY", "CYAN"
    };
    private static final int[] COLORS = {
        0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFFFB8C00,
        0xFF8E24AA, 0xFFEC407A, 0xFF8D6E63, 0xFF9E9E9E, 0xFF00ACC1
    };

    @Override protected String title() { return "Colors"; }
    @Override protected int rounds() { return 8; }

    @Override
    protected Question nextQuestion() {
        int target = rnd().nextInt(NAMES.length);
        int correctIndex = rnd().nextInt(4);
        int[] optColors = new int[4];
        String[] labels = new String[4]; // não usados (opções são cores), mas o modelo pede
        Set<Integer> used = new HashSet<>();
        used.add(target);
        for (int i = 0; i < 4; i++) {
            if (i == correctIndex) { optColors[i] = COLORS[target]; labels[i] = NAMES[target]; continue; }
            int c;
            do { c = rnd().nextInt(NAMES.length); } while (used.contains(c));
            used.add(c);
            optColors[i] = COLORS[c];
            labels[i] = NAMES[c];
        }
        String prompt = "Find " + NAMES[target];
        String speak = "Find the color " + NAMES[target];
        return new Question(prompt, labels, optColors, correctIndex, speak, null);
    }
}
