package eeg.sync;

import eeg.log.EventLogger;

public class MarkerGenerator {
    private final EventLogger logger;

    public MarkerGenerator(EventLogger logger) {
        this.logger = logger;
    }

    public void sendMarker(int marker) {
        // Заглушка отправки метки на оборудование
        logger.logEvent(EventLogger.EventType.MARKER_SENT, "Marker sent: " + marker);
        System.out.println("Marker sent: " + marker);
    }
}
