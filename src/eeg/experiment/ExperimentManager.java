package eeg.experiment;

import eeg.analysis.JitterAnalyzer;
import eeg.input.ResponseRecorder;
import eeg.log.EventLogger;
import eeg.stimulus.VisualStimulus;
import eeg.sync.TimestampModule;
import javafx.application.Platform;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import eeg.analysis.JitterAnalyzer;
import eeg.input.AnswerOption;
import eeg.input.ResponseRecorder;
import eeg.log.EventLogger;
import eeg.stimulus.StroopStimulus;
import eeg.sync.TimestampModule;
import javafx.application.Platform;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Центральный менеджер эксперимента.
 * Координирует работу всех модулей: конфигурацию, сессию, логирование,
 * диспетчеризацию стимулов, генерацию меток и регистрацию ответов.
 */
public class ExperimentManager {

    // Состояния эксперимента
    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    private final ExperimentConfig config;
    private final EventLogger logger;
    private final TimestampModule timestampModule;
    private final ResponseRecorder responseRecorder;
    private ExperimentSession session;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTask;
    private volatile State state = State.IDLE;

    // Списки стимулов по частям
    private List<StroopStimulus> part1Stimuli;
    private List<StroopStimulus> part2Stimuli;
    private List<StroopStimulus> part3Stimuli;
    private int currentPartIdx = 0;           // 0=T1,1=T2,2=T3
    private int currentStimulusIdx = 0;
    private List<StroopStimulus> currentPartList;

    // Таймеры частей (в миллисекундах)
    private long partStartTimeMs = 0;
    private long part1DurationMs = 0;
    private long part2DurationMs = 0;
    private long part3DurationMs = 0;

    // Callbacks
    private Consumer<StroopStimulus> onStimulusPresented;
    private Consumer<String> onStimulusHidden;
    private Runnable onExperimentFinished;
    private Consumer<String> onStatusChanged;
    private Consumer<Double> onProgressChanged;
    private Consumer<Long> onPartTimerUpdate;  // для отображения таймера части
    private Consumer<String> onPartInstruction; // показать инструкцию перед частью

    public ExperimentManager(ExperimentConfig config, EventLogger logger) {
        this.config = config;
        this.logger = logger;
        this.timestampModule = new TimestampModule(config, logger);
        this.responseRecorder = new ResponseRecorder(logger);
    }

    public void setCallbacks(
            Consumer<StroopStimulus> onStimulusPresented,
            Consumer<String> onStimulusHidden,
            Runnable onExperimentFinished,
            Consumer<String> onStatusChanged,
            Consumer<Double> onProgressChanged,
            Consumer<Long> onPartTimerUpdate,
            Consumer<String> onPartInstruction) {
        this.onStimulusPresented = onStimulusPresented;
        this.onStimulusHidden = onStimulusHidden;
        this.onExperimentFinished = onExperimentFinished;
        this.onStatusChanged = onStatusChanged;
        this.onProgressChanged = onProgressChanged;
        this.onPartTimerUpdate = onPartTimerUpdate;
        this.onPartInstruction = onPartInstruction;
    }

    public void prepare() {
        StroopStimulusGenerator generator = new StroopStimulusGenerator();
        int codeStart = 1;
        part1Stimuli = generator.generate(StroopPart.T1, config.getStimulusCount(), codeStart);
        codeStart += part1Stimuli.size();
        part2Stimuli = generator.generate(StroopPart.T2, config.getStimulusCount(), codeStart);
        codeStart += part2Stimuli.size();
        part3Stimuli = generator.generate(StroopPart.T3, config.getStimulusCount(), codeStart);
        timestampModule.reset();
    }

    public void startExperiment() {
        if (state == State.RUNNING) return;
        currentPartIdx = 0;
        currentStimulusIdx = 0;
        part1DurationMs = part2DurationMs = part3DurationMs = 0;

        session = new ExperimentSession(config.getExperimentId());
        logger.configure(config.getLogDirectory(), session.getSessionId());
        logger.clear();
        session.startSession();
        logger.logEvent(EventLogger.EventType.SESSION_START, "Stroop experiment started, mode=" +
                (config.isEmulationMode() ? "EMULATION" : "HARDWARE"));

        state = State.RUNNING;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        updateStatus("Эксперимент запущен");

        // Показать инструкцию для первой части
        showPartInstruction(StroopPart.T1);
    }

    private void showPartInstruction(StroopPart part) {
        Platform.runLater(() -> {
            if (onPartInstruction != null) {
                onPartInstruction.accept(part.title + "\n\n" + part.instruction +
                        "\n\nНажмите «Продолжить», когда будете готовы.");
            }
        });
        // Ждём сигнала от UI (кнопка "Продолжить") – для этого добавим метод continueToPart()
    }

//    public void continueToPart() {
//        if (state != State.RUNNING) return;
//        partStartTimeMs = System.currentTimeMillis();
//        currentStimulusIdx = 0;
//        switch (currentPartIdx) {
//            case 0: currentPartList = part1Stimuli; break;
//            case 1: currentPartList = part2Stimuli; break;
//            case 2: currentPartList = part3Stimuli; break;
//        }
//        // Запускаем таймер обновления для UI
//        startPartTimerUpdater();
//        scheduleNextStimulus(500); // небольшая задержка перед первым стимулом
//    }

