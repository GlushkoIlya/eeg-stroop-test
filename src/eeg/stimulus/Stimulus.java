package eeg.stimulus;

/**
 * Абстрактный базовый класс для всех типов стимулов.
 */
public abstract class Stimulus {
    protected final String name;
    protected final int code;    // код маркера для ЭЭГ

    public Stimulus(String name, int code) {
        this.name = name;
        this.code = code;
    }

    public String getName() { return name; }
    public int getCode() { return code; }

    @Override
    public String toString() { return name + " [code=" + code + "]"; }
}
