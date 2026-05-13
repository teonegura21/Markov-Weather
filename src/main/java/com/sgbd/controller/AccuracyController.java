package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.service.CityService;
import com.sgbd.service.ExportService;
import com.sgbd.service.prediction.AccuracyService;
import com.sgbd.service.prediction.ReinforcementService;
import com.sgbd.util.AnimationUtil;
import javafx.stage.FileChooser;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Controller pentru tab-ul de acuratețe a predicțiilor.
 * Afișează metrici de precizie, compară prognozele cu valorile reale
 * și generează clasamente între orașe pe baza erorilor.
 */
public class AccuracyController {

    private final CityService cityService = new CityService();
    private final AccuracyService accuracyService = new AccuracyService();
    private final ReinforcementService reinforcementService = new ReinforcementService();
    private final ExportService exportService = new ExportService();
    private List<com.sgbd.service.prediction.AccuracyMetrics> lastBacktestResults;
    private Button exportCsvBtn;
    private Button exportJsonBtn;

    private ComboBox<City> cityCombo;
    private ComboBox<Integer> daysBackCombo;
    private ComboBox<Integer> horizonCombo;
    private ProgressIndicator loadingIndicator;
    private Label statusLabel;

    // Carduri metrici
    private VBox maeCard;
    private Label maeValue;
    private VBox rmseCard;
    private Label rmseValue;
    private VBox biasCard;
    private Label biasValue;
    private VBox hitRateCard;
    private Label hitRateValue;
    private ProgressIndicator hitRateIndicator;

    // Grafic
    private LineChart<String, Number> accuracyChart;

    // Tabele
    private TableView<ComparisonRow> comparisonTable;
    private TableView<RankingRow> rankingTable;

    // Container pentru animații
    private HBox metricsBox;
    private VBox chartContainer;
    private VBox comparisonContainer;
    private VBox rankingContainer;

    public Node getView() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #0f172a;");

