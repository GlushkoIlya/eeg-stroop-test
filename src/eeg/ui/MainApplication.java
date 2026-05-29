package eeg.ui;

import eeg.experiment.ExperimentConfig;
import eeg.experiment.ExperimentManager;
import eeg.log.EventLogger;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Точка входа JavaFX-приложения.
 * Создаёт центральные компоненты и запускает главное окно.
 */
public class MainApplication extends Application {

    private ExperimentConfig config;
    private EventLogger logger;
    private ExperimentManager manager;

    @Override
    public void start(Stage primaryStage) {
        config = new ExperimentConfig();
        logger = new EventLogger();
        manager = new ExperimentManager(config, logger);
        manager.prepare();

        MainWindow mainWindow = new MainWindow(manager, config, logger);
        Scene scene = mainWindow.createScene();

        primaryStage.setTitle("EEG Experiment Suite — НИИ Нейронаук и Медицины");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(720);
        primaryStage.setWidth(1280);
        primaryStage.setHeight(800);
        primaryStage.setOnCloseRequest(e -> {
            manager.stopExperiment();
            System.exit(0);
        });
        primaryStage.show();
    }
}
