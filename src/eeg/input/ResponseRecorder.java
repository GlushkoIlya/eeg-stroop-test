package eeg.input;

import eeg.log.EventLogger;
import eeg.stimulus.StroopStimulus;
import javafx.scene.paint.Color;

public class ResponseRecorder {

    private final EventLogger logger;
    private long lastStimulusNano = 0;
    private StroopStimulus currentStimulus = null;

    public ResponseRecorder(EventLogger logger) {
        this.logger = logger;
    }

    public void setStimulus(StroopStimulus stimulus, long nanoTime) {
        this.currentStimulus = stimulus;
        this.lastStimulusNano = nanoTime;
    }

    public double recordResponse(AnswerOption chosen) {
        long responseNano = System.nanoTime();
        double reactionTimeMs = lastStimulusNano > 0
                ? (responseNano - lastStimulusNano) / 1_000_000.0
                : -1;

        boolean isCorrect = currentStimulus != null &&
                chosen.matches(currentStimulus.getCorrectColorName());

        String partName = currentStimulus != null ? currentStimulus.getPart().name() : "UNKNOWN";
        String message = String.format("Part=%s Stimulus=%s Correct=%s Chosen=%s ChosenColorText=%s RT=%.1fms Correctness=%s",
                partName,
                currentStimulus != null ? currentStimulus.getName() : "?",
                currentStimulus != null ? currentStimulus.getCorrectColorName() : "?",
                chosen.getDisplayText(),
                toColorName(chosen.getTextColor()),
                reactionTimeMs,
                isCorrect ? "YES" : "NO");

        logger.logEvent(EventLogger.EventType.RESPONSE_RECORDED, message, reactionTimeMs);
        return reactionTimeMs;
    }

    private String toColorName(Color c) {
        if (c == Color.RED) return "RED";
        if (c == Color.BLUE) return "BLUE";
        if (c == Color.GREEN) return "GREEN";
        if (c == Color.YELLOW) return "YELLOW";
        if (c == Color.WHITE) return "WHITE";
        return c.toString();
    }

    public void reset() {
        currentStimulus = null;
        lastStimulusNano = 0;
    }
}