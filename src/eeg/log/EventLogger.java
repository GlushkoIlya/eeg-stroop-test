package eeg.log;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Журнал событий эксперимента.
 * Логирует события с нанесекундной точностью и поддерживает экспорт в CSV.
 */
public class EventLogger {

    public enum EventType {
        SESSION_START, SESSION_END,
        STIMULUS_PRESENTED, STIMULUS_HIDDEN,
        MARKER_SENT, MARKER_EMULATED,
        RESPONSE_RECORDED,
        INFO, WARNING, ERROR
    }

    public static class LogEntry {
        public final long nanoTime;
        public final long wallClockMs;
        public final EventType type;
        public final String message;
        public final double delayMs; // задержка относительно запланированного времени

        public LogEntry(long nanoTime, long wallClockMs, EventType type, String message, double delayMs) {
            this.nanoTime = nanoTime;
            this.wallClockMs = wallClockMs;
            this.type = type;
            this.message = message;
            this.delayMs = delayMs;
        }

        public String toCsvLine() {
            return nanoTime + "," + wallClockMs + "," + type + ",\"" +
                   message.replace("\"", "\"\"") + "\"," +
                   String.format("%.4f", delayMs);
        }
    }

    private final List<LogEntry> entries = new ArrayList<>();
    private final List<LogListener> listeners = new ArrayList<>();
    private String logDirectory;
    private String sessionId;

    public interface LogListener {
        void onLogEntry(LogEntry entry);
    }

    public EventLogger() {
        this.logDirectory = System.getProperty("user.home") + "/EEGLogs";
        this.sessionId = "session";
    }

    public void configure(String logDirectory, String sessionId) {
        this.logDirectory = logDirectory;
        this.sessionId = sessionId;
    }

    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public synchronized void logEvent(EventType type, String message) {
        logEvent(type, message, 0.0);
    }

    public synchronized void logEvent(EventType type, String message, double delayMs) {
        long nano = System.nanoTime();
        long wall = System.currentTimeMillis();
        LogEntry entry = new LogEntry(nano, wall, type, message, delayMs);
        entries.add(entry);
        String formatted = String.format("[%s] %s: %s (delay=%.2fms)",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            type, message, delayMs);
        System.out.println(formatted);
        for (LogListener l : listeners) l.onLogEntry(entry);
    }

    public synchronized List<LogEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }

    /**
     * Экспорт журнала в CSV.
     * @return путь к созданному файлу
     */
    public String exportToCsv() throws IOException {
        Path dir = Paths.get(logDirectory);
        Files.createDirectories(dir);
        String filename = sessionId + "_events.csv";
        Path filePath = dir.resolve(filename);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("nano_time,wall_clock_ms,event_type,message,delay_ms");
            for (LogEntry e : entries) {
                writer.println(e.toCsvLine());
            }
        }
        System.out.println("[Logger] Exported " + entries.size() + " entries to: " + filePath);
        return filePath.toString();
    }
}
