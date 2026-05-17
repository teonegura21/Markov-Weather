package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.CityRanking;
import com.sgbd.model.Anomaly;
import com.sgbd.model.ComparisonResult;
import com.sgbd.model.Forecast;
import com.sgbd.model.HourlyForecast;
import com.sgbd.service.CityService;
import com.sgbd.service.ForecastService;
import com.sgbd.service.StatisticsService;
import com.sgbd.service.prediction.PredictionEngineService;
import com.sgbd.util.BaseController;
import com.sgbd.util.SessionManager;
import com.sgbd.util.WeatherGradient;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;

/**
 * Dashboard unificat — combină Acum, Istoric, Predicții și Clasamente într-un singur tab scrollabil.
 */
public class UnifiedDashboardController extends BaseController {

    private final CityService cityService = new CityService();
    private final ForecastService forecastService = new ForecastService();
    private final StatisticsService statsService = new StatisticsService();
    private final PredictionEngineService engine = new PredictionEngineService();
    private final SessionManager session = SessionManager.getInstance();

    private VBox innerContent;
    private List<City> allCities = new ArrayList<>();

    // Hero
    private Label cityNameLabel;
    private ComboBox<City> cityCombo;
    private Label dateLabel;
    private Label mainEmoji;
    private Label mainTemp;
    private Label conditionLabel;
    private Label minMaxLabel;
    private Label alertLabel;
    private Label scoreLabel;

    // Hourly
    private HBox hourlyBox;

    // Daily
    private HBox dailyBox;

    // Detail grid
    private GridPane detailGrid;

    // Evolution chart
    private LineChart<String, Number> evolutionChart;
    private VBox historyList;
    private Label rangeLabel;
    private int currentRangeDays = 7;

    // Probabilistic chart
    private LineChart<String, Number> probChart;
    private Label confidenceLabel;
    private Label regimeLabel;
    private VBox probCardsBox;

    // Comparison
    private VBox comparisonBox;
    private HBox comparisonToggleBox;
    private String currentComparisonType = "same_day";

    // Rankings
    private VBox rankingsBox;

    // Similar Cities
    private VBox similarCitiesBox;

    // Anomalii
    private VBox anomaliesBox;

    // Prognoze contestate
    private VBox errorForecastsBox;

