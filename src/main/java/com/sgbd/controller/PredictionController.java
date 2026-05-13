package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.Forecast;
import com.sgbd.service.CityService;
import com.sgbd.service.ForecastService;
import com.sgbd.service.WeatherImporterService;
import com.sgbd.service.prediction.AccuracyService;
import com.sgbd.service.prediction.AccuracySummary;
import com.sgbd.service.prediction.PredictionEngineService;
import com.sgbd.service.ExportService;
import com.sgbd.util.AnimationUtil;
import com.sgbd.util.ColorUtil;
import javafx.stage.FileChooser;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.sgbd.service.WeatherApiService;

/**
 * Controller pentru tab-ul de predictii probabilistice.
 * Afiseaza benzi de incredere Monte Carlo, probabilitati de evenimente
 * si indicatori vizuali moderni pentru prognoza meteo.
 */
public class PredictionController {
    private final CityService cityService = new CityService();
    private final ForecastService forecastService = new ForecastService();
    private final PredictionEngineService predictionEngine = new PredictionEngineService();
    private final AccuracyService accuracyService = new AccuracyService();
    private final ExportService exportService = new ExportService();
    private List<PredictionEngineService.MonteCarloResult> lastMonteCarloResults;
    private City lastCity;
    private Button exportBtn;

    private ComboBox<City> cityCombo;
    private DatePicker datePicker;
    private ComboBox<Integer> daysCombo;
    private LineChart<String, Number> chart;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;
    private HBox probabilityCardsBox;
    private Label spreadLabel;
    private TableView<ForecastRow> predictionTable;
    private Label accuracyBadge;
    private Label offlineBadge;

    public Node getView() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #0f172a;");