    private void startPartTimerUpdater() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.scheduleAtFixedRate(() -> {
                if (state == State.RUNNING && currentPartList != null && partStartTimeMs > 0) {
                    long elapsed = System.currentTimeMillis() - partStartTimeMs;
                    Platform.runLater(() -> {
                        if (onPartTimerUpdate != null) onPartTimerUpdate.accept(elapsed);
                    });
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

//    private void scheduleNextStimulus(long delayMs) {
//        if (state != State.RUNNING) return;
//        if (currentStimulusIdx >= currentPartList.size()) {
//            finishCurrentPart();
//            return;
//        }
//        currentTask = scheduler.schedule(this::presentCurrentStimulus, delayMs, TimeUnit.MILLISECONDS);
//    }

    // Добавьте поле в классе
    private boolean waitingForAnswer = false;

    // Измените presentCurrentStimulus (уберите автопереключение)
    private void presentCurrentStimulus() {
        if (state != State.RUNNING) return;
        StroopStimulus stimulus = currentPartList.get(currentStimulusIdx);
        long presentedNano = System.nanoTime();
        double markerDelay = timestampModule.sendMarker(stimulus.getCode());

        logger.logEvent(EventLogger.EventType.STIMULUS_PRESENTED,
                "Part=" + stimulus.getPart().name() + " Stimulus=" + stimulus.getName() +
                        " text=" + (stimulus.getText().isEmpty() ? "[RECT]" : stimulus.getText()) +
                        " correct=" + stimulus.getCorrectColorName() +
                        " markerDelay=" + String.format("%.3f", markerDelay) + "ms", markerDelay);

        responseRecorder.setStimulus(stimulus, presentedNano);
        waitingForAnswer = true;   // ждём ответ

        double progress = computeOverallProgress();
        Platform.runLater(() -> {
            if (onStimulusPresented != null) onStimulusPresented.accept(stimulus);
            if (onProgressChanged != null) onProgressChanged.accept(progress);
            updateStatus(String.format("Часть %d: стимул %d/%d",
                    currentPartIdx+1, currentStimulusIdx+1, currentPartList.size()));
        });

        // НЕТ вызова scheduleNextStimulus
        // НЕТ автоматического скрытия по таймеру
    }

    // Добавьте метод для показа следующего стимула после задержки
    private void scheduleNextStimulusAfterDelay(long delayMs) {
        if (state != State.RUNNING) return;
        if (currentTask != null) currentTask.cancel(false);
        currentTask = scheduler.schedule(() -> {
            currentStimulusIdx++;
            if (currentStimulusIdx < currentPartList.size()) {
                presentCurrentStimulus();
            } else {
                finishCurrentPart();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    // Измените recordAnswer
    public void recordAnswer(AnswerOption chosen) {
        if (state == State.RUNNING && currentPartList != null && waitingForAnswer) {
            waitingForAnswer = false;
            // Запись ответа
            responseRecorder.recordResponse(chosen);
            // Скрыть стимул (визуально)
            Platform.runLater(() -> {
                if (onStimulusHidden != null) onStimulusHidden.accept("");
            });
            // Показать следующий через 500 мс
            scheduleNextStimulusAfterDelay(config.getPostAnswerDelayMs());
        }
    }

    // В continueToPart сбросьте индексы и покажите первый стимул (без ожидания)
    public void continueToPart() {
        if (state != State.RUNNING) return;
        partStartTimeMs = System.currentTimeMillis();
        currentStimulusIdx = 0;
        waitingForAnswer = false;
        switch (currentPartIdx) {
            case 0: currentPartList = part1Stimuli; break;
            case 1: currentPartList = part2Stimuli; break;
            case 2: currentPartList = part3Stimuli; break;
        }
        startPartTimerUpdater();
        // Показываем первый стимул сразу (без задержки)
        presentCurrentStimulus();
    }

//    private void presentCurrentStimulus() {
//        if (state != State.RUNNING) return;
//        StroopStimulus stimulus = currentPartList.get(currentStimulusIdx);
//        long presentedNano = System.nanoTime();
//        double markerDelay = timestampModule.sendMarker(stimulus.getCode());
//
//        logger.logEvent(EventLogger.EventType.STIMULUS_PRESENTED,
//                "Part=" + stimulus.getPart().name() + " Stimulus=" + stimulus.getName() +
//                        " text=" + (stimulus.getText().isEmpty() ? "[RECT]" : stimulus.getText()) +
//                        " correct=" + stimulus.getCorrectColorName() +
//                        " markerDelay=" + String.format("%.3f", markerDelay) + "ms", markerDelay);
//
//        responseRecorder.setStimulus(stimulus, presentedNano);
//
//        double progress = computeOverallProgress();
//        Platform.runLater(() -> {
//            if (onStimulusPresented != null) onStimulusPresented.accept(stimulus);
//            if (onProgressChanged != null) onProgressChanged.accept(progress);
//            updateStatus(String.format("Часть %d: стимул %d/%d",
//                    currentPartIdx+1, currentStimulusIdx+1, currentPartList.size()));
//        });
//
//        // Скрыть стимул через stimulusDurationMs
//        scheduler.schedule(() -> {
//            logger.logEvent(EventLogger.EventType.STIMULUS_HIDDEN,
//                    "Stimulus=" + stimulus.getName() + " hidden");
//            Platform.runLater(() -> {
//                if (onStimulusHidden != null) onStimulusHidden.accept("");
//            });
//        }, config.getStimulusDurationMs(), TimeUnit.MILLISECONDS);
//
//        currentStimulusIdx++;
//        scheduleNextStimulus(config.getStimulusIntervalMs());
//    }

    private void finishCurrentPart() {
        long partDuration = System.currentTimeMillis() - partStartTimeMs;
        switch (currentPartIdx) {
            case 0: part1DurationMs = partDuration; break;
            case 1: part2DurationMs = partDuration; break;
            case 2: part3DurationMs = partDuration; break;
        }
        logger.logEvent(EventLogger.EventType.INFO,
                "Part " + (currentPartIdx+1) + " finished, duration=" + partDuration + "ms");

        currentPartIdx++;
        if (currentPartIdx < 3) {
            // Переход к следующей части
            StroopPart nextPart = (currentPartIdx == 1) ? StroopPart.T2 : StroopPart.T3;
            showPartInstruction(nextPart);
        } else {
            finishExperiment();
        }
    }

    private double computeOverallProgress() {
        int total = part1Stimuli.size() + part2Stimuli.size() + part3Stimuli.size();
        int done = (currentPartIdx > 0 ? part1Stimuli.size() : 0) +
                (currentPartIdx > 1 ? part2Stimuli.size() : 0) +
                (currentPartIdx == 2 ? currentStimulusIdx : 0);
        return (double) done / total;
    }

    private void finishExperiment() {
        state = State.FINISHED;
        session.endSession();
        logger.logEvent(EventLogger.EventType.SESSION_END,
                "Experiment finished. Part durations (ms): T1=" + part1DurationMs +
                        ", T2=" + part2DurationMs + ", T3=" + part3DurationMs);
        try { logger.exportToCsv(); } catch (IOException e) { e.printStackTrace(); }
        if (scheduler != null) scheduler.shutdown();
        Platform.runLater(() -> {
            updateStatus("Эксперимент завершён. Спасибо!");
            if (onProgressChanged != null) onProgressChanged.accept(1.0);
            if (onExperimentFinished != null) onExperimentFinished.run();
        });
    }

//    public void recordAnswer(AnswerOption chosen) {
//        if (state == State.RUNNING && currentPartList != null && currentStimulusIdx > 0) {
//            responseRecorder.recordResponse(chosen);
//        }
//    }

    public void stopExperiment() {
        if (state == State.RUNNING || state == State.PAUSED) {
            state = State.IDLE;
            if (currentTask != null) currentTask.cancel(false);
            if (scheduler != null) scheduler.shutdownNow();
            session.endSession();
            logger.logEvent(EventLogger.EventType.SESSION_END, "Experiment stopped by user");
            Platform.runLater(() -> updateStatus("Эксперимент остановлен"));
        }
    }


//    public void recordKeyResponse(String key) {
//        if (state == State.RUNNING) {
//            responseRecorder.recordResponse(key);
//        }
//    }

    public JitterAnalyzer.Stats getAnalysisStats() { return JitterAnalyzer.analyze(logger); }

    public long getPart1Duration() { return part1DurationMs; }
    public long getPart2Duration() { return part2DurationMs; }
    public long getPart3Duration() { return part3DurationMs; }

    private void updateStatus(String msg) { if (onStatusChanged != null) onStatusChanged.accept(msg); }
//    public JitterAnalyzer.Stats getAnalysisStats() {
//        return JitterAnalyzer.analyze(logger);
//    }

//    private void updateStatus(String msg) {
//        if (onStatusChanged != null) onStatusChanged.accept(msg);
//    }

    public State getState() { return state; }
    public ExperimentConfig getConfig() { return config; }
    public EventLogger getLogger() { return logger; }
    public TimestampModule getTimestampModule() { return timestampModule; }
    public ExperimentSession getSession() { return session; }
}