    @Override
    protected void buildContent(VBox container) {
        container.setPadding(new Insets(0));
        container.setAlignment(Pos.TOP_CENTER);
        container.setSpacing(0);

        innerContent = new VBox(16);
        innerContent.setPadding(new Insets(20));
        innerContent.setAlignment(Pos.TOP_CENTER);
        innerContent.setMaxWidth(Double.MAX_VALUE);

        ScrollPane scroll = new ScrollPane(innerContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        container.getChildren().add(scroll);

        buildHeroSection();
        buildHourlySection();
        buildDailySection();
        buildDetailGrid();
        buildEvolutionSection();
        buildProbabilisticSection();
        buildComparisonSection();
        buildProbabilityCardsSection();
        buildAnomaliesSection();
        buildErrorForecastsSection();
        buildRankingsSection();
        buildSimilarCitiesSection();

        City existingCity = session.getSelectedCity();
        if (existingCity != null) {
            loadAllData(existingCity);
        }
    }

    // ===================== BUILD SECTIONS =====================

    private void buildHeroSection() {
        // City selector styled as a large title
        cityCombo = new ComboBox<>();
        cityCombo.setPromptText("Selectează un oraș");
        cityCombo.setStyle(
            "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;" +
            "-fx-background-color: transparent; -fx-border-color: transparent;" +
            "-fx-padding: 0;"
        );
        cityCombo.setOnAction(e -> {
            City c = cityCombo.getValue();
            if (c != null) { session.setSelectedCity(c); }
        });

        dateLabel = new Label();
        dateLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
        updateDateLabel();

        VBox headerBox = new VBox(2, cityCombo, dateLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setMaxWidth(Double.MAX_VALUE);

        VBox heroCard = new VBox(6);
        heroCard.getStyleClass().add("weather-hero-card");
        heroCard.setAlignment(Pos.CENTER);
        heroCard.setMaxWidth(420);

        mainEmoji = new Label("🌤️");
        mainEmoji.setStyle("-fx-font-size: 72px;");
        mainEmoji.setMaxWidth(Double.MAX_VALUE);
        mainEmoji.setAlignment(Pos.CENTER);

        mainTemp = new Label("—°");
        mainTemp.getStyleClass().add("weather-hero-temp");
        mainTemp.setMaxWidth(Double.MAX_VALUE);
        mainTemp.setAlignment(Pos.CENTER);

        conditionLabel = new Label("Se încarcă...");
        conditionLabel.getStyleClass().add("weather-hero-condition");
        conditionLabel.setMaxWidth(Double.MAX_VALUE);
        conditionLabel.setAlignment(Pos.CENTER);

        minMaxLabel = new Label("—");
        minMaxLabel.getStyleClass().add("weather-hero-minmax");
        minMaxLabel.setMaxWidth(Double.MAX_VALUE);
        minMaxLabel.setAlignment(Pos.CENTER);

        alertLabel = new Label();
        alertLabel.getStyleClass().add("alert-banner");
        alertLabel.setVisible(false);
        alertLabel.setManaged(false);
        alertLabel.setMaxWidth(Double.MAX_VALUE);
        alertLabel.setAlignment(Pos.CENTER);

        scoreLabel = new Label();
        scoreLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        scoreLabel.setVisible(false);
        scoreLabel.setManaged(false);
        scoreLabel.setMaxWidth(Double.MAX_VALUE);
        scoreLabel.setAlignment(Pos.CENTER);

        heroCard.getChildren().addAll(mainEmoji, mainTemp, conditionLabel, minMaxLabel, alertLabel, scoreLabel);

        // iOS-style date pill strip
        HBox dateStrip = buildDateStrip();

        innerContent.getChildren().addAll(headerBox, heroCard, dateStrip);
    }

    private HBox buildDateStrip() {
        HBox strip = new HBox(6);
        strip.setAlignment(Pos.CENTER);
        strip.setPadding(new Insets(4, 0, 4, 0));
        strip.setMaxWidth(Double.MAX_VALUE);

        LocalDate base = session.getSelectedDate();
        if (base == null) { base = LocalDate.now(); }

        for (int offset = -3; offset <= 3; offset++) {
            LocalDate d = base.plusDays(offset);
            String text;
            if (offset == 0) text = "Azi";
            else if (offset == 1) text = "Mâine";
            else if (offset == -1) text = "Ieri";
            else {
                String dayName = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("ro"));
                text = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
            }

            Button pill = new Button(text);
            pill.setStyle(getDatePillStyle(offset == 0));
            final LocalDate selectedDate = d;
            final boolean isSelected = offset == 0;
            pill.setOnAction(e -> {
                session.setSelectedDate(selectedDate);
                // Refresh all pill styles
                for (Node node : strip.getChildren()) {
                    if (node instanceof Button b) {
                        boolean active = b.getText().equals(text);
                        b.setStyle(getDatePillStyle(active));
                    }
                }
            });
            strip.getChildren().add(pill);
        }
        return strip;
    }

    private String getDatePillStyle(boolean selected) {
        if (selected) {
            return "-fx-background-color: rgba(56, 189, 248, 0.2);" +
                   "-fx-text-fill: #38bdf8; -fx-font-weight: bold;" +
                   "-fx-background-radius: 16px; -fx-padding: 6px 14px;" +
                   "-fx-border-color: rgba(56, 189, 248, 0.4); -fx-border-radius: 16px; -fx-border-width: 1px;" +
                   "-fx-cursor: hand;";
        } else {
            return "-fx-background-color: rgba(255, 255, 255, 0.05);" +
                   "-fx-text-fill: #94a3b8;" +
                   "-fx-background-radius: 16px; -fx-padding: 6px 14px;" +
                   "-fx-border-color: transparent; -fx-cursor: hand;";
        }
    }

    private void buildHourlySection() {
        Label hourlyTitle = new Label("Prognoză orară");
        hourlyTitle.getStyleClass().add("section-title");
        hourlyTitle.setAlignment(Pos.CENTER);
        hourlyTitle.setMaxWidth(Double.MAX_VALUE);

        hourlyBox = new HBox(8);
        hourlyBox.getStyleClass().add("hourly-row");
        hourlyBox.setAlignment(Pos.CENTER);

        // Wrap in a centered HBox so content stays centered when narrower than viewport
        HBox hourlyWrapper = new HBox(hourlyBox);
        hourlyWrapper.setAlignment(Pos.CENTER);
        hourlyWrapper.setMaxWidth(Double.MAX_VALUE);
        hourlyWrapper.setStyle("-fx-background: transparent;");

        ScrollPane hourlyScroll = new ScrollPane(hourlyWrapper);
        hourlyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        hourlyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        hourlyScroll.setStyle("-fx-background: transparent;");
        hourlyScroll.setFitToHeight(true);
        hourlyScroll.setFitToWidth(true);
        hourlyScroll.setMaxWidth(Double.MAX_VALUE);

        innerContent.getChildren().addAll(hourlyTitle, hourlyScroll);
    }

    private void buildDailySection() {
        Label dailyTitle = new Label("Prognoza pe zile");
        dailyTitle.getStyleClass().add("section-title");
        dailyTitle.setAlignment(Pos.CENTER);
        dailyTitle.setMaxWidth(Double.MAX_VALUE);

        dailyBox = new HBox(10);
        dailyBox.setAlignment(Pos.CENTER);

        // Wrap in a centered HBox so content stays centered when narrower than viewport
        HBox dailyWrapper = new HBox(dailyBox);
        dailyWrapper.setAlignment(Pos.CENTER);
        dailyWrapper.setMaxWidth(Double.MAX_VALUE);
        dailyWrapper.setStyle("-fx-background: transparent;");

        ScrollPane dailyScroll = new ScrollPane(dailyWrapper);
        dailyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        dailyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dailyScroll.setStyle("-fx-background: transparent;");
        dailyScroll.setFitToHeight(true);
        dailyScroll.setFitToWidth(true);
        dailyScroll.setMaxWidth(Double.MAX_VALUE);

        innerContent.getChildren().addAll(dailyTitle, dailyScroll);
    }

    private void buildDetailGrid() {
        Label detailsTitle = new Label("Detalii");
        detailsTitle.getStyleClass().add("section-title");
        detailsTitle.setAlignment(Pos.CENTER);
        detailsTitle.setMaxWidth(Double.MAX_VALUE);

        detailGrid = new GridPane();
        detailGrid.getStyleClass().add("weather-detail-grid");
        detailGrid.setAlignment(Pos.CENTER);
        detailGrid.setMaxWidth(520);

        innerContent.getChildren().addAll(detailsTitle, detailGrid);
    }

    private void buildEvolutionSection() {
        Label title = new Label("Evoluție temperatură");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        HBox rangeBox = new HBox(8);
        rangeBox.setAlignment(Pos.CENTER);
        rangeBox.setMaxWidth(Double.MAX_VALUE);
        Button btn7 = createRangeButton("7 zile", 7);
        Button btn30 = createRangeButton("30 zile", 30);
        Button btn90 = createRangeButton("90 zile", 90);
        rangeLabel = new Label("");
        rangeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
        rangeBox.getChildren().addAll(btn7, btn30, btn90, rangeLabel);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setTickLabelFill(javafx.scene.paint.Color.web("#94a3b8"));
        yAxis.setTickLabelFill(javafx.scene.paint.Color.web("#94a3b8"));
        yAxis.setLabel("Temperatură (°C)");

        evolutionChart = new LineChart<>(xAxis, yAxis);
        evolutionChart.setPrefHeight(250);
        evolutionChart.setMaxWidth(Double.MAX_VALUE);
        evolutionChart.getStyleClass().add("chart");
        evolutionChart.setLegendVisible(true);
        evolutionChart.setCreateSymbols(true);
        evolutionChart.setAnimated(false);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);

        historyList = new VBox(4);
        historyList.setAlignment(Pos.CENTER);
        ScrollPane listScroll = new ScrollPane(historyList);
        listScroll.setFitToWidth(true);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScroll.setStyle("-fx-background: transparent;");
        listScroll.setPrefHeight(220);
        listScroll.setMaxWidth(Double.MAX_VALUE);

        VBox chartCard = wrapInChartCard(title, rangeBox, evolutionChart, listScroll);
        innerContent.getChildren().add(chartCard);
    }

