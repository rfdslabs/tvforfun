package com.tvforfun.games;

/** Uma pergunta de quiz: enunciado, opções e qual é a correta. */
public class Question {
    public final String prompt;        // texto grande no centro
    public final String[] options;     // 4 opções (texto)
    public final int[] optionColors;   // se != null, opções viram quadrados coloridos
    public final int correctIndex;     // índice da opção certa
    public final String speak;         // texto para ler em voz alta (pode ser null)
    public final String revealPrefix;  // texto mostrado no acerto (pode ser null)

    public Question(String prompt, String[] options, int correctIndex, String speak) {
        this(prompt, options, null, correctIndex, speak, null);
    }

    public Question(String prompt, String[] options, int[] optionColors,
                    int correctIndex, String speak, String revealPrefix) {
        this.prompt = prompt;
        this.options = options;
        this.optionColors = optionColors;
        this.correctIndex = correctIndex;
        this.speak = speak;
        this.revealPrefix = revealPrefix;
    }
}
