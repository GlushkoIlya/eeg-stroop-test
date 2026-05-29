package eeg.analysis;

import eeg.log.EventLogger;
import eeg.log.EventLogger.EventType;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Модуль пост-экспериментального анализа точности синхронизации.
 * Рассчитывает статистики задержек маркеров и предоставляет данные для графиков.
 */
public class JitterAnalyzer {

    public static class Stats {
        public final int count;
        public final double mean;
        public final double std;
        public final double min;
        public final double max;
        public final double p95;
        public final double p99;
        public final List<Double> delays;

        public Stats(List<Double> delays) {
            this.delays = new ArrayList<>(delays);
            this.count = delays.size();
            if (count == 0) {
                mean = std = min = max = p95 = p99 = 0;
                return;
            }
            double sum = 0;
            double mn = Double.MAX_VALUE, mx = Double.MIN_VALUE;
            for (double d : delays) {
                sum += d;
                if (d < mn) mn = d;
                if (d > mx) mx = d;
            }
            this.mean = sum / count;
            this.min = mn;
            this.max = mx;

            double variance = 0;
            for (double d : delays) variance += (d - mean) * (d - mean);
            this.std = Math.sqrt(variance / count);

            List<Double> sorted = new ArrayList<>(delays);
            Collections.sort(sorted);
            this.p95 = percentile(sorted, 95);
            this.p99 = percentile(sorted, 99);
        }

        private double percentile(List<Double> sorted, double pct) {
            if (sorted.isEmpty()) return 0;
            int idx = (int) Math.ceil((pct / 100.0) * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
        }

        @Override
        public String toString() {
            return String.format(
                "Markers: %d\nMean:    %.3f ms\nStd:     %.3f ms\nMin:     %.3f ms\nMax:     %.3f ms\nP95:     %.3f ms\nP99:     %.3f ms",
                count, mean, std, min, max, p95, p99);
        }
    }

    /**
     * Анализ журнала событий: извлекает задержки маркеров.
     */
    public static Stats analyze(EventLogger logger) {
        List<Double> delays = logger.getEntries().stream()
            .filter(e -> e.type == EventType.MARKER_EMULATED || e.type == EventType.MARKER_SENT)
            .map(e -> e.delayMs)
            .filter(d -> d >= 0)
            .collect(Collectors.toList());
        return new Stats(delays);
    }

    /**
     * Построение гистограммы: возвращает бины для отображения в JavaFX BarChart.
     * @param stats результаты анализа
     * @param bins количество бинов
     * @return массив [binLabel -> count]
     */
    public static Map<String, Long> buildHistogram(Stats stats, int bins) {
        if (stats.count == 0) return Collections.emptyMap();
        double range = stats.max - stats.min;
        double binWidth = range / bins;
        if (binWidth <= 0) binWidth = 1.0;
        Map<String, Long> histogram = new LinkedHashMap<>();
        double[] counts = new double[bins];
        for (double d : stats.delays) {
            int idx = (int) ((d - stats.min) / binWidth);
            if (idx >= bins) idx = bins - 1;
            counts[idx]++;
        }
        for (int i = 0; i < bins; i++) {
            double lo = stats.min + i * binWidth;
            double hi = lo + binWidth;
            String label = String.format("%.1f–%.1f", lo, hi);
            histogram.put(label, (long) counts[i]);
        }
        return histogram;
    }

    /**
     * Экспорт аналитического отчёта в текстовый файл.
     */
    public static String exportReport(Stats stats, String directory, String sessionId) throws IOException {
        Path dir = Paths.get(directory);
        Files.createDirectories(dir);
        String filename = sessionId + "_analysis.txt";
        Path filePath = dir.resolve(filename);
        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("=== EEG Synchronization Analysis Report ===");
            w.println("Session: " + sessionId);
            w.println("Generated: " + new Date());
            w.println();
            w.println(stats);
            w.println();
            w.println("--- Delay Distribution ---");
            Map<String, Long> hist = buildHistogram(stats, 10);
            for (Map.Entry<String, Long> e : hist.entrySet()) {
                w.printf("  %-15s | %s%n", e.getKey(),
                    "#".repeat((int)(e.getValue() * 40 / Math.max(1, stats.count))));
            }
            w.println();
            w.println("--- Raw Delays (ms) ---");
            for (int i = 0; i < stats.delays.size(); i++) {
                w.printf("  [%3d] %.4f ms%n", i + 1, stats.delays.get(i));
            }
        }
        System.out.println("[Analyzer] Report exported to: " + filePath);
        return filePath.toString();
    }
}