    private void buildProbabilisticSection() {
        Label title = new Label("Predicții probabilistice (10 zile)");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        confidenceLabel = new Label("Se calculează...");
        confidenceLabel.getStyleClass().add("confidence-badge");
        confidenceLabel.setMaxWidth(Double.MAX_VALUE);
        confidenceLabel.setAlignment(Pos.CENTER);

        regimeLabel = new Label("");
        regimeLabel.getStyleClass().add("regime-label");
        regimeLabel.setMaxWidth(Double.MAX_VALUE);
        regimeLabel.setAlignment(Pos.CENTER);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setTickLabelFill(javafx.scene.paint.Color.web("#94a3b8"));
        yAxis.setTickLabelFill(javafx.scene.paint.Color.web("#94a3b8"));
        xAxis.setLabel("Ziua");
        yAxis.setLabel("Temperatură (°C)");

        probChart = new LineChart<>(xAxis, yAxis);
        probChart.setPrefHeight(250);
        probChart.setMaxWidth(Double.MAX_VALUE);
        probChart.getStyleClass().add("chart");
        probChart.setLegendVisible(true);
        probChart.setCreateSymbols(false);

        VBox chartCard = wrapInChartCard(title, confidenceLabel, regimeLabel, probChart);
        innerContent.getChildren().add(chartCard);
    }

    private void buildComparisonSection() {
        Label title = new Label("Comparație istorică");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        comparisonToggleBox = new HBox(8);
        comparisonToggleBox.setAlignment(Pos.CENTER);
        comparisonToggleBox.setMaxWidth(Double.MAX_VALUE);
        Button btnSameDay = createComparisonToggleButton("Zi curentă", "same_day");
        Button btnMonthly = createComparisonToggleButton("Lunar", "monthly");
        Button btnAnnual = createComparisonToggleButton("Anual", "annual");
        comparisonToggleBox.getChildren().addAll(btnSameDay, btnMonthly, btnAnnual);

        comparisonBox = new VBox(10);
        comparisonBox.setAlignment(Pos.TOP_CENTER);
        comparisonBox.setMaxWidth(500);

        VBox chartCard = wrapInChartCard(title, comparisonToggleBox, comparisonBox);
        innerContent.getChildren().add(chartCard);
    }

    private void buildProbabilityCardsSection() {
        Label title = new Label("Probabilități meteo");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        probCardsBox = new VBox(10);
        probCardsBox.setAlignment(Pos.TOP_CENTER);
        probCardsBox.setMaxWidth(500);

        innerContent.getChildren().addAll(title, probCardsBox);
    }

    private void buildRankingsSection() {
        Label title = new Label("Clasamente orașe");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        rankingsBox = new VBox(10);
        rankingsBox.setAlignment(Pos.TOP_CENTER);
        rankingsBox.setMaxWidth(600);

        innerContent.getChildren().addAll(title, rankingsBox);
    }

    private void buildSimilarCitiesSection() {
        Label title = new Label("Orașe similare");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        similarCitiesBox = new VBox(10);
        similarCitiesBox.setAlignment(Pos.TOP_CENTER);
        similarCitiesBox.setMaxWidth(600);

        innerContent.getChildren().addAll(title, similarCitiesBox);
    }

    private void buildAnomaliesSection() {
        Label title = new Label("Anomalii detectate");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        anomaliesBox = new VBox(10);
        anomaliesBox.setAlignment(Pos.TOP_CENTER);
        anomaliesBox.setMaxWidth(500);

        innerContent.getChildren().addAll(title, anomaliesBox);
    }

