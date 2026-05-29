package eeg.stimulus;

import eeg.experiment.StroopPart;
import javafx.scene.paint.Color;

/**
 * Стимул для теста Струпа: содержит текст (или null для прямоугольника),
 * цвет текста (или цвет прямоугольника), правильный ответ (название цвета),
 * а также принадлежность к части теста.
 */
public class StroopStimulus extends VisualStimulus {

    private final StroopPart part;
    private final Color correctColor;   // цвет, который должен выбрать испытуемый (например, Color.RED)
    private final String correctColorName; // "КРАСНЫЙ", "СИНИЙ", ...

    /**
     * @param name          имя стимула
     * @param code          маркер для ЭЭГ
     * @param text          текст (null для прямоугольника)
     * @param displayColor  цвет отображения (цвет текста или цвет прямоугольника)
     * @param bgColor       фон
     * @param part          часть теста
     * @param correctColor  целевой цвет (правильный ответ)
     * @param correctColorName строковое представление цвета (заглавными буквами)
     */
    public StroopStimulus(String name, int code, String text, Color displayColor, Color bgColor,
                          StroopPart part, Color correctColor, String correctColorName) {
        super(name, code, text != null ? text : "", displayColor, bgColor);
        this.part = part;
        this.correctColor = correctColor;
        this.correctColorName = correctColorName;
    }

    public StroopPart getPart() { return part; }
    public Color getCorrectColor() { return correctColor; }
    public String getCorrectColorName() { return correctColorName; }
    public boolean isRectangle() { return part.isRectangle; }
}