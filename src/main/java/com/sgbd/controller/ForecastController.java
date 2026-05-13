package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.Forecast;
import com.sgbd.service.CityService;
import com.sgbd.service.ForecastService;
import com.sgbd.service.WeatherImporterService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ForecastController {
    private final CityService cityService = new CityService();
    private final ForecastService forecastService = new ForecastService();

    private ComboBox<City> cityCombo;
    private DatePicker datePicker;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private TableView<Forecast> forecastTable;
    private Label statusLabel;
    private ProgressBar progressBar;

    public Node getView() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        HBox filters = new HBox(10);
        cityCombo = new ComboBox<>();
        datePicker = new DatePicker(LocalDate.now());
        Button loadBtn = new Button("Încarcă prognoza");

        filters.getChildren().addAll(
            new Label("Oraș:"), cityCombo,
            new Label("Dată:"), datePicker,
            loadBtn
        );

        HBox importRow = new HBox(10);
        startDatePicker = new DatePicker(LocalDate.now().minusYears(2));
        endDatePicker = new DatePicker(LocalDate.now());
        Button importHistoricalBtn = new Button("Importă date istorice");
        Button importForecastBtn = new Button("Importă prognoza API");
        Button importAllHistoricalBtn = new Button("Importă istoric TOATE orașele");
        Button importAllForecastBtn = new Button("Importă prognoză TOATE orașele");

        importRow.getChildren().addAll(
            new Label("De la:"), startDatePicker,
            new Label("Până la:"), endDatePicker,
            importHistoricalBtn, importForecastBtn,
            new Separator(),
            importAllHistoricalBtn, importAllForecastBtn
        );

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-weight: bold;");

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(300);

        HBox statusRow = new HBox(10, progressBar, statusLabel);

        forecastTable = new TableView<>();
        setupTable();

        root.getChildren().addAll(filters, new Separator(), importRow, statusRow, forecastTable);

        loadCities();
        cityCombo.setOnAction(e -> loadForecast());
        loadBtn.setOnAction(e -> loadForecast());

        importHistoricalBtn.setOnAction(e -> runImportHistoricalForCity());
        importForecastBtn.setOnAction(e -> runImportForecastForCity());
        importAllHistoricalBtn.setOnAction(e -> runImportHistoricalAll());
        importAllForecastBtn.setOnAction(e -> runImportForecastAll());

        return root;
    }

    @SuppressWarnings("unchecked")
    private void setupTable() {
        TableColumn<Forecast, LocalDate> dateCol = new TableColumn<>("Data");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(100);

        TableColumn<Forecast, Double> tminCol = new TableColumn<>("Temp. Min (°C)");
        tminCol.setCellValueFactory(new PropertyValueFactory<>("tempMin"));
        tminCol.setPrefWidth(100);

        TableColumn<Forecast, Double> tmaxCol = new TableColumn<>("Temp. Max (°C)");
        tmaxCol.setCellValueFactory(new PropertyValueFactory<>("tempMax"));
        tmaxCol.setPrefWidth(100);

        TableColumn<Forecast, Double> windCol = new TableColumn<>("Vânt (km/h)");
        windCol.setCellValueFactory(new PropertyValueFactory<>("windSpeed"));
        windCol.setPrefWidth(90);

        TableColumn<Forecast, String> iconCol = new TableColumn<>("Pictogramă");
        iconCol.setCellValueFactory(new PropertyValueFactory<>("iconType"));
        iconCol.setPrefWidth(110);

        TableColumn<Forecast, Integer> uvCol = new TableColumn<>("UV");
        uvCol.setCellValueFactory(new PropertyValueFactory<>("uvIndex"));
        uvCol.setPrefWidth(50);

        TableColumn<Forecast, Integer> humCol = new TableColumn<>("Umiditate (%)");
        humCol.setCellValueFactory(new PropertyValueFactory<>("humidity"));
        humCol.setPrefWidth(100);

        TableColumn<Forecast, String> warnCol = new TableColumn<>("Avertizare");
        warnCol.setCellValueFactory(new PropertyValueFactory<>("warningText"));
        warnCol.setPrefWidth(350);

        forecastTable.getColumns().addAll(dateCol, tminCol, tmaxCol, windCol, iconCol, uvCol, humCol, warnCol);
    }

    private void loadCities() {
        try {
            List<City> cities = cityService.getAllCities();
            cityCombo.setItems(FXCollections.observableArrayList(cities));
        } catch (SQLException e) {
            statusLabel.setText("Eroare: " + e.getMessage());
        }
    }

    private void loadForecast() {
        City city = cityCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (city == null || date == null) return;

        try {
            List<Forecast> forecasts = forecastService.getForecastsByCity(city.getId(), date, date.plusDays(6));
            forecastTable.setItems(FXCollections.observableArrayList(forecasts));

            Forecast report = forecastService.getDailyReport(city.getId(), date);
            if (report != null && report.getWarningText() != null) {
                statusLabel.setText("⚠ " + report.getWarningText());
            } else {
                statusLabel.setText("");
            }
        } catch (SQLException e) {
            statusLabel.setText("Eroare: " + e.getMessage());
        }
    }

    private void runImportHistoricalForCity() {
        City city = cityCombo.getValue();
        if (city == null) { statusLabel.setText("Selectează un oraș!"); return; }
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null) { statusLabel.setText("Selectează perioada!"); return; }

        showProgress(true);
        new Thread(() -> {
            WeatherImporterService.ImportResult r = forecastService.importHistorical(
                city.getId(), city.getLatitude(), city.getLongitude(), start, end);
            Platform.runLater(() -> {
                statusLabel.setText("Import istoric: " + r.imported + " înregistrări, " + r.errors + " erori");
                showProgress(false);
                loadForecast();
            });
        }).start();
    }

    private void runImportForecastForCity() {
        City city = cityCombo.getValue();
        if (city == null) { statusLabel.setText("Selectează un oraș!"); return; }

        showProgress(true);
        new Thread(() -> {
            WeatherImporterService.ImportResult r = forecastService.importForecast(
                city.getId(), city.getLatitude(), city.getLongitude(), 10);
            Platform.runLater(() -> {
                statusLabel.setText("Import prognoză API: " + r.imported + " zile, " + r.errors + " erori");
                showProgress(false);
                loadForecast();
            });
        }).start();
    }

    private void runImportHistoricalAll() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null) { statusLabel.setText("Selectează perioada!"); return; }

        showProgress(true);
        statusLabel.setText("Importă date istorice pentru toate orașele...");

        new Thread(() -> {
            WeatherImporterService.ImportResult r = forecastService.importHistoricalAll(start, end);
            Platform.runLater(() -> {
                statusLabel.setText("Import istoric TOTAL: " + r.imported + " înregistrări, " + r.errors + " erori");
                showProgress(false);
                loadForecast();
            });
        }).start();
    }

    private void runImportForecastAll() {
        showProgress(true);
        statusLabel.setText("Importă prognoză API pentru toate orașele...");

        new Thread(() -> {
            WeatherImporterService.ImportResult r = forecastService.importForecastAll(10);
            Platform.runLater(() -> {
                statusLabel.setText("Import prognoză TOTAL: " + r.imported + " zile, " + r.errors + " erori");
                showProgress(false);
                loadForecast();
            });
        }).start();
    }

    private void showProgress(boolean show) {
        progressBar.setVisible(show);
        progressBar.setProgress(show ? ProgressBar.INDETERMINATE_PROGRESS : 0);
    }
}