    private void buildErrorForecastsSection() {
        Label title = new Label("Prognoze contestate");
        title.getStyleClass().add("section-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        errorForecastsBox = new VBox(10);
        errorForecastsBox.setAlignment(Pos.TOP_CENTER);
        errorForecastsBox.setMaxWidth(500);

        innerContent.getChildren().addAll(title, errorForecastsBox);
    }

    // ===================== DATA LOADING =====================

    @Override
    protected void onCityChanged(City city) {
        loadAllData(city);
    }

    @Override
    protected void onDateChanged(LocalDate date) {
        City city = session.getSelectedCity();
        if (city != null) {
            loadAllData(city);
        }
    }

    private void loadAllData(City city) {
        if (city == null) { return; }
        showLoading(true);
        hideError();

        // Load cities for dropdown
        try {
            allCities = cityService.getAllCities();
        } catch (SQLException e) {
            // silent — will retry on next render
        }

        LocalDate selectedDate = session.getSelectedDate();
        final LocalDate today = selectedDate != null ? selectedDate : LocalDate.now();

        // Thread 1: Hero + Hourly + Daily + Details
        new Thread(() -> {
            try {
                List<Forecast> forecasts = forecastService.getForecastsByCity(city.getId(), today, today.plusDays(5));
                Forecast report = forecastService.getDailyReport(city.getId(), today);
                double pressure = fetchPressure(city.getId(), today);
                Forecast todayForecast = forecasts.isEmpty() ? null : forecasts.get(0);
                List<HourlyForecast> hourly = forecastService.getHourlyForecasts(city.getId(), today);

                Platform.runLater(() -> {
                    renderHero(city, todayForecast, report, today);
                    renderHourly(hourly, todayForecast);
                    renderDaily(forecasts);
                    renderDetails(todayForecast, pressure);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Eroare date principale: " + e.getMessage()));
            }
        }).start();

        // Thread 2: Evolution chart
        new Thread(() -> {
            LocalDate end = today;
            LocalDate start = end.minusDays(currentRangeDays);
            try {
                List<Forecast> evo = forecastService.getCityWeatherEvolution(city.getId(), start, end);
                Platform.runLater(() -> renderEvolution(evo, start, end));
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(getClass().getName())
                    .warning("Eroare încărcare evoluție (" + currentRangeDays + " zile): " + e.getMessage());
                Platform.runLater(() -> renderEvolution(new ArrayList<>(), start, end));
            }
        }).start();

        // Thread 3: Probabilistic predictions
        new Thread(() -> {
            List<PredictionEngineService.MonteCarloResult> results = new ArrayList<>();
            try {
                for (int d = 0; d < 10; d++) {
                    var r = engine.getProbabilisticForecast(city.getId(), today.plusDays(d));
                    if (r != null) { results.add(r); }
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("Eroare predicții probabilistice: " + e.getMessage()));
            } finally {
                List<PredictionEngineService.MonteCarloResult> finalResults = results;
                Platform.runLater(() -> {
                    renderProbabilistic(finalResults);
                    renderProbabilityCards(finalResults);
                });
            }
        }).start();

        // Thread 3b: Comparison
        new Thread(() -> {
            try {
                List<ComparisonResult> comp;
                switch (currentComparisonType) {
                    case "monthly" -> comp = forecastService.compareMonthly(city.getId(), today.getYear(), today.getMonthValue());
                    case "annual" -> comp = forecastService.compareAnnual(city.getId(), today.getYear());
                    default -> comp = forecastService.compareSameDay(city.getId(), today);
                }
                Platform.runLater(() -> renderComparison(comp));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    renderComparison(new ArrayList<>());
                    showError("Eroare comparație: " + e.getMessage());
                });
            }
        }).start();

        // Thread 4: Rankings
        new Thread(() -> {
            try {
                var hottest = statsService.getCityRankings("hottest", 5);
                var coldest = statsService.getCityRankings("coldest", 5);
                var windiest = statsService.getCityRankings("windiest", 5);
                var mostHumid = statsService.getCityRankings("most_humid", 5);
                var mostWarnings = statsService.getCityRankings("most_warnings", 5);
                var mostExtreme = statsService.getCityRankings("most_extreme", 5);
                Platform.runLater(() -> renderRankings(hottest, coldest, windiest,
                    mostHumid, mostWarnings, mostExtreme));
            } catch (SQLException e) {
                // silent
            } finally {
                Platform.runLater(() -> showLoading(false));
            }
        }).start();

        // Thread 5: Orașe similare + Anomalii + Prognoze contestate
        new Thread(() -> {
            try {
                int year = today.getYear();
                var similar = statsService.classifySimilarCities(city.getId(), 30);
                var anomalies = statsService.detectAnomalies(city.getId(), year);
                var errors = statsService.identifyErrorForecasts(city.getId(), 50);
                Platform.runLater(() -> {
                    renderSimilarCities(similar);
                    renderAnomalies(anomalies);
                    renderErrorForecasts(errors);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Eroare anomalii / prognoze contestate: " + e.getMessage()));
            }
        }).start();
    }

    // ===================== RENDER SECTIONS =====================

    private void renderHero(City city, Forecast today, Forecast report, LocalDate date) {
        // Populate city combo if not already loaded
        if (cityCombo.getItems().isEmpty() && !allCities.isEmpty()) {
            cityCombo.setItems(FXCollections.observableArrayList(allCities));
        }
        cityCombo.getSelectionModel().select(city);
        updateDateLabel();

        if (today == null) {
            mainTemp.setText("—°");
            conditionLabel.setText("Nu există prognoză");
            return;
        }

        String icon = today.getIconType();
        String gradientStyle = WeatherGradient.getGradientStyle(WeatherGradient.getStyleForIcon(icon));
        innerContent.setStyle(gradientStyle);

        mainEmoji.setText(iconToEmoji(icon));
        mainTemp.setText(String.format("%.0f°", today.getTempAvg()));
        conditionLabel.setText(iconToDesc(icon));
        minMaxLabel.setText(String.format("↑ %.0f°    ↓ %.0f°", today.getTempMax(), today.getTempMin()));

        if (report != null && report.getWarningText() != null && !report.getWarningText().isBlank()) {
            alertLabel.setText("⚠ " + report.getWarningText());
            alertLabel.setVisible(true);
            alertLabel.setManaged(true);
        } else {
            alertLabel.setVisible(false);
            alertLabel.setManaged(false);
        }

        if (today != null && today.getId() > 0) {
            final int fid = today.getId();
            new Thread(() -> {
                try {
                    double sc = statsService.getForecastScore(fid);
                    Platform.runLater(() -> {
                        if (sc > 0) {
                            scoreLabel.setText("Scor prognoză: " + String.format("%.2f", sc) + " / 5");
                            scoreLabel.setVisible(true);
                            scoreLabel.setManaged(true);
                        }
                    });
                } catch (SQLException ignored) { }
            }).start();
        } else {
            scoreLabel.setVisible(false);
            scoreLabel.setManaged(false);
        }
    }

    private void renderHourly(List<HourlyForecast> hourly, Forecast today) {
        hourlyBox.getChildren().clear();
        if (!hourly.isEmpty()) {
            for (HourlyForecast h : hourly) {
                if (h.getHour() % 3 == 0) {
                    hourlyBox.getChildren().add(createHourlyCard(h.getHour(), h.getTemperature(), h.getIconType()));
                }
            }
        } else if (today != null) {
            for (int h = 0; h < 24; h += 3) {
                hourlyBox.getChildren().add(createHourlyCard(h, today.getTempAvg(), today.getIconType()));
            }
        }
    }

    private void renderDaily(List<Forecast> forecasts) {
        dailyBox.getChildren().clear();
        for (int i = 0; i < Math.min(forecasts.size(), 10); i++) {
            dailyBox.getChildren().add(createDailyMiniCard(forecasts.get(i), i == 0));
        }
    }

    private void renderDetails(Forecast today, double pressure) {
        detailGrid.getChildren().clear();
        if (today == null) { return; }

        double feelsLike = computeFeelsLike(today.getTempAvg(), today.getWindSpeed(), today.getHumidity());
        double dewPoint = computeDewPoint(today.getTempAvg(), today.getHumidity());

        detailGrid.add(createDetailCell("💧", today.getHumidity() + "%", "Umiditate"), 0, 0);
        detailGrid.add(createDetailCell("💨", String.format("%.0f km/h", today.getWindSpeed()), "Vânt"), 1, 0);
        detailGrid.add(createDetailCell("🌡️", String.format("%.0f hPa", pressure), "Presiune"), 2, 0);
        detailGrid.add(createDetailCell("☀️", String.valueOf(today.getUvIndex()), "UV"), 3, 0);
        detailGrid.add(createDetailCell("🤒", String.format("%.0f°", feelsLike), "Senzație"), 0, 1);
        detailGrid.add(createDetailCell("🌫️", String.format("%.0f°", dewPoint), "Punct rouă"), 1, 1);
        detailGrid.add(createDetailCell("👁️", "10 km", "Vizibilitate"), 2, 1);
        detailGrid.add(createDetailCell("💧", today.getHumidity() > 80 ? "Ridicată" : "Normală", "Precipitații"), 3, 1);
    }

    private void renderEvolution(List<Forecast> forecasts, LocalDate start, LocalDate end) {
        evolutionChart.getData().clear();
        historyList.getChildren().clear();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM", new Locale("ro"));
        rangeLabel.setText(start.format(fmt) + " - " + end.format(fmt));

        if (forecasts == null || forecasts.isEmpty()) {
            historyList.getChildren().add(new Label("Nu există date"));
            return;
        }

        XYChart.Series<String, Number> maxSeries = new XYChart.Series<>();
        maxSeries.setName("Max");
        XYChart.Series<String, Number> minSeries = new XYChart.Series<>();
        minSeries.setName("Min");
        XYChart.Series<String, Number> avgSeries = new XYChart.Series<>();
        avgSeries.setName("Medie");

        for (Forecast f : forecasts) {
            String label = f.getDate().format(DateTimeFormatter.ofPattern("dd/MM", new Locale("ro")));
            maxSeries.getData().add(new XYChart.Data<>(label, f.getTempMax()));
            minSeries.getData().add(new XYChart.Data<>(label, f.getTempMin()));
            avgSeries.getData().add(new XYChart.Data<>(label, f.getTempAvg()));

            HBox row = new HBox(16);
            row.getStyleClass().add("daily-row");
            row.setPadding(new Insets(8, 16, 8, 16));
            row.setAlignment(Pos.CENTER);
            row.setMaxWidth(Double.MAX_VALUE);

            Label dateLbl = new Label(f.getDate().format(DateTimeFormatter.ofPattern("EEE, d MMM", new Locale("ro"))));
            dateLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f8fafc; -fx-min-width: 100px;");

            Label emoji = new Label(iconToEmoji(f.getIconType()));
            emoji.setStyle("-fx-font-size: 20px;");

            Label tempLbl = new Label(String.format("%.0f° / %.0f°", f.getTempMin(), f.getTempMax()));
            tempLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #f8fafc; -fx-min-width: 80px;");

            Label wind = new Label(String.format("💨 %.0f km/h", f.getWindSpeed()));
            wind.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-min-width: 90px;");

            Label hum = new Label(String.format("💧 %d%%", f.getHumidity()));
            hum.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-min-width: 60px;");

            HBox.setHgrow(hum, Priority.ALWAYS);
            row.getChildren().addAll(dateLbl, emoji, tempLbl, wind, hum);
            historyList.getChildren().add(row);
        }

        evolutionChart.getData().addAll(minSeries, avgSeries, maxSeries);
        evolutionChart.layout();

        if (minSeries.getNode() != null) {
            minSeries.getNode().setStyle("-fx-stroke: #38bdf8; -fx-stroke-width: 2px;");
        }
        if (avgSeries.getNode() != null) {
            avgSeries.getNode().setStyle("-fx-stroke: #a78bfa; -fx-stroke-width: 2px; -fx-stroke-dash-array: 5 5;");
        }
        if (maxSeries.getNode() != null) {
            maxSeries.getNode().setStyle("-fx-stroke: #f97316; -fx-stroke-width: 2px;");
        }
    }

    private void renderProbabilistic(List<PredictionEngineService.MonteCarloResult> results) {
        probChart.getData().clear();

        if (results.isEmpty()) {
            confidenceLabel.setText("Nu există date de predicție");
            regimeLabel.setText("");
            return;
        }

        XYChart.Series<String, Number> p10 = new XYChart.Series<>();
        p10.setName("P10 (pessimist)");
        XYChart.Series<String, Number> p50 = new XYChart.Series<>();
        p50.setName("P50 (median)");
        XYChart.Series<String, Number> p90 = new XYChart.Series<>();
        p90.setName("P90 (optimist)");

        double avgSpread = 0;
        for (var r : results) {
            String day = r.getForecastDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("ro"));
            day = day.substring(0, 1).toUpperCase() + day.substring(1);
            p10.getData().add(new XYChart.Data<>(day, r.getTempMaxP10()));
            p50.getData().add(new XYChart.Data<>(day, r.getTempMaxP50()));
            p90.getData().add(new XYChart.Data<>(day, r.getTempMaxP90()));
            avgSpread += r.getEnsembleSpread();
        }

        avgSpread /= results.size();
        double confidence = Math.max(0, 100 - avgSpread * 3);
        confidenceLabel.setText(String.format("Încredere prognoză: %.0f%%", confidence));

        String regime = confidence > 80 ? "Regim stabil" : confidence > 50 ? "Regim în tranziție" : "Regim incert";
        regimeLabel.setText("🌡️ " + regime);

        probChart.getData().addAll(p10, p50, p90);
        Platform.runLater(() -> {
            if (p10.getNode() != null) {
                p10.getNode().setStyle("-fx-stroke: #4ade80; -fx-stroke-width: 2px; -fx-stroke-dash-array: 5 5;");
            }
            if (p50.getNode() != null) {
                p50.getNode().setStyle("-fx-stroke: #38bdf8; -fx-stroke-width: 2.5px;");
            }
            if (p90.getNode() != null) {
                p90.getNode().setStyle("-fx-stroke: #f87171; -fx-stroke-width: 2px; -fx-stroke-dash-array: 5 5;");
            }
        });
    }

    private void renderComparison(List<ComparisonResult> results) {
        comparisonBox.getChildren().clear();
        if (results == null || results.isEmpty()) {
            Label emptyLabel = new Label("Nu există date de comparație");
            emptyLabel.setAlignment(Pos.CENTER);
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            comparisonBox.getChildren().add(emptyLabel);
            return;
        }

        for (ComparisonResult cr : results) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER);
            row.setMaxWidth(Double.MAX_VALUE);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.getStyleClass().add("glass-card");

            Label type = new Label(cr.getTipComparatie() != null ? cr.getTipComparatie() : "Comparație");
            type.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f8fafc; -fx-min-width: 100px;");

            double diffMin = cr.getDiferentaTempMin();
            double diffMax = cr.getDiferentaTempMax();
            String diffText = String.format("Min: %+.1f°  Max: %+.1f°", diffMin, diffMax);

            Label diff = new Label(diffText);
            diff.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
            HBox.setHgrow(diff, Priority.ALWAYS);

            row.getChildren().addAll(type, diff);
            comparisonBox.getChildren().add(row);
        }
    }

