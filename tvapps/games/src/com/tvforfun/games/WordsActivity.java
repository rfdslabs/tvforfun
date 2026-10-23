package com.tvforfun.games;

import java.util.HashSet;
import java.util.Set;

/** Ler e completar palavras: falta uma letra, escolha a certa (7-8 anos). */
public class WordsActivity extends QuizActivity {

    private static final String[] WORDS = {
        "CAT", "DOG", "SUN", "BOOK", "TREE", "FISH", "STAR", "MOON", "BIRD", "CAKE",
        "MILK", "FROG", "BALL", "HAND", "RAIN", "LION", "DUCK", "SHIP", "LEAF", "BEAR",
        "APPLE", "HOUSE", "TIGER", "GREEN", "HORSE", "TRAIN", "CLOCK", "SMILE"
    };
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Override protected String title() { return "Words"; }

    @Override
    protected Question nextQuestion() {
        String word = WORDS[rnd().nextInt(WORDS.length)];
        int pos = rnd().nextInt(word.length());
        char missing = word.charAt(pos);

        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            shown.append(i == pos ? '_' : word.charAt(i));
            if (i < word.length() - 1) shown.append(' ');
        }

        // 4 opções de letra: a certa + 3 letras diferentes
        String[] options = new String[4];
        int correctIndex = rnd().nextInt(4);
        Set<Character> used = new HashSet<>();
        used.add(missing);
        for (int i = 0; i < 4; i++) {
            if (i == correctIndex) { options[i] = String.valueOf(missing); continue; }
            char c;
            do { c = LETTERS.charAt(rnd().nextInt(LETTERS.length())); } while (used.contains(c));
            used.add(c);
            options[i] = String.valueOf(c);
        }

        String speak = "Which letter completes the word? " + spellOut(word, pos);
        String reveal = "The word is " + word + ".";
        return new Question(shown.toString(), options, null, correctIndex, speak, reveal);
    }

    /** "C _ T" lido como "C, blank, T" ajuda a criança. */
    private String spellOut(String word, int pos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            sb.append(i == pos ? "blank" : String.valueOf(word.charAt(i)));
            sb.append(", ");
        }
        return sb.toString();
    }
}
