package eeg.experiment;

import eeg.stimulus.StroopStimulus;
import javafx.scene.paint.Color;
import java.util.*;

public class StroopStimulusGenerator {

    private static final String[] COLOR_NAMES = {"КРАСНЫЙ", "СИНИЙ", "ЗЕЛЁНЫЙ", "ЖЁЛТЫЙ"};
    public static final Color[] COLORS = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW};

    private final Random random = new Random();

    /**
     * Генерирует список стимулов для указанной части.
     * @param part часть теста
     * @param count количество стимулов в этой части
     * @param startCode начальный код маркера (увеличивается для каждого стимула)
     * @return список стимулов и новый код после последнего сгенерированного
     */
    public List<StroopStimulus> generate(StroopPart part, int count, int startCode) {
        List<StroopStimulus> list = new ArrayList<>();
        int code = startCode;
        for (int i = 0; i < count; i++) {
            // Выбираем случайный цвет
            int idx = random.nextInt(COLOR_NAMES.length);
            String correctName = COLOR_NAMES[idx];
            Color correctColor = COLORS[idx];

            StroopStimulus stim;
            if (part == StroopPart.T2) { // прямоугольник
                stim = new StroopStimulus(
                        "Rect_" + correctName, code++,
                        null, correctColor, Color.BLACK,
                        part, correctColor, correctName
                );
            } else {
                // Для T1 и T3 – текстовый стимул
                // Для T1 цвет текста всегда чёрный, для T3 – несовпадающий
                Color textColor;
                if (part == StroopPart.T1) {
                    textColor = Color.BLACK;
                } else { // T3 – конфликт
                    // выбираем цвет, отличный от правильного (и не чёрный)
                    List<Color> otherColors = new ArrayList<>(List.of(COLORS));
                    otherColors.remove(correctColor);
                    textColor = otherColors.get(random.nextInt(otherColors.size()));
                }
                String word = correctName; // слово соответствует правильному ответу? Нет, в T3 слово может быть любым.
                // По классической методике: в T3 слово может быть конгруэнтным или нет.
                // Сделаем 50% конгруэнтных (слово совпадает с цветом чернил), 50% неконгруэнтных.
                if (part == StroopPart.T3 && random.nextBoolean()) {
                    // конгруэнтный – слово = цвет чернил
                    word = correctName;
                } else if (part == StroopPart.T3) {
                    // неконгруэнтный – выбираем другое слово, не совпадающее с цветом чернил
                    List<String> otherWords = new ArrayList<>(List.of(COLOR_NAMES));
                    otherWords.remove(correctName);
                    word = otherWords.get(random.nextInt(otherWords.size()));
                }
                stim = new StroopStimulus(
                        "Stim_" + correctName, code++,
                        word, textColor, Color.BLACK,
                        part, correctColor, correctName
                );
            }
            list.add(stim);
        }
        return list;
    }
}