        // Bara de control
        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(0, 0, 8, 0));

        cityCombo = new ComboBox<>();
        cityCombo.setPrefWidth(180);
        cityCombo.setPromptText("Selectează orașul");

        datePicker = new DatePicker(LocalDate.now().plusDays(1));
        daysCombo = new ComboBox<>(FXCollections.observableArrayList(7, 10, 14));
        daysCombo.setValue(7);
        daysCombo.setPrefWidth(70);

        Button mcBtn = new Button("🔮 Predicție probabilistică");
        mcBtn.getStyleClass().addAll("button", "success");

        Button apiBtn = new Button("🌐 Open-Meteo API");
        apiBtn.getStyleClass().addAll("button", "secondary");

        Button accuracyBtn = new Button("📊 Vizualizează acuratețe");
        accuracyBtn.getStyleClass().addAll("button", "secondary");

        exportBtn = new Button("Export JSON");
        exportBtn.getStyleClass().addAll("button", "secondary");
        exportBtn.setDisable(true);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setPrefSize(24, 24);

        accuracyBadge = new Label();
        accuracyBadge.setVisible(false);
        accuracyBadge.setManaged(false);
        accuracyBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");

        offlineBadge = new Label();
        offlineBadge.setVisible(false);
        offlineBadge.setManaged(false);
        offlineBadge.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #f97316; -fx-padding: 4px 10px; -fx-background-radius: 12px; -fx-background-color: rgba(249, 115, 22, 0.15);");

        filters.getChildren().addAll(
            new Label("Oraș:"), cityCombo, accuracyBadge,
            new Label("De la:"), datePicker,
            new Label("Zile:"), daysCombo,
            mcBtn, apiBtn, accuracyBtn, exportBtn, offlineBadge, loadingIndicator
        );

        statusLabel = new Label("Selectează un oraș pentru a genera predicția");
        statusLabel.getStyleClass().add("subtitle");

        // Grafic cu benzi de incredere
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Data");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Temperatură (°C)");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setPrefHeight(320);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);
        chart.getStyleClass().add("glass-panel");

        // Carduri de probabilitate
        probabilityCardsBox = new HBox(12);
        probabilityCardsBox.setAlignment(Pos.CENTER);
        probabilityCardsBox.setPadding(new Insets(8, 0, 8, 0));

        // Indicator spread
        spreadLabel = new Label();
        spreadLabel.getStyleClass().add("accent");
        spreadLabel.setVisible(false);

        // Tabel predictii
        predictionTable = new TableView<>();
        predictionTable.setPrefHeight(200);
        predictionTable.getStyleClass().add("glass-panel");
        setupTable();

        root.getChildren().addAll(filters, statusLabel, chart, probabilityCardsBox, spreadLabel, predictionTable);

        loadCities();
        updateOfflineBadge();
        mcBtn.setOnAction(e -> runMonteCarloPrediction());
        apiBtn.setOnAction(e -> runApiPrediction());
        accuracyBtn.setOnAction(e -> showAccuracyAlert());
        exportBtn.setOnAction(e -> exportPredictions());

        return root;
    }

    @SuppressWarnings("unchecked")
    private void setupTable() {
        TableColumn<ForecastRow, String> dateCol = new TableColumn<>("Data");
        dateCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(100);

        TableColumn<ForecastRow, String> tminCol = new TableColumn<>("Temp Min");
        tminCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("tempMin"));
        tminCol.setPrefWidth(90);

        TableColumn<ForecastRow, String> tmaxCol = new TableColumn<>("Temp Max");
        tmaxCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("tempMax"));
        tmaxCol.setPrefWidth(90);

        TableColumn<ForecastRow, String> p10Col = new TableColumn<>("P10");
        p10Col.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("p10"));
        p10Col.setPrefWidth(70);

        TableColumn<ForecastRow, String> p50Col = new TableColumn<>("P50");
        p50Col.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("p50"));
        p50Col.setPrefWidth(70);

        TableColumn<ForecastRow, String> p90Col = new TableColumn<>("P90");
        p90Col.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("p90"));
        p90Col.setPrefWidth(70);

        TableColumn<ForecastRow, String> windCol = new TableColumn<>("Vânt");
        windCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("wind"));
        windCol.setPrefWidth(70);

        TableColumn<ForecastRow, String> rainCol = new TableColumn<>("Ploaie %");
        rainCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("rainProb"));
        rainCol.setPrefWidth(80);

        predictionTable.getColumns().addAll(dateCol, tminCol, tmaxCol, p10Col, p50Col, p90Col, windCol, rainCol);
    }

    private void loadCities() {
        try {
            List<City> cities = cityService.getAllCities();
            cityCombo.setItems(FXCollections.observableArrayList(cities));
            cityCombo.setOnAction(e -> {
                loadAccuracyForSelectedCity();
                updateOfflineBadge();
            });
        } catch (SQLException e) {
            statusLabel.setText("Eroare la încărcarea orașelor: " + e.getMessage());
        }
    }

    private void updateOfflineBadge() {
        boolean apiUnavailable = !WeatherApiService.isApiAvailable();
        boolean staleData = false;
        try {
            LocalDateTime lastFetch = forecastService.getLastForecastFetchTime();
            if (lastFetch == null || ChronoUnit.HOURS.between(lastFetch, LocalDateTime.now()) > 6) {
                staleData = true;
            }
        } catch (SQLException e) {
            staleData = true;
        }

        if (apiUnavailable || staleData) {
            offlineBadge.setText("⚠ Prognoza poate fi veche");
            offlineBadge.setVisible(true);
            offlineBadge.setManaged(true);
        } else {
            offlineBadge.setVisible(false);
            offlineBadge.setManaged(false);
        }
    }

    private void loadAccuracyForSelectedCity() {
        City city = cityCombo.getValue();
        if (city == null) {
            accuracyBadge.setVisible(false);
            accuracyBadge.setManaged(false);
            return;
        }

        new Thread(() -> {
            AccuracySummary summary = accuracyService.getAccuracySummary(city.getId(), 30);
            Platform.runLater(() -> updateAccuracyBadge(summary));
        }).start();
    }

    private void updateAccuracyBadge(AccuracySummary summary) {
        if (summary == null || summary.getTotalComparisons() == 0) {
            accuracyBadge.setText("Acuratețe: necunoscută");
            accuracyBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-padding: 4px 10px; -fx-background-radius: 12px; -fx-background-color: rgba(148, 163, 184, 0.15);");
            accuracyBadge.setVisible(true);
            accuracyBadge.setManaged(true);
            AnimationUtil.fadeIn(accuracyBadge, 300);
            accuracyBadge.setTooltip(new Tooltip("Nu există suficiente date istorice pentru a calcula acuratețea."));
            return;
        }

        double score = Math.max(0, Math.min(100, 100 - summary.getOverallMae() * 3));
        String color;
        String bgColor;
        if (score > 80) {
            color = "#22c55e";
            bgColor = "rgba(34, 197, 94, 0.15)";
        } else if (score >= 60) {
            color = "#eab308";
            bgColor = "rgba(234, 179, 8, 0.15)";
        } else {
            color = "#ef4444";
            bgColor = "rgba(239, 68, 68, 0.15)";
        }

        accuracyBadge.setText(String.format("Acuratețe istorică: %.0f%%", score));
        accuracyBadge.setStyle(String.format(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: %s; -fx-padding: 4px 10px; -fx-background-radius: 12px; -fx-background-color: %s;",
            color, bgColor
        ));
        accuracyBadge.setVisible(true);
        accuracyBadge.setManaged(true);
        AnimationUtil.fadeIn(accuracyBadge, 300);

        Tooltip tooltip = new Tooltip(String.format(
            "MAE: %.2f°C\nRMSE: %.2f°C\nBias: %.2f°C\nComparații: %d\nHit rate evenimente: %.0f%%",
            summary.getOverallMae(), summary.getOverallRmse(), summary.getOverallBias(),
            summary.getTotalComparisons(), summary.getEventHitRate() * 100
        ));
        accuracyBadge.setTooltip(tooltip);
    }

    private void showAccuracyAlert() {
        City city = cityCombo.getValue();
        if (city == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Atenție");
            alert.setHeaderText(null);
            alert.setContentText("Selectează un oraș mai întâi!");
            alert.showAndWait();
            return;
        }

        loadingIndicator.setVisible(true);
        new Thread(() -> {
            AccuracySummary summary = accuracyService.getAccuracySummary(city.getId(), 30);
            Platform.runLater(() -> {
                loadingIndicator.setVisible(false);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Acuratețe predictie");
                alert.setHeaderText("Acuratețe pentru " + city.getName());
                if (summary == null || summary.getTotalComparisons() == 0) {
                    alert.setContentText("Nu există date de acuratețe disponibile pentru acest oraș.");
                } else {
                    double score = Math.max(0, Math.min(100, 100 - summary.getOverallMae() * 3));
                    alert.setContentText(String.format(
                        "Scor acuratețe: %.0f%%\n\nMAE: %.2f°C\nRMSE: %.2f°C\nBias: %.2f°C\nComparații: %d\nHit rate evenimente: %.0f%%",
                        score, summary.getOverallMae(), summary.getOverallRmse(), summary.getOverallBias(),
                        summary.getTotalComparisons(), summary.getEventHitRate() * 100
                    ));
                }
                alert.showAndWait();
            });
        }).start();
    }

    private void runMonteCarloPrediction() {
        City city = cityCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (city == null || date == null) {
            statusLabel.setText("⚠ Selectează un oraș și o dată!");
            AnimationUtil.shake(statusLabel, 300);
            return;
        }

        loadingIndicator.setVisible(true);
        statusLabel.setText("🔮 Se rulează simularea Monte Carlo (5000 traiectorii)...");

        new Thread(() -> {
            try {
                List<PredictionEngineService.MonteCarloResult> results =
                    predictionEngine.getFullForecast(city.getId(), date, daysCombo.getValue());

                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    if (results == null || results.isEmpty()) {
                        statusLabel.setText("Nu există date suficiente pentru predicția probabilistică.");
                        return;
                    }
                    showMonteCarloResults(results, city.getName());
                    statusLabel.setText("✅ Predicție probabilistică generată pentru " + city.getName());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Eroare: " + e.getMessage());
                });
            }
        }).start();
    }

    private void runApiPrediction() {
        City city = cityCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (city == null || date == null) {
            statusLabel.setText("⚠ Selectează un oraș și o dată!");
            return;
        }

        loadingIndicator.setVisible(true);
        statusLabel.setText("🌐 Se interoghează Open-Meteo API...");

        new Thread(() -> {
            WeatherImporterService.ImportResult r = forecastService.importForecast(
                city.getId(), city.getLatitude(), city.getLongitude(), daysCombo.getValue());

            LocalDate from = datePicker.getValue();
            int days = daysCombo.getValue();

            Platform.runLater(() -> {
                try {
                    List<Forecast> predictions = forecastService.getForecastsByCity(
                        city.getId(), from, from.plusDays(days));
                    showApiPredictions(predictions, city.getName());
                    statusLabel.setText("🌐 API: " + r.imported + " zile importate, " + r.errors + " erori");
                } catch (SQLException e) {
                    statusLabel.setText("Eroare la citirea datelor importate: " + e.getMessage());
                } finally {
                    loadingIndicator.setVisible(false);
                }
            });
        }).start();
    }

    @SuppressWarnings("unchecked")
    private void showMonteCarloResults(List<PredictionEngineService.MonteCarloResult> results, String cityName) {
        lastMonteCarloResults = results;
        lastCity = cityCombo.getValue();
        exportBtn.setDisable(false);
        chart.getData().clear();
        predictionTable.getItems().clear();
        probabilityCardsBox.getChildren().clear();

        XYChart.Series<String, Number> p10Series = new XYChart.Series<>();
        p10Series.setName("P10 (optimist)");
        XYChart.Series<String, Number> p50Series = new XYChart.Series<>();
        p50Series.setName("P50 (mediu)");
        XYChart.Series<String, Number> p90Series = new XYChart.Series<>();
        p90Series.setName("P90 (pesimist)");

        double avgSpread = 0;
        double avgRain = 0, avgStorm = 0, avgFog = 0, avgHeat = 0;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        for (PredictionEngineService.MonteCarloResult r : results) {
            String label = r.getForecastDate().format(fmt);
            p10Series.getData().add(new XYChart.Data<>(label, r.getTempMaxP10()));
            p50Series.getData().add(new XYChart.Data<>(label, r.getTempMaxP50()));
            p90Series.getData().add(new XYChart.Data<>(label, r.getTempMaxP90()));

            avgSpread += r.getEnsembleSpread();
            avgRain += r.getPrecipProb();
            avgStorm += r.getStormProb();
            avgFog += r.getFogProb();
            avgHeat += r.getHeatwaveProb();

            predictionTable.getItems().add(new ForecastRow(
                label,
                String.format("%.1f", r.getTempMinP50()),
                String.format("%.1f", r.getTempMaxP50()),
                String.format("%.1f", r.getTempMaxP10()),
                String.format("%.1f", r.getTempMaxP50()),
                String.format("%.1f", r.getTempMaxP90()),
                String.format("%.1f", r.getWindSpeedP50()),
                String.format("%.0f%%", r.getPrecipProb() * 100)
            ));
        }

        int n = results.size();
        avgSpread /= n;
        avgRain /= n;
        avgStorm /= n;
        avgFog /= n;
        avgHeat /= n;

        chart.getData().addAll(p10Series, p50Series, p90Series);

        // Stilizare serii
        p10Series.getNode().setStyle("-fx-stroke: #22c55e; -fx-stroke-width: 1.5px; -fx-stroke-dash-array: 5 5;");
        p50Series.getNode().setStyle("-fx-stroke: #0ea5e9; -fx-stroke-width: 3px;");
        p90Series.getNode().setStyle("-fx-stroke: #ef4444; -fx-stroke-width: 1.5px; -fx-stroke-dash-array: 5 5;");

        // Carduri probabilitate
        probabilityCardsBox.getChildren().addAll(
            createProbabilityCard("🌧️", "Ploaie", avgRain, Color.web("#3b82f6")),
            createProbabilityCard("⛈️", "Furtună", avgStorm, Color.web("#a855f7")),
            createProbabilityCard("🌫️", "Ceață", avgFog, Color.web("#94a3b8")),
            createProbabilityCard("🌡️", "Caniculă", avgHeat, Color.web("#f97316"))
        );

        spreadLabel.setText(String.format("📊 Incertitudine medie: %.1f°C (spread ensemble)", avgSpread));
        spreadLabel.setVisible(true);

        AnimationUtil.fadeIn(chart, 400);
        AnimationUtil.fadeIn(probabilityCardsBox, 600);
        AnimationUtil.fadeIn(predictionTable, 800);
    }

    @SuppressWarnings("unchecked")
    private void showApiPredictions(List<Forecast> predictions, String cityName) {
        lastMonteCarloResults = null;
        exportBtn.setDisable(true);
        chart.getData().clear();
        predictionTable.getItems().clear();
        probabilityCardsBox.getChildren().clear();
        spreadLabel.setVisible(false);

        XYChart.Series<String, Number> minSeries = new XYChart.Series<>();
        minSeries.setName("Temp. Min");
        XYChart.Series<String, Number> maxSeries = new XYChart.Series<>();
        maxSeries.setName("Temp. Max");

        for (Forecast f : predictions) {
            String label = f.getDate().toString();
            minSeries.getData().add(new XYChart.Data<>(label, f.getTempMin()));
            maxSeries.getData().add(new XYChart.Data<>(label, f.getTempMax()));

            predictionTable.getItems().add(new ForecastRow(
                label,
                String.format("%.1f", f.getTempMin()),
                String.format("%.1f", f.getTempMax()),
                "-", "-", "-",
                String.format("%.1f", f.getWindSpeed()),
                "-"
            ));
        }

        chart.getData().addAll(minSeries, maxSeries);
        minSeries.getNode().setStyle("-fx-stroke: #38bdf8; -fx-stroke-width: 2px;");
        maxSeries.getNode().setStyle("-fx-stroke: #f97316; -fx-stroke-width: 2px;");

        AnimationUtil.fadeIn(chart, 400);
        AnimationUtil.fadeIn(predictionTable, 600);
    }

    private VBox createProbabilityCard(String emoji, String label, double probability, Color color) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.setPrefWidth(120);
        card.getStyleClass().add("glass-card");

        Label iconLbl = new Label(emoji);
        iconLbl.setStyle("-fx-font-size: 28px;");

        ProgressIndicator pi = new ProgressIndicator(probability);
        pi.setPrefSize(56, 56);
        pi.setStyle(String.format("-fx-progress-color: %s;", ColorUtil.toCss(color)));

        Label pctLbl = new Label(String.format("%.0f%%", probability * 100));
        pctLbl.setStyle(String.format("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: %s;", ColorUtil.toCss(color)));

        Label nameLbl = new Label(label);
        nameLbl.getStyleClass().add("subtitle");

        card.getChildren().addAll(iconLbl, pi, pctLbl, nameLbl);
        AnimationUtil.bounce(card, 400);

        return card;
    }

    private void exportPredictions() {
        if (lastCity == null || lastMonteCarloResults == null || lastMonteCarloResults.isEmpty()) {
            statusLabel.setText("⚠ Nu există predicții de exportat!");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export JSON predicții");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        chooser.setInitialFileName("predictii_" + lastCity.getName().replaceAll("\\s+", "_") + "_" + java.time.LocalDate.now() + ".json");
        java.io.File file = chooser.showSaveDialog(null);
        if (file == null) return;
        try {
            exportService.exportPredictionsToJson(lastCity, lastMonteCarloResults, file.toPath());
            statusLabel.setText("✅ Exportat la: " + file.getAbsolutePath());
        } catch (Exception ex) {
            statusLabel.setText("Eroare export: " + ex.getMessage());
        }
    }

    /**
     * Model de rand pentru tabelul de predictii.
     */
    public static class ForecastRow {
        private final String date;
        private final String tempMin;
        private final String tempMax;
        private final String p10;
        private final String p50;
        private final String p90;
        private final String wind;
        private final String rainProb;

        public ForecastRow(String date, String tempMin, String tempMax, String p10, String p50, String p90, String wind, String rainProb) {
            this.date = date;
            this.tempMin = tempMin;
            this.tempMax = tempMax;
            this.p10 = p10;
            this.p50 = p50;
            this.p90 = p90;
            this.wind = wind;
            this.rainProb = rainProb;
        }

        public String getDate() { return date; }
        public String getTempMin() { return tempMin; }
        public String getTempMax() { return tempMax; }
        public String getP10() { return p10; }
        public String getP50() { return p50; }
        public String getP90() { return p90; }
        public String getWind() { return wind; }
        public String getRainProb() { return rainProb; }
    }
}
