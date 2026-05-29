package eeg.stimulus;

import javafx.scene.paint.Color;

/**
 * Визуальный стимул — текст с цветом.
 * Используется, например, для задачи Струпа (слово + цвет текста).
 */
public class VisualStimulus extends Stimulus {

    private final String text;
    private final Color textColor;
    private final Color backgroundColor;

    public VisualStimulus(String name, int code, String text, Color textColor, Color backgroundColor) {
        super(name, code);
        this.text = text;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
    }

    /** Простой текстовый стимул с белым фоном. */
    public VisualStimulus(String text, int code) {
        this("Stimulus_" + code, code, text, Color.WHITE, Color.BLACK);
    }

    public String getText() { return text; }
    public Color getTextColor() { return textColor; }
    public Color getBackgroundColor() { return backgroundColor; }
}
