package com.sgbd.controller;

import com.sgbd.service.DataPopulationService;
import com.sgbd.service.ForecastService;
import com.sgbd.service.WeatherImporterService;
import com.sgbd.service.prediction.StartupOrchestratorService;
import com.sgbd.util.DatabaseInitializer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class MainApp extends Application {
    private static final Logger logger = Logger.getLogger(MainApp.class.getName());
    private final ForecastService forecastService = new ForecastService();
    private Label statusLabel;
    private final AtomicInteger activeBackgroundTasks = new AtomicInteger(0);

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Prognoză Meteo — România");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);

        // Inițializare bază de date
        boolean dbReady = DatabaseInitializer.initialize();
        if (!dbReady) {
            showDbErrorDialog();
        }

        TabPane tabPane = new TabPane();

        Tab tabForecast = new Tab("Prognoză");
        tabForecast.setClosable(false);
        tabForecast.setContent(createForecastContent());
        Label iconForecast = new Label("🌤️");
        iconForecast.setStyle("-fx-font-size: 16px;");
        tabForecast.setGraphic(iconForecast);

        Tab tabMap = new Tab("Hartă");
        tabMap.setClosable(false);
        tabMap.setContent(createMapContent());
        Label iconMap = new Label("🗺️");
        iconMap.setStyle("-fx-font-size: 16px;");
        tabMap.setGraphic(iconMap);

        Tab tabComparison = new Tab("Comparații");
        tabComparison.setClosable(false);
        tabComparison.setContent(createComparisonContent());
        Label iconComparison = new Label("📊");
        iconComparison.setStyle("-fx-font-size: 16px;");
        tabComparison.setGraphic(iconComparison);

        Tab tabStats = new Tab("Statistici & Anomalii");
        tabStats.setClosable(false);
        tabStats.setContent(createStatsContent());
        Label iconStats = new Label("📈");
        iconStats.setStyle("-fx-font-size: 16px;");
        tabStats.setGraphic(iconStats);

        Tab tabPredict = new Tab("Predicții");
        tabPredict.setClosable(false);
        tabPredict.setContent(createPredictionContent());
        Label iconPredict = new Label("🔮");
        iconPredict.setStyle("-fx-font-size: 16px;");
        tabPredict.setGraphic(iconPredict);

        Tab tabRankings = new Tab("Clasamente");
        tabRankings.setClosable(false);
        tabRankings.setContent(createRankingsContent());
        Label iconRankings = new Label("🏆");
        iconRankings.setStyle("-fx-font-size: 16px;");
        tabRankings.setGraphic(iconRankings);

        Tab tabAccuracy = new Tab("Acuratețe");
        tabAccuracy.setClosable(false);
        tabAccuracy.setContent(createAccuracyContent());
        Label iconAccuracy = new Label("🎯");
        iconAccuracy.setStyle("-fx-font-size: 16px;");
        tabAccuracy.setGraphic(iconAccuracy);

        Tab tabComments = new Tab("Comentarii & Voturi");
        tabComments.setClosable(false);
        tabComments.setContent(createCommentsContent());
        Label iconComments = new Label("💬");
        iconComments.setStyle("-fx-font-size: 16px;");
        tabComments.setGraphic(iconComments);

        tabPane.getTabs().addAll(tabForecast, tabMap, tabComparison, tabStats, tabPredict, tabRankings, tabAccuracy, tabComments);

        // Bară de stare îmbunătățită: mesaj stânga + indicator DB dreapta
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Pornire...");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        Label dbStatusLabel = new Label();
        dbStatusLabel.setStyle("-fx-font-size: 11px;");
        if (dbReady) {
            boolean populated = DatabaseInitializer.isDatabasePopulated();
            dbStatusLabel.setText(populated ? "DB: ✅" : "DB: ❌");
        } else {
            dbStatusLabel.setText("DB: ❌");
        }

        statusBar.getChildren().addAll(statusLabel, dbStatusLabel);

        VBox.setVgrow(tabPane, Priority.ALWAYS);

        VBox root = new VBox(statusBar, tabPane);

        Scene scene = new Scene(root, 1200, 830);
        scene.getStylesheets().add(getClass().getResource("/com/sgbd/css/style.css").toExternalForm());

        // Scurtături tastatură Ctrl+1..Ctrl+8 pentru schimbarea taburilor
        scene.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case DIGIT1 -> tabPane.getSelectionModel().select(0);
                    case DIGIT2 -> tabPane.getSelectionModel().select(1);
                    case DIGIT3 -> tabPane.getSelectionModel().select(2);
                    case DIGIT4 -> tabPane.getSelectionModel().select(3);
                    case DIGIT5 -> tabPane.getSelectionModel().select(4);
                    case DIGIT6 -> tabPane.getSelectionModel().select(5);
                    case DIGIT7 -> tabPane.getSelectionModel().select(6);
                    case DIGIT8 -> tabPane.getSelectionModel().select(7);
                    default -> {}
                }
            }
        });

        primaryStage.setScene(scene);
        primaryStage.show();

        // Confirmare la închidere dacă există operații în fundal
        primaryStage.setOnCloseRequest(event -> {
            if (activeBackgroundTasks.get() > 0) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmare închidere");
                alert.setHeaderText("Operații în desfășurare");
                alert.setContentText("Există operații de fundal active. Doriți să închideți aplicația?");
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    event.consume();
                }
            }
        });

        // Verifică dacă baza de date are date reale
        if (dbReady) {
            checkDatabasePopulationOnStartup();
        }
    }

    private void showDbErrorDialog() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Eroare bază de date");
        alert.setHeaderText("Nu s-a putut conecta la PostgreSQL");
        alert.setContentText("Verifică că serverul PostgreSQL rulează și că variabilele DB_URL, DB_USER, DB_PASSWORD sunt setate corect.");
        alert.showAndWait();
    }

    private void checkDatabasePopulationOnStartup() {
        new Thread(wrapBackgroundTask(() -> {
            try {
                Thread.sleep(800);
                boolean populated = DatabaseInitializer.isDatabasePopulated();
                int forecastCount = forecastService.checkDataFreshness().totalForecastCount;

                if (!populated || forecastCount == 0) {
                    Platform.runLater(() -> statusLabel.setText(
                        "🔄 Baza de date este goală. Se oferă populare automată..."));

                    // În mediu headless (fără display real), importăm automat fără dialog blocant
                    boolean isHeadless = java.awt.GraphicsEnvironment.isHeadless()
                        || System.getenv("HEADLESS") != null;

                    if (isHeadless) {
                        logger.info("Mediu headless detectat — import automat fără dialog.");
                        Platform.runLater(() -> populateDatabaseWithRealData());
                    } else {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                            alert.setTitle("Populare bază de date");
                            alert.setHeaderText("Baza de date nu conține date meteo.");
                            alert.setContentText("Doriți să importăm automat date istorice (2 ani) și prognoza actuală (10 zile) pentru toate orașele din România?\n\nAtenție: procesul poate dura câteva minute și folosește API-ul Open-Meteo.");

                            Optional<ButtonType> result = alert.showAndWait();
                            if (result.isPresent() && result.get() == ButtonType.OK) {
                                populateDatabaseWithRealData();
                            } else {
                                statusLabel.setText("⚠ Baza de date goală. Importați manual date din tabul 'Prognoză'.");
                            }
                        });
                    }
                } else {
                    checkDataFreshnessOnStartup();
                }
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Eroare la verificarea bazei de date: " + e.getMessage()));
            }
        })).start();
    }

    private void populateDatabaseWithRealData() {
        statusLabel.setText("🔄 Se importă date meteo reale pentru toate orașele...");
        boolean isHeadless = java.awt.GraphicsEnvironment.isHeadless()
            || System.getenv("HEADLESS") != null;

        new Thread(wrapBackgroundTask(() -> {
            try {
                DataPopulationService populator = new DataPopulationService();
                DataPopulationService.PopulationSummary summary = populator.populateAll(2, 10);

                Platform.runLater(() -> {
                    statusLabel.setText("✅ " + summary.toString());

                    if (!isHeadless) {
                        Alert info = new Alert(Alert.AlertType.INFORMATION);
                        info.setTitle("Import complet");
                        info.setHeaderText("Datele meteo au fost importate cu succes!");
                        info.setContentText(summary.toString() + "\n\nAcum puteți explora toate funcționalitățile aplicației.");
                        info.showAndWait();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Eroare la import: " + e.getMessage());
                    if (!isHeadless) {
                        Alert err = new Alert(Alert.AlertType.ERROR);
                        err.setTitle("Eroare import");
                        err.setHeaderText("Importul datelor a eșuat");
                        err.setContentText(e.getMessage());
                        err.showAndWait();
                    }
                });
            }
        })).start();
    }

    private void checkDataFreshnessOnStartup() {
        new Thread(wrapBackgroundTask(() -> {
            try {
                Thread.sleep(500);

                WeatherImporterService.FreshnessStatus status = forecastService.checkDataFreshness();
                if (status.totalForecastCount == 0) {
                    Platform.runLater(() -> statusLabel.setText(
                        "🔄 Nicio dată importată. Mergeți la tabul 'Prognoză' pentru a importa date."));
                    return;
                }

                // Rulează mentenanța automatizată de startup
                Platform.runLater(() -> statusLabel.setText(
                    "🔄 Se sincronizează datele meteo și se reconstruiește modelul..."));

                StartupOrchestratorService orchestrator = new StartupOrchestratorService();
                StartupOrchestratorService.StartupResult result = orchestrator.runStartupMaintenance();

                Platform.runLater(() -> {
                    statusLabel.setText("✅ " + result.message);

                    // Afișează rezumat doar dacă s-a făcut ceva semnificativ
                    if (result.gapDaysImported > 0 || result.mlRebuildFull) {
                        Alert info = new Alert(Alert.AlertType.INFORMATION);
                        info.setTitle("Sincronizare date");
                        info.setHeaderText("Datele meteo au fost sincronizate automat");
                        info.setContentText(
                            "• Zile istorice importate: " + result.gapDaysImported + "\n" +
                            "• Zile prognoză importate: " + result.forecastDaysImported + "\n" +
                            "• Orașe procesate: " + result.citiesProcessed + "\n" +
                            "• Predicții Monte Carlo: " + result.predictionsGenerated + " zile-oras\n" +
                            "• Iterații RL: " + result.rlIterations + " orașe\n" +
                            "• Reconstrucție ML: " + (result.mlRebuildFull ? "completă" : "incrementală")
                        );
                        info.show();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText(
                    "Eroare la sincronizare: " + e.getMessage()));
            }
        })).start();
    }

    private void refreshAllForecasts() {
        statusLabel.setText("🔄 Se actualizează prognoza meteo pentru toate orașele...");

        new Thread(wrapBackgroundTask(() -> {
            try {
                forecastService.cleanupOldForecasts();
                forecastService.autoRefreshIfStale();

                Platform.runLater(() -> {
                    try {
                        LocalDateTime lastFetch = forecastService.getLastForecastFetchTime();
                        String time = lastFetch != null ? lastFetch.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "necunoscut";
                        statusLabel.setText("✓ Prognoză actualizată la: " + time);
                    } catch (Exception e) {
                        statusLabel.setText("✓ Prognoză actualizată.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Eroare la actualizare: " + e.getMessage()));
            }
        })).start();
    }

    /**
     * Înrolează un Runnable pentru a incrementa/decrementa contorul de operații de fundal.
     * Folosit pentru a putea avertiza utilizatorul la închidere dacă mai există task-uri active.
     */
    private Runnable wrapBackgroundTask(Runnable task) {
        return () -> {
            activeBackgroundTasks.incrementAndGet();
            try {
                task.run();
            } finally {
                activeBackgroundTasks.decrementAndGet();
            }
        };
    }

    private javafx.scene.Node createForecastContent() {
        ForecastController ctrl = new ForecastController();
        return ctrl.getView();
    }

    private javafx.scene.Node createMapContent() {
        MapController ctrl = new MapController();
        return ctrl.getView();
    }

    private javafx.scene.Node createComparisonContent() {
        ComparisonController ctrl = new ComparisonController();
        return ctrl.getView();
    }

    private javafx.scene.Node createStatsContent() {
        StatsController ctrl = new StatsController();
        return ctrl.getView();
    }

    private javafx.scene.Node createPredictionContent() {
        PredictionController ctrl = new PredictionController();
        return ctrl.getView();
    }

    private javafx.scene.Node createRankingsContent() {
        RankingsController ctrl = new RankingsController();
        return ctrl.getView();
    }

    private javafx.scene.Node createAccuracyContent() {
        AccuracyController ctrl = new AccuracyController();
        return ctrl.getView();
    }

    private javafx.scene.Node createCommentsContent() {
        CommentsController ctrl = new CommentsController();
        return ctrl.getView();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
