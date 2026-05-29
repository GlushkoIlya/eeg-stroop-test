package eeg.sync;

import eeg.experiment.ExperimentConfig;
import eeg.log.EventLogger;
import java.util.Random;

/**
 * Модуль временных меток.
 * Поддерживает два режима:
 * 1. Реальный — отправка меток через COM/LPT порт на ЭЭГ-усилитель.
 * 2. Эмуляционный — программная генерация меток с настраиваемым джиттером
 *    (нормальное распределение + случайные выбросы).
 */
public class TimestampModule {

    private final ExperimentConfig config;
    private final EventLogger logger;
    private final Random random = new Random();

    // Статистика текущей сессии
    private int markersSent = 0;
    private double totalDelayMs = 0.0;
    private double minDelayMs = Double.MAX_VALUE;
    private double maxDelayMs = Double.MIN_VALUE;

    public TimestampModule(ExperimentConfig config, EventLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    /**
     * Отправка маркера с заданным кодом.
     * Возвращает фактическую задержку в мс (измеренную или эмулированную).
     */
    public double sendMarker(int markerCode) {
        double actualDelayMs;

        if (config.isEmulationMode()) {
            actualDelayMs = emulateMarker(markerCode);
        } else {
            actualDelayMs = sendHardwareMarker(markerCode);
        }

        updateStats(actualDelayMs);
        return actualDelayMs;
    }

    /**
     * Эмуляция маркера с реалистичным джиттером.
     * Джиттер моделируется нормальным распределением N(baseDelay, jitterStd),
     * с заданной вероятностью генерируется выброс (outlier).
     */
    private double emulateMarker(int markerCode) {
        long plannedNano = System.nanoTime();

        double delayMs;
        if (random.nextDouble() < config.getEmulationOutlierProbability()) {
            // Выброс: равномерно в [jitterStd*3, outlierMax]
            double lo = config.getEmulationJitterStdMs() * 3;
            double hi = config.getEmulationOutlierMaxMs();
            delayMs = lo + random.nextDouble() * (hi - lo);
        } else {
            // Нормальный джиттер
            delayMs = config.getEmulationBaseDelayMs() +
                      random.nextGaussian() * config.getEmulationJitterStdMs();
            delayMs = Math.max(0, delayMs);
        }

        // Имитируем фактическую задержку
        try {
            long sleepNs = (long)(delayMs * 1_000_000);
            Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long actualNano = System.nanoTime();
        double measuredDelayMs = (actualNano - plannedNano) / 1_000_000.0;

        logger.logEvent(EventLogger.EventType.MARKER_EMULATED,
            "Marker=" + markerCode + " planned=" + String.format("%.3f", delayMs) +
            "ms actual=" + String.format("%.3f", measuredDelayMs) + "ms",
            measuredDelayMs);

        markersSent++;
        return measuredDelayMs;
    }

    /**
     * Отправка реального маркера через последовательный порт.
     * В текущей реализации — заглушка; в продакшене использовать RXTX/jSerialComm.
     */
    private double sendHardwareMarker(int markerCode) {
        long t0 = System.nanoTime();
        // TODO: реальная отправка через порт
        // SerialPort port = SerialPort.getCommPort(config.getPortName());
        // port.writeBytes(new byte[]{(byte) markerCode}, 1);
        System.out.println("[Hardware] Marker " + markerCode + " sent to " + config.getPortName());
        long t1 = System.nanoTime();
        double delayMs = (t1 - t0) / 1_000_000.0;

        logger.logEvent(EventLogger.EventType.MARKER_SENT,
            "Marker=" + markerCode + " port=" + config.getPortName(), delayMs);
        markersSent++;
        return delayMs;
    }

    private void updateStats(double delayMs) {
        totalDelayMs += delayMs;
        if (delayMs < minDelayMs) minDelayMs = delayMs;
        if (delayMs > maxDelayMs) maxDelayMs = delayMs;
    }

    public void reset() {
        markersSent = 0;
        totalDelayMs = 0;
        minDelayMs = Double.MAX_VALUE;
        maxDelayMs = Double.MIN_VALUE;
    }

    public int getMarkersSent() { return markersSent; }
    public double getMeanDelayMs() { return markersSent > 0 ? totalDelayMs / markersSent : 0; }
    public double getMinDelayMs() { return markersSent > 0 ? minDelayMs : 0; }
    public double getMaxDelayMs() { return markersSent > 0 ? maxDelayMs : 0; }
}