    private void renderProbabilityCards(List<PredictionEngineService.MonteCarloResult> results) {
        probCardsBox.getChildren().clear();
        if (results == null || results.isEmpty()) {
            Label emptyLabel = new Label("Nu există date de probabilitate");
            emptyLabel.setAlignment(Pos.CENTER);
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            probCardsBox.getChildren().add(emptyLabel);
            return;
        }

        double maxPrecip = results.stream().mapToDouble(PredictionEngineService.MonteCarloResult::getPrecipProb).max().orElse(0);
        double maxStorm  = results.stream().mapToDouble(PredictionEngineService.MonteCarloResult::getStormProb).max().orElse(0);
        double maxFog    = results.stream().mapToDouble(PredictionEngineService.MonteCarloResult::getFogProb).max().orElse(0);
        double maxHeat   = results.stream().mapToDouble(PredictionEngineService.MonteCarloResult::getHeatwaveProb).max().orElse(0);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        grid.setMaxWidth(Double.MAX_VALUE);

        grid.add(createProbCard("🌧️", "Șanse ploaie", maxPrecip), 0, 0);
        grid.add(createProbCard("⛈️", "Risc furtună", maxStorm),  1, 0);
        grid.add(createProbCard("🌫️", "Risc ceață", maxFog),      0, 1);
        grid.add(createProbCard("🔥", "Risc caniculă", maxHeat),   1, 1);

        probCardsBox.getChildren().add(grid);
    }

