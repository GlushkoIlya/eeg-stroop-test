package eeg.input;

import javafx.scene.paint.Color;

/**
 * Один вариант ответа – кнопка с текстом и цветом шрифта.
 */
public class AnswerOption {
    private final String displayText;      // "КРАСНЫЙ", "СИНИЙ" и т.д.
    private final Color textColor;         // цвет, которым написан этот текст на кнопке
    private final String semanticColor;    // смысл (какой цвет обозначает кнопка) – то же, что displayText

    public AnswerOption(String displayText, Color textColor) {
        this.displayText = displayText;
        this.textColor = textColor;
        this.semanticColor = displayText; // по условию: ответом является название цвета, которое написано
    }

    public String getDisplayText() { return displayText; }
    public Color getTextColor() { return textColor; }
    public String getSemanticColor() { return semanticColor; }

    public boolean matches(String expectedColorName) {
        return semanticColor.equalsIgnoreCase(expectedColorName);
    }
}