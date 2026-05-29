package eeg.analysis;

import eeg.log.EventLogger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StroopStatistics {

    public static class PartStats {
        public final String partName;
        public final List<Double> reactionTimes = new ArrayList<>();
        public int errorCount = 0;
        public long totalDurationMs = 0;

        public PartStats(String partName) { this.partName = partName; }

        public double getMeanRT() { return average(reactionTimes); }
        public double getMedianRT() { return median(reactionTimes); }
        public double getStdRT() { return std(reactionTimes); }
        public int getCorrectCount() { return reactionTimes.size() - errorCount; }
        public int getTotalTrials() { return reactionTimes.size(); }
        public double getErrorRate() { return getTotalTrials() == 0 ? 0 : (double) errorCount / getTotalTrials(); }
    }

    private static double average(List<Double> list) {
        if (list.isEmpty()) return 0;
        double sum = 0;
        for (double d : list) sum += d;
        return sum / list.size();
    }

    private static double median(List<Double> list) {
        if (list.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) return (sorted.get(mid-1) + sorted.get(mid)) / 2;
        else return sorted.get(mid);
    }

    private static double std(List<Double> list) {
        double mean = average(list);
        double sq = 0;
        for (double d : list) sq += (d - mean) * (d - mean);
        return Math.sqrt(sq / list.size());
    }

    public static Map<String, PartStats> computeFromLog(EventLogger logger) {
        Map<String, PartStats> statsMap = new LinkedHashMap<>();
        statsMap.put("T1", new PartStats("Часть 1 (чёрный текст)"));
        statsMap.put("T2", new PartStats("Часть 2 (прямоугольники)"));
        statsMap.put("T3", new PartStats("Часть 3 (конфликт)"));

        // Разрешаем как точку, так и запятую в числе
        Pattern pattern = Pattern.compile("Part=(T[123]).*RT=([\\d,]+)ms.*Correctness=(YES|NO)");
        for (EventLogger.LogEntry entry : logger.getEntries()) {
            if (entry.type == EventLogger.EventType.RESPONSE_RECORDED && entry.message != null) {
                Matcher m = pattern.matcher(entry.message);
                if (m.find()) {
                    String part = m.group(1);
                    String rtStr = m.group(2).replace(',', '.'); // заменяем запятую на точку
                    double rt;
                    try {
                        rt = Double.parseDouble(rtStr);
                    } catch (NumberFormatException e) {
                        continue; // пропускаем ошибочные
                    }
                    boolean correct = "YES".equals(m.group(3));
                    PartStats ps = statsMap.get(part);
                    if (ps != null) {
                        ps.reactionTimes.add(rt);
                        if (!correct) ps.errorCount++;
                    }
                }
            }
        }
        return statsMap;
    }

    // ---------- Новые показатели ----------
    public static double computeID(double t2Mean, double t3Mean) {
        return t3Mean - t2Mean;
    }

    public static double computeIR(double t2Mean, double t3Mean) {
        if (t3Mean == 0) return 0;
        return t2Mean / t3Mean;
    }

    public static double computeIK(double t1Mean, double t2Mean) {
        if (t1Mean == 0) return 0;
        return t2Mean / t1Mean;
    }
}