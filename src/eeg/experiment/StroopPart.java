package eeg.experiment;

/**
 * Три части теста Струпа.
 */
public enum StroopPart {
    T1("Названия цветов чёрным цветом",
        "Выбери цвет, который написан. Чем быстрее тем лучше",
        true,   // ответ по смыслу слова
        false), // стимул – текст
    T2("Цветные прямоугольники",
        "Перед тобой цветные прямоугольники. Выбери название цвета фона прямоугольника.",
        true,   // ответ по цвету фона
        true),  // стимул – прямоугольник
    T3("Конфликтные слова",
        "Выбери только цвет слова, которым оно напечатано. Игнорируй смысл.",
        false,  // ответ по цвету чернил (а не по смыслу)
        false); // стимул – текст

    public final String title;
    public final String instruction;
    public final boolean respondToMeaning; // T1 – да, T2 – да (цвет прямоугольника), T3 – нет (цвет чернил)
    public final boolean isRectangle;      // T2 – прямоугольник, остальные – текст

    StroopPart(String title, String instruction, boolean respondToMeaning, boolean isRectangle) {
        this.title = title;
        this.instruction = instruction;
        this.respondToMeaning = respondToMeaning;
        this.isRectangle = isRectangle;
    }
}