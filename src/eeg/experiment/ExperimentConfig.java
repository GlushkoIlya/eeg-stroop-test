package eeg.experiment;

/**
 * Конфигурация параметров эксперимента.
 * Поддерживает как реальный режим работы с ЭЭГ-оборудованием,
 * так и режим эмуляции временных меток для тестирования.
 */
public class ExperimentConfig {

    // Параметры стимуляции
    private int stimulusCount = 10;
    private long stimulusIntervalMs = 2000;
    private long stimulusDurationMs = 500;
    private int postAnswerDelayMs = 500;

    public int getPostAnswerDelayMs() { return postAnswerDelayMs; }
    public void setPostAnswerDelayMs(int delay) { this.postAnswerDelayMs = delay; }

    // Режим работы
    private boolean emulationMode = true;

    // Параметры эмуляции джиттера (нормальное распределение)
    private double emulationBaseDelayMs = 2.5;    // базовая задержка в мс
    private double emulationJitterStdMs = 1.0;    // СКО джиттера в мс
    private double emulationOutlierProbability = 0.05; // вероятность выброса (0.0-1.0)
    private double emulationOutlierMaxMs = 50.0;  // максимальная задержка выброса в мс

    // Параметры порта (реальный режим)
    private String portName = "COM3";
    private int baudRate = 9600;

    // Параметры сохранения данных
    private String logDirectory = System.getProperty("user.home") + "/EEGLogs";
    private String experimentId = "EXP_001";

    public ExperimentConfig() {}

    // --- Getters and Setters ---

    public int getStimulusCount() { return stimulusCount; }
    public void setStimulusCount(int stimulusCount) { this.stimulusCount = stimulusCount; }

    public long getStimulusIntervalMs() { return stimulusIntervalMs; }
    public void setStimulusIntervalMs(long stimulusIntervalMs) { this.stimulusIntervalMs = stimulusIntervalMs; }

    public long getStimulusDurationMs() { return stimulusDurationMs; }
    public void setStimulusDurationMs(long stimulusDurationMs) { this.stimulusDurationMs = stimulusDurationMs; }

    public boolean isEmulationMode() { return emulationMode; }
    public void setEmulationMode(boolean emulationMode) { this.emulationMode = emulationMode; }

    public double getEmulationBaseDelayMs() { return emulationBaseDelayMs; }
    public void setEmulationBaseDelayMs(double emulationBaseDelayMs) { this.emulationBaseDelayMs = emulationBaseDelayMs; }

    public double getEmulationJitterStdMs() { return emulationJitterStdMs; }
    public void setEmulationJitterStdMs(double emulationJitterStdMs) { this.emulationJitterStdMs = emulationJitterStdMs; }

    public double getEmulationOutlierProbability() { return emulationOutlierProbability; }
    public void setEmulationOutlierProbability(double emulationOutlierProbability) {
        this.emulationOutlierProbability = Math.max(0, Math.min(1, emulationOutlierProbability));
    }

    public double getEmulationOutlierMaxMs() { return emulationOutlierMaxMs; }
    public void setEmulationOutlierMaxMs(double emulationOutlierMaxMs) { this.emulationOutlierMaxMs = emulationOutlierMaxMs; }

    public String getPortName() { return portName; }
    public void setPortName(String portName) { this.portName = portName; }

    public int getBaudRate() { return baudRate; }
    public void setBaudRate(int baudRate) { this.baudRate = baudRate; }

    public String getLogDirectory() { return logDirectory; }
    public void setLogDirectory(String logDirectory) { this.logDirectory = logDirectory; }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }
}
