package com.tvforfun.games;

import java.util.HashSet;
import java.util.Set;

/** Somar, subtrair e tabuada (nível 7-8 anos). */
public class MathActivity extends QuizActivity {

    @Override protected String title() { return "Math"; }

    @Override
    protected Question nextQuestion() {
        int op = rnd().nextInt(3); // 0=+, 1=-, 2=x
        int a, b, answer;
        String sign, word;
        if (op == 0) {
            a = 2 + rnd().nextInt(19);        // 2..20
            b = 2 + rnd().nextInt(19);
            answer = a + b; sign = "+"; word = "plus";
        } else if (op == 1) {
            a = 6 + rnd().nextInt(15);        // 6..20
            b = 1 + rnd().nextInt(a - 1);     // 1..a-1
            answer = a - b; sign = "−"; word = "minus";
        } else {
            a = 2 + rnd().nextInt(8);         // 2..9
            b = 2 + rnd().nextInt(8);         // 2..9
            answer = a * b; sign = "×"; word = "times";
        }

        String prompt = a + " " + sign + " " + b + " = ?";
        String speak = "What is " + a + " " + word + " " + b + "?";

        // 4 opções: resposta + 3 distratores próximos, únicos e >= 0
        String[] options = new String[4];
        int correctIndex = rnd().nextInt(4);
        Set<Integer> used = new HashSet<>();
        used.add(answer);
        for (int i = 0; i < 4; i++) {
            if (i == correctIndex) { options[i] = String.valueOf(answer); continue; }
            int d;
            do {
                int delta = 1 + rnd().nextInt(5);
                d = answer + (rnd().nextBoolean() ? delta : -delta);
            } while (d < 0 || used.contains(d));
            used.add(d);
            options[i] = String.valueOf(d);
        }
        return new Question(prompt, options, correctIndex, speak);
    }
}
