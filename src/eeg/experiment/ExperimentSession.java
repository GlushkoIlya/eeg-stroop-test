package eeg.experiment;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Сессия эксперимента — хранит временные метки начала и конца,
 * а также идентификатор сессии для привязки к лог-файлу.
 */
public class ExperimentSession {

    private final String sessionId;
    private long startTimeNano;
    private long endTimeNano;
    private long startTimeMs;
    private boolean active = false;

    public ExperimentSession(String experimentId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.sessionId = experimentId + "_" + timestamp;
    }

    public void startSession() {
        startTimeNano = System.nanoTime();
        startTimeMs = System.currentTimeMillis();
        active = true;
        System.out.println("[Session] Started: " + sessionId + " at " + startTimeMs);
    }

    public void endSession() {
        endTimeNano = System.nanoTime();
        active = false;
        long durationMs = (endTimeNano - startTimeNano) / 1_000_000;
        System.out.println("[Session] Ended: " + sessionId + ", duration: " + durationMs + " ms");
    }

    public long getElapsedNano() {
        return active ? System.nanoTime() - startTimeNano : 0;
    }

    public long getElapsedMs() {
        return getElapsedNano() / 1_000_000;
    }

    public String getSessionId() { return sessionId; }
    public long getStartTimeMs() { return startTimeMs; }
    public boolean isActive() { return active; }
}