    private void renderRankings(List<CityRanking> hottest, List<CityRanking> coldest,
                                List<CityRanking> windiest, List<CityRanking> mostHumid,
                                List<CityRanking> mostWarnings, List<CityRanking> mostExtreme) {
        rankingsBox.getChildren().clear();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        grid.add(createRankingMiniCard("Cele mai calde", "🔥", "#f97316", hottest), 0, 0);
        grid.add(createRankingMiniCard("Cele mai reci", "❄️", "#38bdf8", coldest), 1, 0);
        grid.add(createRankingMiniCard("Cele mai vântoase", "💨", "#a78bfa", windiest), 2, 0);
        grid.add(createRankingMiniCard("Cele mai umede", "💧", "#60a5fa", mostHumid), 0, 1);
        grid.add(createRankingMiniCard("Cele mai multe avertizări", "⚠️", "#ef4444", mostWarnings), 1, 1);
        grid.add(createRankingMiniCard("Cele mai extreme", "🌪️", "#f59e0b", mostExtreme), 2, 1);

        rankingsBox.getChildren().add(grid);
    }

    private VBox createRankingMiniCard(String title, String emoji, String color,
                                       List<CityRanking> rankings) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(10));
        card.getStyleClass().add("glass-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMinWidth(160);
        card.setMaxWidth(180);

        Label titleLbl = new Label(emoji + " " + title);
        titleLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        titleLbl.setMaxWidth(Double.MAX_VALUE);
        titleLbl.setAlignment(Pos.CENTER);
        card.getChildren().add(titleLbl);

        for (int i = 0; i < Math.min(3, rankings.size()); i++) {
            var r = rankings.get(i);
            String unit = r.getUnitate() != null ? r.getUnitate() : "";
            String text = String.format("%d. %s %.1f%s", i + 1, r.getOras(), r.getValoare(), unit);
            Label lbl = new Label(text);
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #f8fafc;");
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            card.getChildren().add(lbl);
        }

        if (rankings.isEmpty()) {
            Label empty = new Label("Fără date");
            empty.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setAlignment(Pos.CENTER);
            card.getChildren().add(empty);
        }

        return card;
    }

    private void renderSimilarCities(List<CityRanking> similar) {
        similarCitiesBox.getChildren().clear();

        if (similar == null || similar.isEmpty()) {
            Label msg = new Label("Nu există date suficiente pentru clasificare.");
            msg.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
            msg.setAlignment(Pos.CENTER);
            msg.setMaxWidth(Double.MAX_VALUE);
            similarCitiesBox.getChildren().add(msg);
            return;
        }

        for (int i = 0; i < Math.min(5, similar.size()); i++) {
            var r = similar.get(i);
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER);
            row.setMaxWidth(Double.MAX_VALUE);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.getStyleClass().add("glass-card");

            Label nameLbl = new Label(r.getOras());
            nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f8fafc; " +
                "-fx-min-width: 100px;");

            Label countryLbl = new Label(r.getTara());
            countryLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-min-width: 80px;");

            String unit = r.getUnitate() != null ? r.getUnitate() : "";
            String distText = String.format("%.2f %s", r.getValoare(), unit);
            Label distLbl = new Label(distText);
            distLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #f8fafc;");
            HBox.setHgrow(distLbl, Priority.ALWAYS);
            distLbl.setAlignment(Pos.CENTER_RIGHT);

            row.getChildren().addAll(nameLbl, countryLbl, distLbl);
            similarCitiesBox.getChildren().add(row);
        }
    }

    // ===================== UI HELPERS =====================

    private Node createHourlyCard(int hour, double temperature, String iconType) {
        VBox card = new VBox(4);
        card.getStyleClass().add("hourly-card");
        if (hour == 12) { card.getStyleClass().add("hourly-card-now"); }
        card.setAlignment(Pos.CENTER);

        Label timeLbl = new Label(String.format("%02d:00", hour));
        timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        Label emoji = new Label(iconToEmoji(iconType));
        emoji.setStyle("-fx-font-size: 22px;");

        Label temp = new Label(String.format("%.0f°", temperature));
        temp.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");

        card.getChildren().addAll(timeLbl, emoji, temp);
        return card;
    }

    private Node createDailyMiniCard(Forecast f, boolean isToday) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setMinWidth(75);
        card.getStyleClass().add("glass-card");
        if (isToday) {
            card.setStyle(card.getStyle() + " -fx-border-color: rgba(56,189,248,0.4); -fx-border-width: 1px; -fx-border-radius: 12px;");
        }

        String dayName = isToday ? "Azi" : f.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("ro"));
        dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);

        Label day = new Label(dayName);
        day.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");

        Label emoji = new Label(iconToEmoji(f.getIconType()));
        emoji.setStyle("-fx-font-size: 28px;");

        Label max = new Label(String.format("%.0f°", f.getTempMax()));
        max.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");

        Label min = new Label(String.format("%.0f°", f.getTempMin()));
        min.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        card.getChildren().addAll(day, emoji, max, min);
        return card;
    }

    private Node createDetailCell(String icon, String value, String label) {
        VBox cell = new VBox(4);
        cell.getStyleClass().add("weather-detail-cell");
        cell.setAlignment(Pos.CENTER);

        Label ic = new Label(icon);
        ic.getStyleClass().add("weather-detail-icon");
        ic.setMaxWidth(Double.MAX_VALUE);
        ic.setAlignment(Pos.CENTER);

        Label val = new Label(value);
        val.getStyleClass().add("weather-detail-value");
        val.setMaxWidth(Double.MAX_VALUE);
        val.setAlignment(Pos.CENTER);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("weather-detail-label");
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setAlignment(Pos.CENTER);

        cell.getChildren().addAll(ic, val, lbl);
        return cell;
    }

    private VBox createProbCard(String emoji, String label, double prob) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.getStyleClass().add("glass-card");
        card.setAlignment(Pos.CENTER);
        card.setMinWidth(180);
        card.setMaxWidth(240);
        card.setPrefWidth(220);

        Label ic = new Label(emoji);
        ic.setStyle("-fx-font-size: 28px;");
        ic.setAlignment(Pos.CENTER);
        ic.setMaxWidth(Double.MAX_VALUE);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
        lbl.setAlignment(Pos.CENTER);
        lbl.setMaxWidth(Double.MAX_VALUE);

        Label val = new Label(String.format("%.0f%%", prob * 100));
        String valColor = prob > 0.5 ? "#ef4444" : prob > 0.25 ? "#f59e0b" : "#22c55e";
        val.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + valColor + ";");
        val.setAlignment(Pos.CENTER);
        val.setMaxWidth(Double.MAX_VALUE);

        double pct = Math.max(0, Math.min(1, prob));
        String barColor = prob > 0.5 ? "#ef4444" : prob > 0.25 ? "#f59e0b" : "#22c55e";
        Region barFill = new Region();
        barFill.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 4px; -fx-min-height: 8px; -fx-pref-height: 8px; -fx-max-height: 8px;", barColor));
        barFill.setPrefWidth(pct * 180);
        barFill.setMinWidth(pct * 180);
        barFill.setMaxWidth(Double.MAX_VALUE);

        Region barBg = new Region();
        barBg.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 4px; -fx-min-height: 8px; -fx-pref-height: 8px; -fx-max-height: 8px;");
        barBg.setPrefWidth(180);
        barBg.setMinWidth(180);
        barBg.setMaxWidth(Double.MAX_VALUE);

        StackPane barPane = new StackPane(barBg, barFill);
        barPane.setAlignment(Pos.CENTER_LEFT);
        barPane.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(ic, lbl, val, barPane);
        return card;
    }

    private Button createComparisonToggleButton(String text, String type) {
        Button btn = new Button(text);
        btn.getStyleClass().add("button");
        updateComparisonToggleStyle(btn, type.equals(currentComparisonType));
        btn.setOnAction(e -> {
            currentComparisonType = type;
            if (comparisonToggleBox != null) {
                for (Node node : comparisonToggleBox.getChildren()) {
                    if (node instanceof Button b) {
                        updateComparisonToggleStyle(b, b == btn);
                    }
                }
            }
            City city = session.getSelectedCity();
            if (city != null) {
                showLoading(true);
                new Thread(() -> {
                    try {
                        List<ComparisonResult> comp;
                        LocalDate date = session.getSelectedDate();
                        if (date == null) { date = LocalDate.now(); }
                        switch (currentComparisonType) {
                            case "monthly" -> comp = forecastService.compareMonthly(city.getId(), date.getYear(), date.getMonthValue());
                            case "annual" -> comp = forecastService.compareAnnual(city.getId(), date.getYear());
                            default -> comp = forecastService.compareSameDay(city.getId(), date);
                        }
                        List<ComparisonResult> finalComp = comp;
                        Platform.runLater(() -> {
                            renderComparison(finalComp);
                            showLoading(false);
                        });
                    } catch (SQLException ex) {
                        Platform.runLater(() -> {
                            showError("Eroare comparație: " + ex.getMessage());
                            showLoading(false);
                        });
                    }
                }).start();
            }
        });
        return btn;
    }

    private void updateComparisonToggleStyle(Button btn, boolean selected) {
        if (selected) {
            btn.setStyle("-fx-background-color: rgba(56, 189, 248, 0.2);" +
                   "-fx-text-fill: #38bdf8; -fx-font-weight: bold;" +
                   "-fx-background-radius: 8px; -fx-padding: 6px 14px;" +
                   "-fx-border-color: rgba(56, 189, 248, 0.4); -fx-border-radius: 8px; -fx-border-width: 1px;" +
                   "-fx-cursor: hand;");
        } else {
            btn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.05);" +
                   "-fx-text-fill: #94a3b8;" +
                   "-fx-background-radius: 8px; -fx-padding: 6px 14px;" +
                   "-fx-border-color: transparent; -fx-cursor: hand;");
        }
    }

    private Button createRangeButton(String text, int days) {
        Button btn = new Button(text);
        btn.getStyleClass().add("button");
        btn.setOnAction(e -> {
            currentRangeDays = days;
            City city = session.getSelectedCity();
            if (city != null) {
                showLoading(true);
                new Thread(() -> {
                    LocalDate selDate = session.getSelectedDate();
                    final LocalDate end = selDate != null ? selDate : LocalDate.now();
                    final LocalDate start = end.minusDays(currentRangeDays);
                    try {
                        List<Forecast> evo = forecastService.getCityWeatherEvolution(city.getId(), start, end);
                        Platform.runLater(() -> {
                            renderEvolution(evo, start, end);
                            showLoading(false);
                        });
                    } catch (Exception ex) {
                        java.util.logging.Logger.getLogger(getClass().getName())
                            .warning("Eroare evoluție buton (" + days + " zile): " + ex.getMessage());
                        Platform.runLater(() -> {
                            renderEvolution(new ArrayList<>(), start, end);
                            showLoading(false);
                        });
                    }
                }).start();
            }
        });
        return btn;
    }

    private VBox wrapInChartCard(Node... children) {
        VBox card = new VBox(10);
        card.getStyleClass().add("chart-card");
        card.setPadding(new Insets(16));
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(600);
        card.getChildren().addAll(children);
        return card;
    }

    private void updateDateLabel() {
        LocalDate today = session.getSelectedDate();
        String dayName = today.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("ro"));
        String formatted = dayName.substring(0, 1).toUpperCase() + dayName.substring(1) + ", " +
                           today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ro")));
        dateLabel.setText(formatted);
    }

    private double fetchPressure(int cityId, LocalDate date) {
        String sql = "SELECT pressure_mean FROM forecasts WHERE city_id = ? AND date = ?";
        try (var conn = com.sgbd.util.DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, java.sql.Date.valueOf(date));
            var rs = stmt.executeQuery();
            if (rs.next()) {
                double p = rs.getDouble("pressure_mean");
                return rs.wasNull() ? 1013.0 : p;
            }
        } catch (Exception ignored) { }
        return 1013.0;
    }

    private String iconToEmoji(String icon) {
        if (icon == null) { return "🌤️"; }
        String i = icon.toLowerCase();
        if (i.contains("sun") || i.contains("senin") || i.contains("hot")) return "☀️";
        if (i.contains("snow") || i.contains("ninsoare")) return "❄️";
        if (i.contains("storm") || i.contains("furtun")) return "⛈️";
        if (i.contains("rain") || i.contains("ploaie")) return "🌧️";
        if (i.contains("cloud") || i.contains("innorat")) return "☁️";
        if (i.contains("wind") || i.contains("vant")) return "💨";
        if (i.contains("fog") || i.contains("ceata")) return "🌫️";
        return "🌤️";
    }

    private String iconToDesc(String icon) {
        if (icon == null) return "Cer variabil";
        String i = icon.toLowerCase();
        if (i.contains("sun") || i.contains("senin")) return "Senin";
        if (i.contains("snow")) return "Ninsoare";
        if (i.contains("storm")) return "Furtună";
        if (i.contains("rain")) return "Ploaie";
        if (i.contains("cloud")) return "Înnorat";
        if (i.contains("wind")) return "Vânt puternic";
        if (i.contains("fog")) return "Ceață";
        if (i.contains("hot")) return "Caniculă";
        return "Parțial înnorat";
    }

    private double computeFeelsLike(double temp, double windSpeed, int humidity) {
        double windChill = 13.12 + 0.6215 * temp - 11.37 * Math.pow(windSpeed, 0.16)
            + 0.3965 * temp * Math.pow(windSpeed, 0.16);
        double heatIndex = -8.784694755 + 1.61139411 * temp + 2.338548839 * humidity
            - 0.14611605 * temp * humidity - 0.012308094 * temp * temp
            - 0.016424828 * humidity * humidity + 0.002211732 * temp * temp * humidity
            + 0.00072546 * temp * humidity * humidity - 0.000003582 * temp * temp * humidity * humidity;
        if (temp <= 10 && windSpeed > 4.8) {
            return windChill;
        } else if (temp >= 27 && humidity >= 40) {
            return heatIndex;
        }
        return temp;
    }

    private double computeDewPoint(double temp, int humidity) {
        double a = 17.27;
        double b = 237.7;
        double alpha = ((a * temp) / (b + temp)) + Math.log(humidity / 100.0);
        return (b * alpha) / (a - alpha);
    }

    private void renderAnomalies(List<Anomaly> anomalies) {
        anomaliesBox.getChildren().clear();
        if (anomalies.isEmpty()) {
            Label emptyLabel = new Label("Nu au fost detectate anomalii pentru perioada selectată.");
            emptyLabel.setAlignment(Pos.CENTER);
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            anomaliesBox.getChildren().add(emptyLabel);
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("ro"));
        for (Anomaly a : anomalies) {
            VBox card = new VBox(8);
            card.getStyleClass().add("glass-card");
            card.setPadding(new Insets(12));
            card.setAlignment(Pos.CENTER);
            card.setMaxWidth(500);

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER);

            Label dateLbl = new Label(a.getData().format(fmt));
            dateLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");

            Label cityLbl = new Label(a.getOras());
            cityLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
            HBox.setHgrow(cityLbl, Priority.ALWAYS);

            header.getChildren().addAll(dateLbl, cityLbl);

            HBox badges = new HBox(6);
            badges.setAlignment(Pos.CENTER);

            if (a.isAnomalieTemperatura()) {
                badges.getChildren().add(createBadge("🔥 Temp", "#ef4444"));
            }
            if (a.isAnomalieVant()) {
                badges.getChildren().add(createBadge("💨 Vânt", "#a855f7"));
            }
            if (a.isAnomalieUmiditate()) {
                badges.getChildren().add(createBadge("💧 Umiditate", "#3b82f6"));
            }
            if (a.isAnomalieUV()) {
                badges.getChildren().add(createBadge("☀️ UV", "#f97316"));
            }

            card.getChildren().addAll(header, badges);

            if (a.getDetaliiAnomalie() != null && !a.getDetaliiAnomalie().isBlank()) {
                Label detalii = new Label(a.getDetaliiAnomalie());
                detalii.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1; -fx-wrap-text: true;");
                detalii.setMaxWidth(460);
                card.getChildren().add(detalii);
            }

            anomaliesBox.getChildren().add(card);
        }
    }

    private void renderErrorForecasts(List<Forecast> forecasts) {
        errorForecastsBox.getChildren().clear();
        if (forecasts.isEmpty()) {
            Label empty = new Label("Nu există prognoze contestate pentru acest oraș.");
            empty.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
            empty.setAlignment(Pos.CENTER);
            empty.setMaxWidth(Double.MAX_VALUE);
            errorForecastsBox.getChildren().add(empty);
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("ro"));
        for (Forecast f : forecasts) {
            HBox card = new HBox(12);
            card.getStyleClass().add("glass-card");
            card.setPadding(new Insets(12));
            card.setAlignment(Pos.CENTER);
            card.setMaxWidth(500);

            Label dateLbl = new Label(f.getDate().format(fmt));
            dateLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f8fafc; "
                + "-fx-min-width: 100px;");

            Label tempsLbl = new Label(String.format("↓ %.0f°  ↑ %.0f°", f.getTempMin(), f.getTempMax()));
            tempsLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #e2e8f0;");
            HBox.setHgrow(tempsLbl, Priority.ALWAYS);

            Label badge = createBadge("Contestată", "#ef4444");

            card.getChildren().addAll(dateLbl, tempsLbl, badge);
            errorForecastsBox.getChildren().add(card);
        }
    }

    private Label createBadge(String text, String color) {
        Label badge = new Label(text);
        badge.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: #ffffff; -fx-background-radius: 12px; "
            + "-fx-padding: 2px 8px; -fx-font-size: 11px; -fx-font-weight: bold;"
            , color));
        return badge;
    }
}
