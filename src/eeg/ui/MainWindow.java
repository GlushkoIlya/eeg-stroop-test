package eeg.ui;

import eeg.analysis.JitterAnalyzer;
import eeg.experiment.ExperimentConfig;
import eeg.experiment.ExperimentManager;
import eeg.log.EventLogger;
import eeg.stimulus.VisualStimulus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import eeg.input.AnswerOption;
import eeg.stimulus.StroopStimulus;
import eeg.experiment.StroopStimulusGenerator;
import javafx.scene.web.WebView;
import java.util.*;
import eeg.experiment.StroopPart;
import eeg.analysis.StroopStatistics;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory; // если используется, но у нас SimpleStringProperty


/**
 * Главное окно приложения.
 * Содержит три вкладки:
 * 1. Управление экспериментом (запуск, стимул, прогресс)
 * 2. Настройки (параметры стимуляции и эмуляции)
 * 3. Анализ логов (статистика, гистограмма, таблица событий)
 */
public class MainWindow {

    private static final String ACCENT_COLOR = "#4F8EF7";
    private static final String SUCCESS_COLOR = "#34C759";
    private static final String WARNING_COLOR = "#FF9500";
    private static final String DANGER_COLOR = "#FF3B30";
    private static final String BG_DARK = "#1C1C1E";
    private static final String BG_CARD = "#2C2C2E";
    private static final String BG_INPUT = "#3A3A3C";
    private static final String TEXT_PRIMARY = "#FFFFFF";
    private static final String TEXT_SECONDARY = "#AEAEB2";

    private final ExperimentManager manager;
    private final ExperimentConfig config;
    private final EventLogger logger;

    // Experiment tab controls
    private Label statusLabel;
    private Label stimulusDisplay;
    private ProgressBar progressBar;
    private Button startStopButton;
    private Label progressLabel;
    private Label markerCountLabel;
    private Label meanDelayLabel;
    private Circle statusIndicator;
    private Label modeLabel;

    // Settings controls
    private Spinner<Integer> stimulusCountSpinner;
    private Spinner<Integer> intervalSpinner;
    private Spinner<Integer> durationSpinner;
    private ToggleGroup modeToggle;
    private RadioButton emulationRadio;
    private RadioButton realRadio;
    private VBox emulationParamsBox;
    private Spinner<Double> baseDelaySpinner;
    private Spinner<Double> jitterStdSpinner;
    private Spinner<Double> outlierProbSpinner;
    private Spinner<Double> outlierMaxSpinner;
    private TextField portField;
    private TextField experimentIdField;
    private TextField logDirField;

    // Analysis tab controls
    private TableView<EventLogger.LogEntry> logTable;
    private ObservableList<EventLogger.LogEntry> logData;
    private Label statCount, statMean, statStd, statMin, statMax, statP95;
    private BarChart<String, Number> jitterChart;

    private VBox answerButtonsPanel;
    private Button[] answerButtons = new Button[4];
    private List<AnswerOption> currentAnswerOptions;
    private Label timerLabel;
    private Button continueButton;
    private VBox instructionOverlay;

    private Label idLabel;
    private Label irLabel;
    private Label ikLabel;

    public MainWindow(ExperimentManager manager, ExperimentConfig config, EventLogger logger) {
        this.manager = manager;
        this.config = config;
        this.logger = logger;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        // Top bar
        root.setTop(createTopBar());

        // Tab pane
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-tab-min-height: 42px; -fx-tab-max-height: 42px; -fx-background-color: transparent;");

        Tab experimentTab = createExperimentTab();
        Tab settingsTab = createSettingsTab();
        Tab analysisTab = createAnalysisTab();
        Tab helpTab = createHelpTab();
        tabPane.getTabs().addAll(experimentTab, settingsTab, analysisTab, helpTab);

//        tabPane.getTabs().addAll(experimentTab, settingsTab, analysisTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        root.setCenter(tabPane);
        root.setBottom(createStatusBar());

        // Listen to log events for live table update
        logger.addListener(entry -> Platform.runLater(() -> {
            if (logData != null) logData.add(entry);
        }));

        // Wire up experiment callbacks
        manager.setCallbacks(
                this::onStimulusPresented,
                this::onStimulusHidden,
                this::onExperimentFinished,
                this::onStatusChanged,
                this::onProgressChanged,
                this::onPartTimerUpdate,
                this::onPartInstruction
        );

        Scene scene = new Scene(root, 1280, 800);
        applyGlobalStyle(scene);
        return scene;
    }

    // ── TOP BAR ─────────────────────────────────────────────────────────────

    private HBox createTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: " + BG_CARD + "; -fx-border-color: #3A3A3C; -fx-border-width: 0 0 1 0;");

        // Logo / title
        VBox titleBox = new VBox(2);
        Label title = new Label("EEG Experiment Suite");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);
        Label subtitle = new Label("НИИ Нейронаук и Медицины — Лаборатория дифференциальной психофизиологии");
        subtitle.setFont(Font.font("System", 11));
        subtitle.setTextFill(Color.web(TEXT_SECONDARY));
        titleBox.getChildren().addAll(title, subtitle);

        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Mode badge - исправлено: убран text block, используется обычная строка
        modeLabel = new Label(config.isEmulationMode() ? "⚡ ЭМУЛЯЦИЯ" : "🔌 РЕАЛЬНЫЙ");
        String modeStyle = "-fx-background-color: " + (config.isEmulationMode() ? WARNING_COLOR : SUCCESS_COLOR) +
                "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; " +
                "-fx-padding: 4 10 4 10; -fx-background-radius: 8;";
        modeLabel.setStyle(modeStyle);

        // Status indicator dot
        statusIndicator = new Circle(6);
        statusIndicator.setFill(Color.web("#8E8E93"));

        bar.getChildren().addAll(titleBox, statusIndicator, modeLabel);
        return bar;
    }

    // ── EXPERIMENT TAB ───────────────────────────────────────────────────────

//    private Tab createExperimentTab() {
//        Tab tab = new Tab("▶  Эксперимент");
//
//        BorderPane content = new BorderPane();
//        content.setPadding(new Insets(24));
//        content.setStyle("-fx-background-color: " + BG_DARK + ";");
//
//        // Left: stimulus display
//        VBox stimulusPanel = createStimulusPanel();
//        content.setCenter(stimulusPanel);
//
//        // Right: controls + live stats
//        VBox controlPanel = createControlPanel();
//        controlPanel.setPrefWidth(300);
//        content.setRight(controlPanel);
//
//        tab.setContent(content);
//        return tab;
//    }