        // ==================== Bara de control ====================
        HBox controlBar = new HBox(12);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(0, 0, 4, 0));

        cityCombo = new ComboBox<>();
        cityCombo.setPrefWidth(180);
        cityCombo.setPromptText("Selectează orașul");

        daysBackCombo = new ComboBox<>(FXCollections.observableArrayList(7, 14, 30, 90));
        daysBackCombo.setValue(30);
        daysBackCombo.setPrefWidth(80);

        horizonCombo = new ComboBox<>(FXCollections.observableArrayList(1, 3, 5, 7, 10));
        horizonCombo.setValue(7);
        horizonCombo.setPrefWidth(70);

        Button backtestBtn = new Button("Rulează backtest");
        backtestBtn.getStyleClass().addAll("button");

        exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().addAll("button", "secondary");
        exportCsvBtn.setDisable(true);

        exportJsonBtn = new Button("Export JSON");
        exportJsonBtn.getStyleClass().addAll("button", "secondary");
        exportJsonBtn.setDisable(true);

        Button learnBtn = new Button("Învață din erori");
        learnBtn.getStyleClass().addAll("button", "secondary");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setPrefSize(24, 24);

        controlBar.getChildren().addAll(
            new Label("Oraș:"), cityCombo,
            new Label("Zile înapoi:"), daysBackCombo,
            new Label("Orizont max:"), horizonCombo,
            backtestBtn, exportCsvBtn, exportJsonBtn, learnBtn, loadingIndicator
        );

        // ==================== Status ====================
        statusLabel = new Label("Selectează parametrii și rulează backtest-ul");
        statusLabel.getStyleClass().add("subtitle");

        // ==================== Carduri metrici ====================
        metricsBox = new HBox(12);
        metricsBox.setAlignment(Pos.CENTER);

        maeCard = createMetricCard("Eroare Medie Absolută", "0.0°C", "cu cât mai mic, cu atât mai bine");
        maeValue = (Label) maeCard.getChildren().get(1);

        rmseCard = createMetricCard("Eroare Pătratică", "0.0°C", "penalizează erorile mari");
        rmseValue = (Label) rmseCard.getChildren().get(1);

        biasCard = createMetricCard("Deplasare", "0.0°C", "tendință de supra/sub-estimare");
        biasValue = (Label) biasCard.getChildren().get(1);

        hitRateCard = createMetricCard("Rata de Succes", "0%", "prognoze în interval acceptabil");
        hitRateValue = (Label) hitRateCard.getChildren().get(1);
        hitRateIndicator = (ProgressIndicator) hitRateCard.getChildren().get(2);

        metricsBox.getChildren().addAll(maeCard, rmseCard, biasCard, hitRateCard);
        HBox.setHgrow(maeCard, Priority.ALWAYS);
        HBox.setHgrow(rmseCard, Priority.ALWAYS);
        HBox.setHgrow(biasCard, Priority.ALWAYS);
        HBox.setHgrow(hitRateCard, Priority.ALWAYS);

        // ==================== Grafic acuratețe ====================
        chartContainer = new VBox(8);
        chartContainer.getStyleClass().add("glass-panel");
        Label chartTitle = new Label("Evoluția erorii în timp");
        chartTitle.getStyleClass().add("subtitle");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Data");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Eroare (°C)");
        accuracyChart = new LineChart<>(xAxis, yAxis);
        accuracyChart.setPrefHeight(280);
        accuracyChart.setLegendVisible(true);
        accuracyChart.setCreateSymbols(false);
        accuracyChart.setStyle("-fx-background-color: transparent;");

        chartContainer.getChildren().addAll(chartTitle, accuracyChart);

        // ==================== Tabel comparație ====================
        comparisonContainer = new VBox(8);
        comparisonContainer.getStyleClass().add("glass-panel");
        Label compTitle = new Label("Comparație prognoză vs. real");
        compTitle.getStyleClass().add("subtitle");

        comparisonTable = new TableView<>();
        comparisonTable.setPrefHeight(220);
        setupComparisonTable();

        comparisonContainer.getChildren().addAll(compTitle, comparisonTable);

        // ==================== Tabel clasament ====================
        rankingContainer = new VBox(8);
        rankingContainer.getStyleClass().add("glass-panel");
        Label rankTitle = new Label("Clasament orașe după acuratețe");
        rankTitle.getStyleClass().add("subtitle");

        rankingTable = new TableView<>();
        rankingTable.setPrefHeight(200);
        setupRankingTable();

        rankingContainer.getChildren().addAll(rankTitle, rankingTable);

        // ==================== Regim breakdown (placeholder) ====================
        HBox regimeBox = new HBox(12);
        regimeBox.setAlignment(Pos.CENTER_LEFT);
        regimeBox.getStyleClass().add("glass-panel");
        regimeBox.setPadding(new Insets(10));
        Label regimeTitle = new Label("Acuratețe pe regimuri: ");
        regimeTitle.getStyleClass().add("subtitle");
        Label normalLbl = new Label("Normal: -- ");
        Label rainLbl = new Label("Ploaie: -- ");
        Label stormLbl = new Label("Furtună: -- ");
        Label heatLbl = new Label("Caniculă: -- ");
        regimeBox.getChildren().addAll(regimeTitle, normalLbl, rainLbl, stormLbl, heatLbl);

        // Adaugare în root
        root.getChildren().addAll(
            controlBar, statusLabel, metricsBox,
            chartContainer, comparisonContainer, rankingContainer, regimeBox
        );

        // Evenimente
        loadCities();
        backtestBtn.setOnAction(e -> runBacktest());
        learnBtn.setOnAction(e -> runLearning());
        exportCsvBtn.setOnAction(e -> exportAccuracyCsv());
        exportJsonBtn.setOnAction(e -> exportAccuracyJson());

        return root;
    }

    private VBox createMetricCard(String title, String initialValue, String subtitle) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(14));
        card.getStyleClass().add("glass-card");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("subtitle");

        Label valueLbl = new Label(initialValue);
        valueLbl.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");

        // Hit rate are indicator circular, celelalte nu
        Region extraNode;
        if (title.contains("Succes")) {
            ProgressIndicator pi = new ProgressIndicator(0);
            pi.setPrefSize(50, 50);
            pi.setStyle("-fx-progress-color: #22c55e;");
            extraNode = pi;
        } else {
            extraNode = new Region();
            extraNode.setPrefSize(1, 1);
        }

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        card.getChildren().addAll(titleLbl, valueLbl, extraNode, subLbl);
        return card;
    }

    @SuppressWarnings("unchecked")
    private void setupComparisonTable() {
        TableColumn<ComparisonRow, String> dataCol = new TableColumn<>("Data");
        dataCol.setCellValueFactory(new PropertyValueFactory<>("data"));
        dataCol.setPrefWidth(100);

        TableColumn<ComparisonRow, Integer> orizontCol = new TableColumn<>("Orizont");
        orizontCol.setCellValueFactory(new PropertyValueFactory<>("orizont"));
        orizontCol.setPrefWidth(70);

        TableColumn<ComparisonRow, String> prezisCol = new TableColumn<>("Temp Prezisă");
        prezisCol.setCellValueFactory(new PropertyValueFactory<>("tempPrezisa"));
        prezisCol.setPrefWidth(100);

        TableColumn<ComparisonRow, String> realCol = new TableColumn<>("Temp Reală");
        realCol.setCellValueFactory(new PropertyValueFactory<>("tempReala"));
        realCol.setPrefWidth(100);

        TableColumn<ComparisonRow, String> difCol = new TableColumn<>("Diferență");
        difCol.setCellValueFactory(new PropertyValueFactory<>("diferenta"));
        difCol.setPrefWidth(90);
        difCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    try {
                        double val = Double.parseDouble(item.replace("°C", "").replace("+", "").trim());
                        if (Math.abs(val) > 3) {
                            setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                        } else if (Math.abs(val) < 1) {
                            setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #f97316;");
                        }
                    } catch (NumberFormatException e) {
                        setStyle("");
                    }
                }
            }
        });

        TableColumn<ComparisonRow, String> windErrCol = new TableColumn<>("Eroare Vânt");
        windErrCol.setCellValueFactory(new PropertyValueFactory<>("eroareVant"));
        windErrCol.setPrefWidth(90);

        TableColumn<ComparisonRow, String> humErrCol = new TableColumn<>("Eroare Umid.");
        humErrCol.setCellValueFactory(new PropertyValueFactory<>("eroareUmiditate"));
        humErrCol.setPrefWidth(90);

        comparisonTable.getColumns().addAll(dataCol, orizontCol, prezisCol, realCol, difCol, windErrCol, humErrCol);
    }

    @SuppressWarnings("unchecked")
    private void setupRankingTable() {
        TableColumn<RankingRow, Integer> pozCol = new TableColumn<>("Poziție");
        pozCol.setCellValueFactory(new PropertyValueFactory<>("pozitie"));
        pozCol.setPrefWidth(70);

        TableColumn<RankingRow, String> orasCol = new TableColumn<>("Oraș");
        orasCol.setCellValueFactory(new PropertyValueFactory<>("oras"));
        orasCol.setPrefWidth(140);

        TableColumn<RankingRow, String> maeCol = new TableColumn<>("MAE (°C)");
        maeCol.setCellValueFactory(new PropertyValueFactory<>("mae"));
        maeCol.setPrefWidth(90);

        TableColumn<RankingRow, String> rmseCol = new TableColumn<>("RMSE (°C)");
        rmseCol.setCellValueFactory(new PropertyValueFactory<>("rmse"));
        rmseCol.setPrefWidth(90);

        TableColumn<RankingRow, Integer> compCol = new TableColumn<>("Comparații");
        compCol.setCellValueFactory(new PropertyValueFactory<>("comparatii"));
        compCol.setPrefWidth(90);

        TableColumn<RankingRow, String> scorCol = new TableColumn<>("Scor");
        scorCol.setCellValueFactory(new PropertyValueFactory<>("scor"));
        scorCol.setPrefWidth(80);
        scorCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    try {
                        int scor = Integer.parseInt(item.replace("/100", "").trim());
                        if (scor >= 80) {
                            setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                        } else if (scor >= 50) {
                            setStyle("-fx-text-fill: #eab308; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                        }
                    } catch (NumberFormatException e) {
                        setStyle("");
                    }
                }
            }
        });

        rankingTable.getColumns().addAll(pozCol, orasCol, maeCol, rmseCol, compCol, scorCol);
    }

    private void loadCities() {
        try {
            List<City> cities = cityService.getAllCities();
            cityCombo.setItems(FXCollections.observableArrayList(cities));
        } catch (SQLException e) {
            statusLabel.setText("Eroare la încărcarea orașelor: " + e.getMessage());
        }
    }

    private void runBacktest() {
        City city = cityCombo.getValue();
        if (city == null) {
            statusLabel.setText("⚠ Selectează un oraș pentru backtest!");
            AnimationUtil.shake(statusLabel, 300);
            return;
        }
        Integer daysBack = daysBackCombo.getValue();
        Integer maxHorizon = horizonCombo.getValue();
        if (daysBack == null || maxHorizon == null) {
            statusLabel.setText("⚠ Selectează toți parametrii!");
            return;
        }

        loadingIndicator.setVisible(true);
        statusLabel.setText("📊 Se rulează backtest-ul pentru " + city.getName() + "...");

        new Thread(() -> {
            try {
                List<com.sgbd.service.prediction.AccuracyMetrics> realResults =
                    accuracyService.runBacktest(city.getId(), daysBack, maxHorizon);

                lastBacktestResults = realResults;
                List<AccuracyMetrics> results;
                boolean hasReal = realResults != null && !realResults.isEmpty();
                if (hasReal) {
                    results = convertToControllerMetrics(realResults);
                } else {
                    results = generateMockData(city.getName(), daysBack, maxHorizon);
                }

                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    displayResults(results, city.getName());
                    exportCsvBtn.setDisable(!hasReal);
                    exportJsonBtn.setDisable(!hasReal);
                    statusLabel.setText("✅ Backtest completat pentru " + city.getName()
                        + (hasReal ? " (date reale)" : " (demo)"));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Eroare backtest: " + e.getMessage());
                });
            }
        }).start();
    }

    private void runLearning() {
        City city = cityCombo.getValue();
        if (city == null) {
            statusLabel.setText("⚠ Selectează un oraș pentru învățare!");
            return;
        }
        statusLabel.setText("🧠 Se rulează iteratia de învățare pentru " + city.getName() + "...");
        new Thread(() -> {
            try {
                reinforcementService.runLearningIteration(city.getId());
                Platform.runLater(() -> statusLabel.setText(
                    "✅ Învățare completă pentru " + city.getName() +
                    " — ponderile Markov/HMM au fost ajustate."));
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText(
                    "⚠ Eroare învățare: " + e.getMessage()));
            }
        }).start();
    }

    private List<AccuracyMetrics> convertToControllerMetrics(
            List<com.sgbd.service.prediction.AccuracyMetrics> serviceMetrics) {
        List<AccuracyMetrics> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        for (com.sgbd.service.prediction.AccuracyMetrics m : serviceMetrics) {
            String dateStr = m.getDate() != null ? m.getDate().format(fmt) : "?";
            boolean hit = Math.abs(m.getMaeTempMax()) <= 2.0;
            result.add(new AccuracyMetrics(
                dateStr,
                m.getMaeTempMax(),
                m.getRmseTempMax(),
                m.getBiasTempMax(),
                hit,
                m.getPredictedTempMaxP50(),
                m.getActualTempMax(),
                m.getWindError(),
                m.getHumidityError(),
                m.getHorizonDay()
            ));
        }
        return result;
    }

    /**
     * Generează date mock realiste pentru demonstrația interfeței.
     * Folosit ca fallback când nu există date reale sau predicții în baza de date.
     */
    private List<AccuracyMetrics> generateMockData(String cityName, int daysBack, int maxHorizon) {
        List<AccuracyMetrics> list = new ArrayList<>();
        Random rand = new Random(cityName.hashCode());
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        double baseMae = 1.5 + rand.nextDouble() * 2.5;
        double baseRmse = baseMae * 1.3 + rand.nextDouble();

        for (int i = daysBack; i > 0; i--) {
            LocalDate date = today.minusDays(i);
            double noise = rand.nextGaussian() * 0.4;
            double mae = Math.max(0.2, baseMae + noise);
            double rmse = Math.max(0.3, baseRmse + noise * 1.2);
            double bias = rand.nextGaussian() * 1.5;
            boolean hit = rand.nextDouble() > 0.22;

            // Date pentru tabelul de comparație
            double tempReal = 8 + rand.nextDouble() * 18;
            double tempPred = tempReal + bias + rand.nextGaussian() * mae;
            double windErr = rand.nextGaussian() * 3;
            double humErr = rand.nextGaussian() * 8;
            int horizon = 1 + rand.nextInt(maxHorizon);

            list.add(new AccuracyMetrics(
                date.format(fmt), mae, rmse, bias, hit,
                tempPred, tempReal, windErr, humErr, horizon
            ));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private void displayResults(List<AccuracyMetrics> results, String cityName) {
        // Calcul medii
        double avgMae = results.stream().mapToDouble(AccuracyMetrics::getMae).average().orElse(0);
        double avgRmse = results.stream().mapToDouble(AccuracyMetrics::getRmse).average().orElse(0);
        double avgBias = results.stream().mapToDouble(AccuracyMetrics::getBias).average().orElse(0);
        long hits = results.stream().filter(AccuracyMetrics::isHit).count();
        double hitRate = results.isEmpty() ? 0 : (hits * 100.0 / results.size());

        // Animare carduri
        animateValue(maeValue, avgMae, "°C", 800);
        animateValue(rmseValue, avgRmse, "°C", 800);
        animateValue(biasValue, avgBias, "°C", 800);

        // Stilizare bias: verde dacă e aproape de 0, roșu dacă e mare
        if (Math.abs(avgBias) < 1.0) {
            biasValue.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #22c55e;");
        } else if (Math.abs(avgBias) < 2.5) {
            biasValue.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #eab308;");
        } else {
            biasValue.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #ef4444;");
        }

        AnimationUtil.countUp(hitRateValue, hitRate, 800);
        hitRateIndicator.setProgress(hitRate / 100.0);

        AnimationUtil.fadeIn(metricsBox, 400);

        // Grafic
        accuracyChart.getData().clear();
        XYChart.Series<String, Number> maeSeries = new XYChart.Series<>();
        maeSeries.setName("MAE (°C)");
        XYChart.Series<String, Number> rmseSeries = new XYChart.Series<>();
        rmseSeries.setName("RMSE (°C)");

        for (AccuracyMetrics m : results) {
            maeSeries.getData().add(new XYChart.Data<>(m.getDate(), m.getMae()));
            rmseSeries.getData().add(new XYChart.Data<>(m.getDate(), m.getRmse()));
        }

        accuracyChart.getData().addAll(maeSeries, rmseSeries);
        maeSeries.getNode().setStyle("-fx-stroke: #0ea5e9; -fx-stroke-width: 2.5px;");
        rmseSeries.getNode().setStyle("-fx-stroke: #f97316; -fx-stroke-width: 2px; -fx-stroke-dash-array: 6 4;");

        AnimationUtil.fadeIn(chartContainer, 500);

        // Tabel comparație
        comparisonTable.getItems().clear();
        for (AccuracyMetrics m : results) {
            comparisonTable.getItems().add(new ComparisonRow(
                m.getDate(),
                m.getHorizon(),
                String.format("%.1f°C", m.getTempPredicted()),
                String.format("%.1f°C", m.getTempReal()),
                String.format("%+.1f°C", m.getTempPredicted() - m.getTempReal()),
                String.format("%.1f km/h", m.getWindError()),
                String.format("%.1f%%", m.getHumidityError())
            ));
        }
        AnimationUtil.fadeIn(comparisonContainer, 600);

        // Tabel clasament (mock pe mai multe orașe)
        rankingTable.getItems().clear();
        List<RankingRow> rankings = generateMockRankings(cityName, avgMae, avgRmse, results.size());
        rankingTable.setItems(FXCollections.observableArrayList(rankings));
        AnimationUtil.fadeIn(rankingContainer, 700);
    }

    private List<RankingRow> generateMockRankings(String selectedCity, double selMae, double selRmse, int comparisons) {
        List<RankingRow> list = new ArrayList<>();
        Random rand = new Random(42);
        String[] orase = { selectedCity, "Cluj-Napoca", "Timișoara", "Iași", "Constanța", "Brașov", "Craiova", "Oradea" };

        for (int i = 0; i < orase.length; i++) {
            double mae;
            double rmse;
            int comp;
            if (orase[i].equals(selectedCity)) {
                mae = selMae;
                rmse = selRmse;
                comp = comparisons;
            } else {
                mae = 1.2 + rand.nextDouble() * 3.5;
                rmse = mae * (1.2 + rand.nextDouble() * 0.5);
                comp = 20 + rand.nextInt(80);
            }
            int scor = (int) Math.max(0, Math.min(100, 100 - mae * 2));
            list.add(new RankingRow(i + 1, orase[i],
                String.format("%.2f", mae),
                String.format("%.2f", rmse),
                comp,
                scor + "/100"));
        }

        // Sortare după scor descrescător și reasignare poziții
        list.sort((a, b) -> {
            int scorA = Integer.parseInt(a.getScor().replace("/100", ""));
            int scorB = Integer.parseInt(b.getScor().replace("/100", ""));
            return Integer.compare(scorB, scorA);
        });
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setPozitie(i + 1);
        }
        return list;
    }

    private void animateValue(Label label, double target, String unit, double millis) {
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(new SimpleDoubleProperty(0), target);
        KeyFrame kf = new KeyFrame(Duration.millis(millis), kv);
        timeline.getKeyFrames().add(kf);
        timeline.currentTimeProperty().addListener((obs, old, val) -> {
            double progress = val.toMillis() / millis;
            double current = target * progress;
            label.setText(String.format("%.1f%s", current, unit));
        });
        timeline.play();
    }

    private void exportAccuracyCsv() {
        City city = cityCombo.getValue();
        if (city == null || lastBacktestResults == null || lastBacktestResults.isEmpty()) {
            statusLabel.setText("⚠ Nu există date de exportat!");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV acuratețe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("acuratete_" + city.getName().replaceAll("\\s+", "_") + "_" + java.time.LocalDate.now() + ".csv");
        java.io.File file = chooser.showSaveDialog(null);
        if (file == null) return;
        try {
            exportService.exportAccuracyToCsv(lastBacktestResults, file.toPath());
            statusLabel.setText("✅ Exportat la: " + file.getAbsolutePath());
        } catch (Exception ex) {
            statusLabel.setText("Eroare export: " + ex.getMessage());
        }
    }

    private void exportAccuracyJson() {
        City city = cityCombo.getValue();
        if (city == null || lastBacktestResults == null || lastBacktestResults.isEmpty()) {
            statusLabel.setText("⚠ Nu există date de exportat!");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export JSON acuratețe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        chooser.setInitialFileName("acuratete_" + city.getName().replaceAll("\\s+", "_") + "_" + java.time.LocalDate.now() + ".json");
        java.io.File file = chooser.showSaveDialog(null);
        if (file == null) return;
        try {
            exportService.exportAccuracyToJson(lastBacktestResults, file.toPath());
            statusLabel.setText("✅ Exportat la: " + file.getAbsolutePath());
        } catch (Exception ex) {
            statusLabel.setText("Eroare export: " + ex.getMessage());
        }
    }

    // ==================== Modele de date interne ====================

    /**
     * Reprezintă metricile de acuratețe pentru o singură zi/orizont.
     */
    public static class AccuracyMetrics {
        private final String date;
        private final double mae;
        private final double rmse;
        private final double bias;
        private final boolean hit;
        private final double tempPredicted;
        private final double tempReal;
        private final double windError;
        private final double humidityError;
        private final int horizon;

        public AccuracyMetrics(String date, double mae, double rmse, double bias, boolean hit,
                               double tempPredicted, double tempReal, double windError,
                               double humidityError, int horizon) {
            this.date = date;
            this.mae = mae;
            this.rmse = rmse;
            this.bias = bias;
            this.hit = hit;
            this.tempPredicted = tempPredicted;
            this.tempReal = tempReal;
            this.windError = windError;
            this.humidityError = humidityError;
            this.horizon = horizon;
        }

        public String getDate() { return date; }
        public double getMae() { return mae; }
        public double getRmse() { return rmse; }
        public double getBias() { return bias; }
        public boolean isHit() { return hit; }
        public double getTempPredicted() { return tempPredicted; }
        public double getTempReal() { return tempReal; }
        public double getWindError() { return windError; }
        public double getHumidityError() { return humidityError; }
        public int getHorizon() { return horizon; }
    }

    /**
     * Rând pentru tabelul de comparație prognoză vs. real.
     */
    public static class ComparisonRow {
        private final String data;
        private final int orizont;
        private final String tempPrezisa;
        private final String tempReala;
        private final String diferenta;
        private final String eroareVant;
        private final String eroareUmiditate;

        public ComparisonRow(String data, int orizont, String tempPrezisa, String tempReala,
                             String diferenta, String eroareVant, String eroareUmiditate) {
            this.data = data;
            this.orizont = orizont;
            this.tempPrezisa = tempPrezisa;
            this.tempReala = tempReala;
            this.diferenta = diferenta;
            this.eroareVant = eroareVant;
            this.eroareUmiditate = eroareUmiditate;
        }

        public String getData() { return data; }
        public int getOrizont() { return orizont; }
        public String getTempPrezisa() { return tempPrezisa; }
        public String getTempReala() { return tempReala; }
        public String getDiferenta() { return diferenta; }
        public String getEroareVant() { return eroareVant; }
        public String getEroareUmiditate() { return eroareUmiditate; }
    }

    /**
     * Rând pentru tabelul de clasament al orașelor.
     */
    public static class RankingRow {
        private int pozitie;
        private final String oras;
        private final String mae;
        private final String rmse;
        private final int comparatii;
        private final String scor;

        public RankingRow(int pozitie, String oras, String mae, String rmse, int comparatii, String scor) {
            this.pozitie = pozitie;
            this.oras = oras;
            this.mae = mae;
            this.rmse = rmse;
            this.comparatii = comparatii;
            this.scor = scor;
        }

        public int getPozitie() { return pozitie; }
        public void setPozitie(int pozitie) { this.pozitie = pozitie; }
        public String getOras() { return oras; }
        public String getMae() { return mae; }
        public String getRmse() { return rmse; }
        public int getComparatii() { return comparatii; }
        public String getScor() { return scor; }
    }
}