//    private VBox createStimulusPanel() {
//        VBox panel = new VBox(20);
//        panel.setAlignment(Pos.CENTER);
//        panel.setPadding(new Insets(0, 24, 0, 0));
//
//        // Stimulus display area
//        StackPane displayArea = new StackPane();
//        displayArea.setMinHeight(360);
//        String displayStyle = "-fx-background-color: #000000; -fx-background-radius: 16; " +
//                "-fx-border-color: #3A3A3C; -fx-border-radius: 16; -fx-border-width: 1;";
//        displayArea.setStyle(displayStyle);
//
//        stimulusDisplay = new Label("Ожидание запуска...");
//        stimulusDisplay.setFont(Font.font("System", FontWeight.BOLD, 64));
//        stimulusDisplay.setTextFill(Color.web("#8E8E93"));
//        stimulusDisplay.setWrapText(true);
//        stimulusDisplay.setAlignment(Pos.CENTER);
//        displayArea.getChildren().add(stimulusDisplay);
//        VBox.setVgrow(displayArea, Priority.ALWAYS);
//
//        // Progress section
//        VBox progressSection = new VBox(8);
//        HBox progressHeader = new HBox();
//        Label progressTitle = new Label("Прогресс эксперимента");
//        progressTitle.setTextFill(Color.web(TEXT_SECONDARY));
//        progressTitle.setFont(Font.font("System", 13));
//        progressLabel = new Label("0 / 0");
//        progressLabel.setTextFill(Color.web(TEXT_PRIMARY));
//        progressLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
//        progressHeader.getChildren().addAll(progressTitle, createSpacer(), progressLabel);
//
//        progressBar = new ProgressBar(0);
//        progressBar.setMaxWidth(Double.MAX_VALUE);
//        progressBar.setPrefHeight(8);
//        progressBar.setStyle("-fx-accent: " + ACCENT_COLOR + ";");
//        progressSection.getChildren().addAll(progressHeader, progressBar);
//
//        panel.getChildren().addAll(displayArea, progressSection);
//        return panel;
//    }
//
//    private VBox createControlPanel() {
//        VBox panel = new VBox(16);
//        panel.setAlignment(Pos.TOP_CENTER);
//
//        // Start/Stop button
//        startStopButton = new Button("▶  Запустить");
//        startStopButton.setMaxWidth(Double.MAX_VALUE);
//        startStopButton.setPrefHeight(52);
//        startStopButton.setFont(Font.font("System", FontWeight.BOLD, 15));
//        startStopButton.setStyle(primaryButtonStyle(ACCENT_COLOR));
//        startStopButton.setOnAction(e -> onStartStop());
//
//        // Live stats cards
//        VBox statsCard = createCard("📊 Статистика сессии");
//        markerCountLabel = createStatRow(statsCard, "Маркеров отправлено", "0");
//        meanDelayLabel = createStatRow(statsCard, "Средняя задержка", "—");
//
//        // Keyboard hint
//        VBox hintCard = createCard("⌨  Управление");
//        Label hint1 = new Label("ПРОБЕЛ — ответ испытуемого");
//        Label hint2 = new Label("ESC — остановить эксперимент");
//        hint1.setTextFill(Color.web(TEXT_SECONDARY));
//        hint2.setTextFill(Color.web(TEXT_SECONDARY));
//        hint1.setFont(Font.font("System", 12));
//        hint2.setFont(Font.font("System", 12));
//        hintCard.getChildren().addAll(hint1, hint2);
//
//        // Status label
//        statusLabel = new Label("Готов к запуску");
//        statusLabel.setTextFill(Color.web(TEXT_SECONDARY));
//        statusLabel.setFont(Font.font("System", 12));
//        statusLabel.setWrapText(true);
//        statusLabel.setMaxWidth(Double.MAX_VALUE);
//
//        panel.getChildren().addAll(startStopButton, statsCard, hintCard, statusLabel);
//
//        // Keyboard handler placeholder (set on scene)
//        Platform.runLater(() -> {
//            if (startStopButton.getScene() != null) {
//                startStopButton.getScene().setOnKeyPressed(e -> {
//                    switch (e.getCode()) {
//                        case SPACE -> manager.recordKeyResponse("SPACE");
//                        case ESCAPE -> onStartStop();
//                        default -> {}
//                    }
//                });
//            }
//        });
//
//        return panel;
//    }

    private Tab createExperimentTab() {
        Tab tab = new Tab("▶  Эксперимент");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        // Центр: стимул и кнопки
        VBox centerArea = new VBox(20);
        centerArea.setAlignment(Pos.CENTER);

        // Стимул
        stimulusDisplay = new Label("Ожидание");
        stimulusDisplay.setFont(Font.font(48));          // уменьшили с 72 до 48
        stimulusDisplay.setWrapText(true);               // перенос по словам
        stimulusDisplay.setAlignment(Pos.CENTER);
        stimulusDisplay.setMaxWidth(Double.MAX_VALUE);   // растягивание по ширине

        StackPane stimulusPane = new StackPane(stimulusDisplay);
        stimulusPane.setMinHeight(400);                  // увеличили высоту
        stimulusPane.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 20;");

        // Панель кнопок ответа
        answerButtonsPanel = new VBox(10);
        answerButtonsPanel.setAlignment(Pos.CENTER);
        HBox buttonRow = new HBox(15);
        buttonRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            Button btn = new Button();
            btn.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 10 20;");
            final int idx = i;
            btn.setOnAction(e -> onAnswerChosen(idx));
            buttonRow.getChildren().add(btn);
            answerButtons[i] = btn;
        }
        answerButtonsPanel.getChildren().add(buttonRow);

        centerArea.getChildren().addAll(stimulusPane, answerButtonsPanel);
        root.setCenter(centerArea);

        // Правая панель: таймер, прогресс, кнопка старт/стоп, инструкция
        VBox rightPanel = new VBox(15);
        rightPanel.setPrefWidth(280);
        BorderPane.setMargin(rightPanel, new Insets(0, 0, 0, 20));

        timerLabel = new Label("Таймер: 0 сек.");
        timerLabel.setFont(Font.font(16));
        timerLabel.setTextFill(Color.web(ACCENT_COLOR));

        startStopButton = new Button("▶ Запустить");
        startStopButton.setOnAction(e -> onStartStop());

        continueButton = new Button("▶ Продолжить");
        continueButton.setVisible(false);
        continueButton.setOnAction(e -> onContinue());

        progressBar = new ProgressBar(0);
        progressLabel = new Label("0%");

        statusLabel = new Label("Готов");

        rightPanel.getChildren().addAll(timerLabel, startStopButton, continueButton, progressBar, progressLabel, statusLabel);
        root.setRight(rightPanel);

        tab.setContent(root);
        return tab;
    }

    private void generatePlainTextAnswerOptions(StroopStimulus stimulus) {
        List<String> allColors = List.of("КРАСНЫЙ", "СИНИЙ", "ЗЕЛЁНЫЙ", "ЖЁЛТЫЙ");
        List<String> options = new ArrayList<>(allColors);
        Collections.shuffle(options);
        List<AnswerOption> opts = new ArrayList<>();
        for (String opt : options) {
            opts.add(new AnswerOption(opt, Color.BLACK)); // цвет текста чёрный
        }
        currentAnswerOptions = opts;
        for (int i = 0; i < answerButtons.length; i++) {
            AnswerOption ao = opts.get(i);
            answerButtons[i].setText(ao.getDisplayText());
            answerButtons[i].setStyle("-fx-text-fill: black; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-color: #E0E0E0; -fx-background-radius: 8;");
        }
    }

    private void generateColorAnswerOptions(StroopStimulus stimulus) {
        // Список всех цветов и их названий
        String[] colorNames = {"КРАСНЫЙ", "СИНИЙ", "ЗЕЛЁНЫЙ", "ЖЁЛТЫЙ"};
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW};

        // Перемешиваем порядок
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < colorNames.length; i++) indices.add(i);
        Collections.shuffle(indices);

        List<AnswerOption> opts = new ArrayList<>();
        for (int idx : indices) {
            opts.add(new AnswerOption(colorNames[idx], colors[idx]));
        }
        currentAnswerOptions = opts;

        // Настраиваем кнопки как цветные прямоугольники
        for (int i = 0; i < answerButtons.length; i++) {
            AnswerOption ao = opts.get(i);
            answerButtons[i].setText("");  // убираем текст
            String bgColor = String.format("-fx-background-color: rgb(%d,%d,%d);",
                    (int)(ao.getTextColor().getRed()*255),
                    (int)(ao.getTextColor().getGreen()*255),
                    (int)(ao.getTextColor().getBlue()*255));
            answerButtons[i].setStyle(bgColor + " -fx-min-width: 80; -fx-min-height: 80; -fx-background-radius: 8;");
        }
    }

    private Tab createHelpTab() {
        Tab tab = new Tab("❓ Справка");
        WebView webView = new WebView();
        String html = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <style>
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                margin: 0;
                padding: 30px;
                background: #1C1C1E;
                color: #E5E5E5;
                line-height: 1.6;
            }
            h1 {
                color: #4F8EF7;
                border-bottom: 2px solid #4F8EF7;
                padding-bottom: 10px;
            }
            h2 {
                color: #34C759;
                margin-top: 30px;
            }
            h3 {
                color: #FF9500;
                margin-top: 20px;
            }
            .note {
                background: #2C2C2E;
                border-left: 4px solid #4F8EF7;
                padding: 10px 20px;
                margin: 20px 0;
                border-radius: 8px;
            }
            .warning {
                background: #3A2A1A;
                border-left: 4px solid #FF9500;
                padding: 10px 20px;
                margin: 20px 0;
                border-radius: 8px;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                margin: 20px 0;
                background: #2C2C2E;
                border-radius: 8px;
                overflow: hidden;
            }
            th, td {
                padding: 10px 15px;
                text-align: left;
                border-bottom: 1px solid #3A3A3C;
            }
            th {
                background: #3A3A3C;
                color: #FFFFFF;
            }
            code {
                background: #3A3A3C;
                padding: 2px 6px;
                border-radius: 4px;
                font-family: monospace;
                font-size: 0.9em;
            }
            ul, ol {
                margin: 10px 0;
                padding-left: 25px;
            }
            li {
                margin: 5px 0;
            }
            hr {
                border-color: #3A3A3C;
            }
            .key {
                display: inline-block;
                background: #3A3A3C;
                border: 1px solid #555;
                border-radius: 6px;
                padding: 2px 8px;
                font-family: monospace;
                font-weight: bold;
                margin: 0 2px;
            }
        </style>
    </head>
    <body>
        <h1>Руководство по тесту Струпа</h1>
        <p>Данное приложение реализует классический <strong>тест Струпа (Stroop test)</strong> для оценки когнитивной гибкости, избирательности внимания и устойчивости к интерференции. Эксперимент синхронизирован с ЭЭГ-записью и генерирует временные метки для последующего анализа.</p>
        
        <div class="note">
            <strong>Перед началом:</strong> Убедитесь, что испытуемый удобно сидит перед экраном, готов быстро и точно отвечать. Ответы даются <strong>нажатием на экранные кнопки</strong> (мышкой или тачпадом). Время реакции фиксируется автоматически.
        </div>
        
        <h2>Структура теста</h2>
        <p>Тест состоит из трёх последовательных частей. Каждая часть имеет свою инструкцию, которая появляется перед стартом. После ознакомления с инструкцией нажмите <strong>«Продолжить»</strong> – начнётся предъявление стимулов и запустится таймер части.</p>
        
        <table>
            <tr><th>Часть</th><th>Название</th><th>Задача</th><th>Что показывается</th></tr>
            <tr><td><strong>T1</strong></td><td>Названия цветов чёрным цветом</td><td>Выбрать <strong>смысл слова</strong> (название цвета, которое написано)</td><td>Чёрные слова: «КРАСНЫЙ», «СИНИЙ», «ЗЕЛЁНЫЙ», «ЖЁЛТЫЙ»</td></tr>
            <tr><td><strong>T2</strong></td><td>Цветные прямоугольники</td><td>Выбрать <strong>цвет фона</strong> прямоугольника</td><td>Цветные прямоугольники без текста</td></tr>
            <tr><td><strong>T3</strong></td><td>Конфликтные слова</td><td>Выбрать <strong>цвет чернил</strong> (цвет, которым напечатано слово), <em>игнорируя смысл</em></td><td>Слова напечатаны несовпадающим цветом, например «КРАСНЫЙ» зелёным цветом</td></tr>
        </table>
        
        <h2>Управление</h2>
        <ul>
            <li><strong>Клик по кнопке ответа</strong> – выбор варианта (четыре цветные кнопки, порядок и цвет текста на кнопке случайны для каждого стимула).</li>
            <li><strong>Кнопка «▶ Запустить»</strong> – начать эксперимент (после настройки параметров).</li>
            <li><strong>Кнопка «⏹ Остановить»</strong> – прервать эксперимент досрочно (данные не сохраняются).</li>
            <li><strong>Кнопка «Продолжить»</strong> – появляется после каждой инструкции; переходит к выполнению части.</li>
            <li><strong>Вкладка «Настройки»</strong> – позволяет изменить количество стимулов на часть, интервалы, режим работы (эмуляция / реальный COM-порт) и параметры джиттера.</li>
            <li><strong>Вкладка «Анализ»</strong> – после эксперимента показывает статистику задержек маркеров, журнал событий, возможность экспорта CSV.</li>
        </ul>
        
        <h2>Что измеряется?</h2>
        <ul>
            <li><strong>Время реакции (RT)</strong> – от момента появления стимула до клика на кнопку.</li>
            <li><strong>Ошибки</strong> – неправильный выбор цвета.</li>
            <li><strong>Эффект интерференции</strong> – увеличение времени реакции в части T3 по сравнению с T1/T2.</li>
            <li><strong>Общая длительность каждой части</strong> – отображается на таймере справа.</li>
        </ul>
        
        <h2>Интерпретация результатов</h2>
        <p>После завершения эксперимента результаты логируются в CSV-файл (папка по умолчанию <code>~/EEGLogs</code>). Там же сохраняется отчёт с детальной статистикой. Вы можете импортировать CSV в любую программу для анализа (Excel, SPSS, R).</p>
        
        <h2>Дополнительные показатели</h2>
                    <p>На основе среднего времени реакции (T1, T2, T3) вычисляются три важных коэффициента:</p>
                    <ul>
                        <li><strong>ID = T3 - T2</strong> – показатель ригидности/гибкости контроля.\s
                        Чем больше разница, тем выраженнее эффект интерференции и ригидность (узость, жесткость) познавательного контроля.\s
                        Интерференция возникает из-за конфликта между вербальными функциями (чтение слова) и сенсорно-перцептивными (восприятие цвета).\s
                        Низкая интерференция говорит о способности тормозить более сильные вербальные функции ради восприятия цвета.</li>
                       \s
                        <li><strong>IR = T2 / T3</strong> – относительный показатель интерференции.\s
                        Обратно пропорционален интерференции: чем выше значение (ближе к 1 или больше), тем меньше влияние интерференции.\s
                        Характеризует гибкий контроль и автоматизацию познавательных функций.</li>
                       \s
                        <li><strong>IK = T2 / T1</strong> – показатель вербальности.\s
                        Соотношение времени обработки цвета и слов.\s
                        Высокие значения (больше 1) свидетельствуют о преобладании словесного способа переработки информации,\s
                        низкие (ближе к 0) – сенсорно-перцептивного.</li>
                    </ul>
                    <p>Таким образом, один полюс когнитивного стиля соответствует гибкому контролю и сильной автоматизации функций, другой – ригидному (жесткому) контролю и слабой автоматизации.</p>
        
        <h2>Технические особенности</h2>
        <ul>
            <li>Приложение генерирует <strong>маркеры синхронизации</strong> для каждого предъявления стимула (номер маркера соответствует коду стимула). Маркеры могут отправляться через COM-порт в реальном режиме или эмулироваться с настраиваемым джиттером для отладки.</li>
            <li>Все события (появление стимула, ответ, скрытие) логируются с наносекундной точностью (<code>System.nanoTime()</code>).</li>
            <li>Для задачи Струпа используются 4 базовых цвета: КРАСНЫЙ, СИНИЙ, ЗЕЛЁНЫЙ, ЖЁЛТЫЙ.</li>
            <li>Варианты ответов (кнопки) генерируются случайным образом для каждого стимула – как порядок, так и цвет текста на кнопке, что усложняет автоматическое реагирование и соответствует современным экспериментальным парадигмам.</li>
        </ul>
        
        <h2>Информация о разработчике</h2>
        <p>Разработано <strong>Глушко Ильёй Игоревичем</strong>, группа 23201, НГУ ФИТ, в рамках учебной практики на базе ФГБНУ НИИ Нейронаук и медицины (лаборатория дифференциальной психофизиологии).<br>
        Руководитель практики: проф. Савостьянов Александр Николаевич.<br>
        Версия приложения: 2.0 (ЭЭГ Experiment Suite).</p>
        
        <div class="warning">
            <strong>Важно:</strong> При проведении реального ЭЭГ-эксперимента перед запуском убедитесь, что вкладка «Настройки» переключена в <strong>режим реального оборудования</strong>, указан правильный COM-порт и скорость, а также корректно задан идентификатор эксперимента. Результаты не должны использоваться в медицинских целях без дополнительной валидации.
        </div>
        
        <hr>
    </body>
    </html>
    """;
        webView.getEngine().loadContent(html);
        tab.setContent(webView);
        return tab;
    }

    private void onContinue() {
        if (manager.getState() == ExperimentManager.State.RUNNING) {
            manager.continueToPart();
            continueButton.setVisible(false);
        }
    }

    private void onAnswerChosen(int idx) {
        if (manager.getState() == ExperimentManager.State.RUNNING && currentAnswerOptions != null && idx < currentAnswerOptions.size()) {
            AnswerOption chosen = currentAnswerOptions.get(idx);
            manager.recordAnswer(chosen);
            // Можно тут же визуально подсветить нажатие, но не обязательно
        }
    }

    private void onStimulusPresented(StroopStimulus stimulus) {
        // Отображение стимула (без изменений)
        if (stimulus.isRectangle()) {
            stimulusDisplay.setText("");
            String colorStyle = String.format("-fx-background-color: rgb(%d,%d,%d);",
                    (int)(stimulus.getTextColor().getRed()*255),
                    (int)(stimulus.getTextColor().getGreen()*255),
                    (int)(stimulus.getTextColor().getBlue()*255));
            stimulusDisplay.setStyle(colorStyle + " -fx-background-radius: 20;");
            stimulusDisplay.setPrefSize(200, 400);
        } else {
            stimulusDisplay.setText(stimulus.getText());
            String cssColor = String.format("rgb(%d,%d,%d)",
                    (int)(stimulus.getTextColor().getRed()*255),
                    (int)(stimulus.getTextColor().getGreen()*255),
                    (int)(stimulus.getTextColor().getBlue()*255));
            stimulusDisplay.setStyle("-fx-text-fill: " + cssColor + ";");
        }

        // Выбор типа вариантов ответа по части
        StroopPart part = stimulus.getPart();
        if (part == StroopPart.T1) {
            generateColorAnswerOptions(stimulus);      // цветные прямоугольники
        } else if (part == StroopPart.T2) {
            generatePlainTextAnswerOptions(stimulus);  // обычные текстовые кнопки
        } else { // T3
            generateTextAnswerOptions(stimulus);       // текстовые кнопки с рандомным цветом шрифта
        }
    }

    private void generateTextAnswerOptions(StroopStimulus stimulus) {
        List<String> allColors = List.of("КРАСНЫЙ", "СИНИЙ", "ЗЕЛЁНЫЙ", "ЖЁЛТЫЙ");
        List<String> options = new ArrayList<>(allColors);
        Collections.shuffle(options);
        List<AnswerOption> opts = new ArrayList<>();
        Random r = new Random();
        for (String opt : options) {
            Color textColor = StroopStimulusGenerator.COLORS[r.nextInt(StroopStimulusGenerator.COLORS.length)];
            opts.add(new AnswerOption(opt, textColor));
        }
        currentAnswerOptions = opts;
        for (int i = 0; i < answerButtons.length; i++) {
            AnswerOption ao = opts.get(i);
            answerButtons[i].setText(ao.getDisplayText());
            String cssColor = String.format("rgb(%d,%d,%d)",
                    (int)(ao.getTextColor().getRed()*255),
                    (int)(ao.getTextColor().getGreen()*255),
                    (int)(ao.getTextColor().getBlue()*255));
            answerButtons[i].setStyle("-fx-text-fill: " + cssColor + "; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-color: #3A3A3C; -fx-background-radius: 8;");
        }
    }

    private void onPartInstruction(String instructionText) {
        // Показать диалог с инструкцией и кнопкой "Продолжить"
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Инструкция");
        alert.setHeaderText(instructionText);
        alert.setContentText("Нажмите OK, когда будете готовы.");
        alert.showAndWait().ifPresent(response -> manager.continueToPart());
    }

    // Обновление таймера части
    private void onPartTimerUpdate(long elapsedMs) {
        timerLabel.setText(String.format("Таймер части: %.1f сек.", elapsedMs / 1000.0));
    }
    // ── SETTINGS TAB ────────────────────────────────────────────────────────

    private Tab createSettingsTab() {
        Tab tab = new Tab("⚙  Настройки");
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + BG_DARK + "; -fx-border-color: transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + BG_DARK + ";");

        // Параметры теста (только количество стимулов)
        VBox testCard = createCard("🎯 Параметры теста");
        stimulusCountSpinner = createIntSpinner(1, 100, config.getStimulusCount());
        addFormRow(testCard, "Количество стимулов на каждую часть:", stimulusCountSpinner);

        // Задержка после ответа (перерыв между стимулами)
        Spinner<Integer> postDelaySpinner = createIntSpinner(200, 2000, 500);
        addFormRow(testCard, "Задержка после ответа (мс):", postDelaySpinner);

        // Режим работы
        VBox modeCard = createCard("🔧 Режим работы");
        modeToggle = new ToggleGroup();
        emulationRadio = createRadio("Эмуляция (для тестирования без ЭЭГ-оборудования)", modeToggle);
        realRadio = createRadio("Реальный (отправка меток на ЭЭГ-усилитель через порт)", modeToggle);
        emulationRadio.setSelected(config.isEmulationMode());
        realRadio.setSelected(!config.isEmulationMode());
        modeCard.getChildren().addAll(emulationRadio, realRadio);

        // Эмуляция джиттера
        emulationParamsBox = createCard("⚡ Параметры эмуляции джиттера");
        baseDelaySpinner = createDoubleSpinner(0, 100, config.getEmulationBaseDelayMs(), 0.5);
        jitterStdSpinner = createDoubleSpinner(0, 50, config.getEmulationJitterStdMs(), 0.1);
        outlierProbSpinner = createDoubleSpinner(0, 1, config.getEmulationOutlierProbability(), 0.01);
        outlierMaxSpinner = createDoubleSpinner(5, 500, config.getEmulationOutlierMaxMs(), 5);
        addFormRow(emulationParamsBox, "Базовая задержка (мс):", baseDelaySpinner);
        addFormRow(emulationParamsBox, "СКО джиттера (мс):", jitterStdSpinner);
        addFormRow(emulationParamsBox, "Вероятность выброса (0–1):", outlierProbSpinner);
        addFormRow(emulationParamsBox, "Макс. задержка выброса (мс):", outlierMaxSpinner);
        emulationParamsBox.setVisible(config.isEmulationMode());
        emulationParamsBox.setManaged(config.isEmulationMode());

        // Реальный порт
        VBox realCard = createCard("🔌 Параметры порта");
        portField = createTextField(config.getPortName());
        addFormRow(realCard, "COM-порт:", portField);

        // Логирование
        VBox sessionCard = createCard("📁 Сессия и логирование");
        experimentIdField = createTextField(config.getExperimentId());
        logDirField = createTextField(config.getLogDirectory());
        Button browseBtn = new Button("Обзор...");
        browseBtn.setStyle(secondaryButtonStyle());
        browseBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle("Выбор папки для логов");
            File dir = dc.showDialog(null);
            if (dir != null) logDirField.setText(dir.getAbsolutePath());
        });
        HBox logDirRow = new HBox(8, logDirField, browseBtn);
        HBox.setHgrow(logDirField, Priority.ALWAYS);
        addFormRow(sessionCard, "Идентификатор эксперимента:", experimentIdField);
        addFormRow(sessionCard, "Папка для логов:", logDirRow);

        // Кнопка применения
        Button applyBtn = new Button("✓  Применить настройки");
        applyBtn.setStyle(primaryButtonStyle(SUCCESS_COLOR));
        applyBtn.setPrefHeight(44);
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.setOnAction(e -> applySettings(postDelaySpinner));

        // Видимость параметров эмуляции
        emulationRadio.setOnAction(e -> {
            emulationParamsBox.setVisible(true);
            emulationParamsBox.setManaged(true);
        });
        realRadio.setOnAction(e -> {
            emulationParamsBox.setVisible(false);
            emulationParamsBox.setManaged(false);
        });

        content.getChildren().addAll(testCard, modeCard, emulationParamsBox, realCard, sessionCard, applyBtn);
        scroll.setContent(content);
        tab.setContent(scroll);
        return tab;
    }

    // ── ANALYSIS TAB ────────────────────────────────────────────────────────

//    private Tab createAnalysisTab() {
//        Tab tab = new Tab("📈 Анализ");
//
//        VBox content = new VBox(16);
//        content.setPadding(new Insets(24));
//        content.setStyle("-fx-background-color: " + BG_DARK + ";");
//
//        // Toolbar
//        HBox toolbar = new HBox(12);
//        toolbar.setAlignment(Pos.CENTER_LEFT);
//        Button refreshBtn = createToolbarButton("↻  Обновить");
//        Button exportCsvBtn = createToolbarButton("📄  Экспорт CSV");
//        Button exportReportBtn = createToolbarButton("📊  Экспорт отчёта");
//        refreshBtn.setOnAction(e -> refreshAnalysis());
//        exportCsvBtn.setOnAction(e -> exportCsv());
//        exportReportBtn.setOnAction(e -> exportAnalysisReport());
//        toolbar.getChildren().addAll(refreshBtn, exportCsvBtn, exportReportBtn);
//
//        // Stats row
//        HBox statsRow = new HBox(12);
//        statsRow.setFillHeight(true);
//        VBox statsCard = createCard("📐 Статистика задержек маркеров");
//        statsCard.setPrefWidth(300);
//        statCount = createStatValue(statsCard, "Маркеров");
//        statMean  = createStatValue(statsCard, "Среднее (мс)");
//        statStd   = createStatValue(statsCard, "СКО (мс)");
//        statMin   = createStatValue(statsCard, "Min (мс)");
//        statMax   = createStatValue(statsCard, "Max (мс)");
//        statP95   = createStatValue(statsCard, "P95 (мс)");
//
//        // Histogram
//        CategoryAxis xAxis = new CategoryAxis();
//        NumberAxis yAxis = new NumberAxis();
//        xAxis.setLabel("Задержка (мс)");
//        xAxis.setStyle("-fx-tick-label-fill: " + TEXT_SECONDARY + ";");
//        yAxis.setLabel("Количество");
//        yAxis.setStyle("-fx-tick-label-fill: " + TEXT_SECONDARY + ";");
//        jitterChart = new BarChart<>(xAxis, yAxis);
//        jitterChart.setTitle("Распределение джиттера");
//        jitterChart.setAnimated(true);
//        jitterChart.setLegendVisible(false);
//        jitterChart.setStyle("-fx-background-color: transparent;");
//        jitterChart.setBarGap(2);
//        jitterChart.setCategoryGap(4);
//        HBox.setHgrow(jitterChart, Priority.ALWAYS);
//        statsRow.getChildren().addAll(statsCard, jitterChart);
//
//        // Log table
//        VBox tableCard = createCard("📋 Журнал событий");
//        VBox.setVgrow(tableCard, Priority.ALWAYS);
//        logData = FXCollections.observableArrayList();
//        logTable = createLogTable();
//        logTable.setItems(logData);
//        VBox.setVgrow(logTable, Priority.ALWAYS);
//        tableCard.getChildren().add(logTable);
//
//        VBox.setVgrow(statsRow, Priority.NEVER);
//        VBox.setVgrow(tableCard, Priority.ALWAYS);
//        content.getChildren().addAll(toolbar, statsRow, tableCard);
//
//        ScrollPane scroll = new ScrollPane(content);
//        scroll.setFitToWidth(true);
//        scroll.setStyle("-fx-background-color: " + BG_DARK + "; -fx-border-color: transparent;");
//        tab.setContent(scroll);
//        return tab;
//    }

    private Tab createAnalysisTab() {
        Tab tab = new Tab("📈 Анализ");

        TabPane innerTabPane = new TabPane();
        innerTabPane.setStyle("-fx-background-color: transparent;");

        // --- Вкладка "Результаты теста" ---
        Tab resultsTab = new Tab("📊 Результаты теста");
        VBox resultsContent = new VBox(15);
        resultsContent.setPadding(new Insets(20));
        resultsContent.setStyle("-fx-background-color: " + BG_DARK + ";");

        // Таблица статистики по частям
        TableView<PartStatsRow> statsTable = new TableView<>();
        statsTable.setStyle("-fx-background-color: " + BG_CARD + ";");
        ObservableList<PartStatsRow> statsData = FXCollections.observableArrayList();

        TableColumn<PartStatsRow, String> colPart = new TableColumn<>("Часть");
        colPart.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().partName));

        TableColumn<PartStatsRow, String> colMean = new TableColumn<>("Ср. время (мс)");
        colMean.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().meanRT));

        TableColumn<PartStatsRow, String> colMedian = new TableColumn<>("Медиана (мс)");
        colMedian.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().medianRT));

        TableColumn<PartStatsRow, String> colStd = new TableColumn<>("СКО (мс)");
        colStd.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().stdRT));

        TableColumn<PartStatsRow, String> colErrors = new TableColumn<>("Ошибки");
        colErrors.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().errors));

        TableColumn<PartStatsRow, String> colErrorRate = new TableColumn<>("% ошибок");
        colErrorRate.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().errorRate));


        statsTable.getColumns().addAll(colPart, colMean, colMedian, colStd, colErrors, colErrorRate);
        statsTable.setItems(statsData);
        statsTable.setPrefHeight(200);
        Label tableNote = new Label("Ср. время – среднее арифметическое; Медиана – более устойчивая оценка; СКО – разброс; Ошибки – количество неверных ответов / всего стимулов.");
        tableNote.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");
        resultsContent.getChildren().add(tableNote);

        // Интерференция и общее время
        Label interferenceLabel = new Label("Эффект интерференции (T3 - T1): -- мс");
        interferenceLabel.setStyle("-fx-text-fill: " + ACCENT_COLOR + "; -fx-font-weight: bold;");
        Label totalTimeLabel = new Label("Общее время эксперимента: -- сек");
        Label durationsLabel = new Label("Длительность частей: T1: --, T2: --, T3: -- сек");

        // Кнопка обновления
        Button refreshStatsBtn = new Button("↻ Обновить статистику");
        refreshStatsBtn.setStyle(secondaryButtonStyle());
        refreshStatsBtn.setOnAction(e -> refreshTestStatistics(statsData, interferenceLabel, totalTimeLabel, durationsLabel));

        // Нормы и теория
//        TextArea normsArea = new TextArea(StroopStatistics.getAgeNorms());
//        normsArea.setEditable(false);
//        normsArea.setWrapText(true);
//        normsArea.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");
//        normsArea.setPrefHeight(150);

        idLabel = new Label("ID (T3 - T2): -- мс");
        irLabel = new Label("IR (T2 / T3): --");
        ikLabel = new Label("IK (T2 / T1): --");
        idLabel.setStyle("-fx-text-fill: " + ACCENT_COLOR + "; -fx-font-weight: bold;");
        irLabel.setStyle("-fx-text-fill: " + ACCENT_COLOR + "; -fx-font-weight: bold;");
        ikLabel.setStyle("-fx-text-fill: " + ACCENT_COLOR + "; -fx-font-weight: bold;");

        resultsContent.getChildren().addAll(statsTable, interferenceLabel, totalTimeLabel, durationsLabel,
                refreshStatsBtn, idLabel, irLabel, ikLabel);

//        resultsContent.getChildren().addAll(statsTable, interferenceLabel, totalTimeLabel, durationsLabel, refreshStatsBtn, new Label("Нормы для разных возрастов:"), normsArea);
        resultsTab.setContent(resultsContent);

        // --- Вкладка "Статистика маркеров" (старая) ---
        Tab markersTab = new Tab("🔧 Статистика маркеров");
        VBox markersContent = new VBox(16);
        markersContent.setPadding(new Insets(20));
        markersContent.setStyle("-fx-background-color: " + BG_DARK + ";");

        // Toolbar
        HBox toolbar = new HBox(12);
        Button refreshBtn = createToolbarButton("↻  Обновить маркеры");
        Button exportCsvBtn = createToolbarButton("📄  Экспорт CSV");
        Button exportReportBtn = createToolbarButton("📊  Экспорт отчёта");
        refreshBtn.setOnAction(e -> refreshMarkersAnalysis());
        exportCsvBtn.setOnAction(e -> exportCsv());
        exportReportBtn.setOnAction(e -> exportAnalysisReport());
        toolbar.getChildren().addAll(refreshBtn, exportCsvBtn, exportReportBtn);

        // Stats row
        HBox statsRow = new HBox(12);
        VBox statsCard = createCard("📐 Статистика задержек маркеров");
        statsCard.setPrefWidth(300);
        statCount = createStatValue(statsCard, "Маркеров");
        statMean  = createStatValue(statsCard, "Среднее (мс)");
        statStd   = createStatValue(statsCard, "СКО (мс)");
        statMin   = createStatValue(statsCard, "Min (мс)");
        statMax   = createStatValue(statsCard, "Max (мс)");
        statP95   = createStatValue(statsCard, "P95 (мс)");

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Задержка (мс)");
        yAxis.setLabel("Количество");
        jitterChart = new BarChart<>(xAxis, yAxis);
        jitterChart.setTitle("Распределение джиттера");
        jitterChart.setAnimated(false);
        jitterChart.setLegendVisible(false);
        HBox.setHgrow(jitterChart, Priority.ALWAYS);
        statsRow.getChildren().addAll(statsCard, jitterChart);

        // Log table
        VBox tableCard = createCard("📋 Журнал событий");
        VBox.setVgrow(tableCard, Priority.ALWAYS);
        logData = FXCollections.observableArrayList();
        logTable = createLogTable();
        logTable.setItems(logData);
        tableCard.getChildren().add(logTable);

        markersContent.getChildren().addAll(toolbar, statsRow, tableCard);
        markersTab.setContent(markersContent);

        innerTabPane.getTabs().addAll(resultsTab, markersTab);
        tab.setContent(innerTabPane);
        return tab;
    }

    private static class PartStatsRow {
        String partName, meanRT, medianRT, stdRT, errors, errorRate;
        PartStatsRow(String name, double mean, double median, double std, int errCount, int total, double errRate) {
            this.partName = name;
            this.meanRT = String.format("%.1f", mean);
            this.medianRT = String.format("%.1f", median);
            this.stdRT = String.format("%.1f", std);
            this.errors = errCount + " / " + total;
            this.errorRate = String.format("%.1f%%", errRate * 100);
        }
    }

    private void refreshTestStatistics(ObservableList<PartStatsRow> statsData, Label interferenceLabel, Label totalTimeLabel, Label durationsLabel) {
        Map<String, StroopStatistics.PartStats> stats = StroopStatistics.computeFromLog(logger);
        statsData.clear();
        for (StroopStatistics.PartStats ps : stats.values()) {
            statsData.add(new PartStatsRow(ps.partName,
                    ps.getMeanRT(), ps.getMedianRT(), ps.getStdRT(),
                    ps.errorCount, ps.getTotalTrials(), ps.getErrorRate()));
        }

        StroopStatistics.PartStats t1 = stats.get("T1");
        StroopStatistics.PartStats t2 = stats.get("T2");
        StroopStatistics.PartStats t3 = stats.get("T3");

        if (t1 != null && t3 != null) {
            double interference = t3.getMeanRT() - t1.getMeanRT();
            interferenceLabel.setText(String.format("Эффект интерференции (T3 - T1): %.1f мс", interference));
        } else {
            interferenceLabel.setText("Эффект интерференции: данные неполные");
        }

        if (t2 != null && t3 != null) {
            double id = StroopStatistics.computeID(t2.getMeanRT(), t3.getMeanRT());
            double ir = StroopStatistics.computeIR(t2.getMeanRT(), t3.getMeanRT());
            idLabel.setText(String.format("ID (T3 - T2): %.1f мс — ригидность контроля (чем выше, тем больше интерференция)", id));
            irLabel.setText(String.format("IR (T2 / T3): %.3f — относительный показатель интерференции (чем выше, тем меньше влияние)", ir));
        } else {
            idLabel.setText("ID (T3 - T2): данные неполные");
            irLabel.setText("IR (T2 / T3): данные неполные");
        }

        if (t1 != null && t2 != null) {
            double ik = StroopStatistics.computeIK(t1.getMeanRT(), t2.getMeanRT());
            ikLabel.setText(String.format("IK (T2 / T1): %.3f — вербальность (>1 – словесный тип, <1 – сенсорно-перцептивный)", ik));
        } else {
            ikLabel.setText("IK (T2 / T1): данные неполные");
        }

        long dur1 = manager.getPart1Duration();
        long dur2 = manager.getPart2Duration();
        long dur3 = manager.getPart3Duration();
        durationsLabel.setText(String.format("Длительность частей: T1: %.1f сек, T2: %.1f сек, T3: %.1f сек",
                dur1 / 1000.0, dur2 / 1000.0, dur3 / 1000.0));
        long total = dur1 + dur2 + dur3;
        totalTimeLabel.setText(String.format("Общее время эксперимента: %.1f сек", total / 1000.0));
    }

    @SuppressWarnings("unchecked")
    private TableView<EventLogger.LogEntry> createLogTable() {
        TableView<EventLogger.LogEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String tableStyle = "-fx-background-color: " + BG_CARD + "; -fx-background-radius: 8; " +
                "-fx-border-color: #3A3A3C; -fx-border-radius: 8; -fx-control-inner-background: " + BG_CARD + "; " +
                "-fx-table-cell-border-color: #3A3A3C;";
        table.setStyle(tableStyle);
        table.setPrefHeight(300);

        TableColumn<EventLogger.LogEntry, String> colTime = new TableColumn<>("Время (ns)");
        colTime.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().nanoTime)));
        colTime.setPrefWidth(160);

        TableColumn<EventLogger.LogEntry, String> colType = new TableColumn<>("Тип события");
        colType.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().type.toString()));
        colType.setPrefWidth(180);

        TableColumn<EventLogger.LogEntry, String> colMsg = new TableColumn<>("Сообщение");
        colMsg.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().message));
        colMsg.setPrefWidth(400);

        TableColumn<EventLogger.LogEntry, String> colDelay = new TableColumn<>("Задержка (мс)");
        colDelay.setCellValueFactory(p -> new SimpleStringProperty(
                String.format("%.3f", p.getValue().delayMs)));
        colDelay.setPrefWidth(120);

        table.getColumns().addAll(colTime, colType, colMsg, colDelay);
        return table;
    }

    // ── STATUS BAR ──────────────────────────────────────────────────────────

    private HBox createStatusBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 24, 8, 24));
        bar.setStyle("-fx-background-color: " + BG_CARD + "; -fx-border-color: #3A3A3C; -fx-border-width: 1 0 0 0;");
        Label versionLabel = new Label("v2.0 — Эксплуатационная практика 2026 | Глушко И.И. гр. 23201");
        versionLabel.setTextFill(Color.web(TEXT_SECONDARY));
        versionLabel.setFont(Font.font("System", 11));
        bar.getChildren().add(versionLabel);
        return bar;
    }

    // ── EVENT HANDLERS ───────────────────────────────────────────────────────

    private void onStartStop() {
        if (manager.getState() == ExperimentManager.State.RUNNING) {
            manager.stopExperiment();
            startStopButton.setText("▶  Запустить");
            startStopButton.setStyle(primaryButtonStyle(ACCENT_COLOR));
            statusIndicator.setFill(Color.web("#8E8E93"));
            stimulusDisplay.setText("Остановлено");
            stimulusDisplay.setTextFill(Color.web(TEXT_SECONDARY));
        } else {
            manager.prepare();
            manager.startExperiment();
            startStopButton.setText("⏹  Остановить");
            startStopButton.setStyle(primaryButtonStyle(DANGER_COLOR));
            statusIndicator.setFill(Color.web(SUCCESS_COLOR));
            progressBar.setProgress(0);
            progressLabel.setText("0 / " + config.getStimulusCount());
        }
    }

//    private void onStimulusPresented(VisualStimulus stimulus) {
//        stimulusDisplay.setText(stimulus.getText());
//        javafx.scene.paint.Color c = stimulus.getTextColor();
//        String cssColor = String.format("rgb(%d,%d,%d)",
//                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
//        stimulusDisplay.setStyle("-fx-text-fill: " + cssColor + ";");
//        markerCountLabel.setText(String.valueOf(manager.getTimestampModule().getMarkersSent()));
//        meanDelayLabel.setText(String.format("%.2f мс", manager.getTimestampModule().getMeanDelayMs()));
//    }

    private void onStimulusHidden(String ignored) {
        stimulusDisplay.setText("+");
        stimulusDisplay.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-background-color: transparent;");
    }

    private void onExperimentFinished() {
        startStopButton.setText("▶  Запустить");
        startStopButton.setStyle(primaryButtonStyle(ACCENT_COLOR));
        statusIndicator.setFill(Color.web(ACCENT_COLOR));
        stimulusDisplay.setText("Эксперимент завершён");
        stimulusDisplay.setStyle("-fx-text-fill: " + SUCCESS_COLOR + ";");
        showInfo("Эксперимент завершён", "Данные сохранены в " + config.getLogDirectory());
    }

    private void onStatusChanged(String status) {
        statusLabel.setText(status);
    }

    private void onProgressChanged(double progress) {
        progressBar.setProgress(progress);
        int total = config.getStimulusCount();
        int done = (int)(progress * total);
        progressLabel.setText(done + " / " + total);
    }

    private void applySettings(Spinner<Integer> postDelaySpinner) {
        config.setStimulusCount(stimulusCountSpinner.getValue());
        config.setEmulationMode(emulationRadio.isSelected());
        config.setEmulationBaseDelayMs(baseDelaySpinner.getValue());
        config.setEmulationJitterStdMs(jitterStdSpinner.getValue());
        config.setEmulationOutlierProbability(outlierProbSpinner.getValue());
        config.setEmulationOutlierMaxMs(outlierMaxSpinner.getValue());
        config.setPortName(portField.getText());
        config.setExperimentId(experimentIdField.getText());
        config.setLogDirectory(logDirField.getText());

        // Если вы добавили поле postAnswerDelayMs в ExperimentConfig, то:
         config.setPostAnswerDelayMs(postDelaySpinner.getValue());
        // А пока просто сохраним в отдельную переменную (например, через менеджер)
        // Но для простоты можно пока не использовать, оставить фиксированную задержку 500 мс

        boolean emu = config.isEmulationMode();
        modeLabel.setText(emu ? "⚡ ЭМУЛЯЦИЯ" : "🔌 РЕАЛЬНЫЙ");
        String modeStyle = "-fx-background-color: " + (emu ? WARNING_COLOR : SUCCESS_COLOR) +
                "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; " +
                "-fx-padding: 4 10 4 10; -fx-background-radius: 8;";
        modeLabel.setStyle(modeStyle);

        showInfo("Настройки применены", "Параметры эксперимента обновлены.");
    }

    private void refreshMarkersAnalysis() {
        logData.setAll(logger.getEntries());
        JitterAnalyzer.Stats stats = manager.getAnalysisStats();
        statCount.setText(String.valueOf(stats.count));
        statMean.setText(stats.count > 0 ? String.format("%.3f", stats.mean) : "—");
        statStd.setText(stats.count > 0 ? String.format("%.3f", stats.std) : "—");
        statMin.setText(stats.count > 0 ? String.format("%.3f", stats.min) : "—");
        statMax.setText(stats.count > 0 ? String.format("%.3f", stats.max) : "—");
        statP95.setText(stats.count > 0 ? String.format("%.3f", stats.p95) : "—");

        // Update histogram
        jitterChart.getData().clear();
        if (stats.count > 0) {
            Map<String, Long> hist = JitterAnalyzer.buildHistogram(stats, 12);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Задержка");
            hist.forEach((bin, count) ->
                    series.getData().add(new XYChart.Data<>(bin, count)));
            jitterChart.getData().add(series);
            // Style bars
            Platform.runLater(() -> jitterChart.lookupAll(".bar").forEach(node ->
                    node.setStyle("-fx-bar-fill: " + ACCENT_COLOR + "; -fx-background-radius: 3;")));
        }
    }

    private void exportCsv() {
        try {
            String path = logger.exportToCsv();
            showInfo("Экспорт завершён", "CSV сохранён:\n" + path);
        } catch (IOException e) {
            showError("Ошибка экспорта", e.getMessage());
        }
    }

    private void exportAnalysisReport() {
        JitterAnalyzer.Stats stats = manager.getAnalysisStats();
        try {
            String path = JitterAnalyzer.exportReport(stats,
                    config.getLogDirectory(), config.getExperimentId() + "_analysis");
            showInfo("Отчёт сохранён", path);
        } catch (IOException e) {
            showError("Ошибка экспорта", e.getMessage());
        }
    }

    // ── UI HELPERS ───────────────────────────────────────────────────────────

    private VBox createCard(String title) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        String cardStyle = "-fx-background-color: " + BG_CARD + "; -fx-background-radius: 12; " +
                "-fx-border-color: #3A3A3C; -fx-border-radius: 12; -fx-border-width: 1;";
        card.setStyle(cardStyle);
        if (title != null && !title.isEmpty()) {
            Label titleLabel = new Label(title);
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
            titleLabel.setTextFill(Color.WHITE);
            card.getChildren().add(titleLabel);
            Separator sep = new Separator();
            sep.setStyle("-fx-background-color: #3A3A3C;");
            card.getChildren().add(sep);
        }
        return card;
    }

    private Label createStatRow(VBox card, String label, String defaultVal) {
        HBox row = new HBox();
        Label lbl = new Label(label);
        lbl.setTextFill(Color.web(TEXT_SECONDARY));
        lbl.setFont(Font.font("System", 12));
        Label val = new Label(defaultVal);
        val.setFont(Font.font("System", FontWeight.BOLD, 13));
        val.setTextFill(Color.WHITE);
        row.getChildren().addAll(lbl, createSpacer(), val);
        card.getChildren().add(row);
        return val;
    }

    private Label createStatValue(VBox card, String label) {
        VBox row = new VBox(2);
        Label lbl = new Label(label);
        lbl.setFont(Font.font("System", 11));
        lbl.setTextFill(Color.web(TEXT_SECONDARY));
        Label val = new Label("—");
        val.setFont(Font.font("System", FontWeight.BOLD, 20));
        val.setTextFill(Color.web(ACCENT_COLOR));
        row.getChildren().addAll(lbl, val);
        card.getChildren().add(row);
        return val;
    }

    private void addFormRow(VBox parent, String label, javafx.scene.Node control) {
        VBox row = new VBox(4);
        Label lbl = new Label(label);
        lbl.setFont(Font.font("System", 12));
        lbl.setTextFill(Color.web(TEXT_SECONDARY));
        if (control instanceof Control) ((Control) control).setMaxWidth(Double.MAX_VALUE);
        row.getChildren().addAll(lbl, control);
        parent.getChildren().add(row);
    }

    private Spinner<Integer> createIntSpinner(int min, int max, int init) {
        Spinner<Integer> s = new Spinner<>(min, max, init, 1);
        s.setEditable(true);
        s.setStyle(inputStyle());
        return s;
    }

    private Spinner<Double> createDoubleSpinner(double min, double max, double init, double step) {
        Spinner<Double> s = new Spinner<>(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, init, step));
        s.setEditable(true);
        s.setStyle(inputStyle());
        return s;
    }

    private TextField createTextField(String text) {
        TextField tf = new TextField(text);
        tf.setStyle(inputStyle());
        return tf;
    }

    private RadioButton createRadio(String text, ToggleGroup group) {
        RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(group);
        rb.setTextFill(Color.web(TEXT_PRIMARY));
        rb.setStyle("-fx-font-size: 13px;");
        return rb;
    }

    private Button createToolbarButton(String text) {
        Button b = new Button(text);
        b.setStyle(secondaryButtonStyle());
        return b;
    }

    private Region createSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private String primaryButtonStyle(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 10; -fx-cursor: hand; -fx-font-size: 14px;";
    }

    private String secondaryButtonStyle() {
        return "-fx-background-color: " + BG_INPUT + "; -fx-text-fill: white; -fx-background-radius: 8; " +
                "-fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 6 14 6 14;";
    }

    private String inputStyle() {
        return "-fx-background-color: " + BG_INPUT + "; -fx-text-fill: white; -fx-background-radius: 6; " +
                "-fx-border-color: #3A3A3C; -fx-border-radius: 6; -fx-prompt-text-fill: " + TEXT_SECONDARY + ";";
    }

    private void applyGlobalStyle(Scene scene) {
        String globalStyle = "-fx-font-family: 'System'; -fx-base: " + BG_DARK + "; " +
                "-fx-control-inner-background: " + BG_INPUT + "; -fx-focused-base: " + ACCENT_COLOR + "; " +
                "-fx-selection-bar: " + ACCENT_COLOR + "; -fx-selection-bar-non-focused: #3A3A3C;";
        scene.getRoot().setStyle(globalStyle);
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showError(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}