package com.sgbd.controller;

import com.sgbd.model.Anomaly;
import com.sgbd.model.City;
import com.sgbd.model.Forecast;
import com.sgbd.service.CityService;
import com.sgbd.service.StatisticsService;
import com.sgbd.util.ValidationUtil;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class StatsController {
    private final CityService cityService = new CityService();
    private final StatisticsService statsService = new StatisticsService();

    private ComboBox<City> cityCombo;
    private TextField yearField;
    private TableView<Anomaly> anomalyTable;
    private TableView<Forecast> errorTable;

    public Node getView() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        HBox filters = new HBox(10);
        cityCombo = new ComboBox<>();
        yearField = new TextField(String.valueOf(LocalDate.now().getYear()));
        yearField.setPrefWidth(70);
        Button detectBtn = new Button("Detectează anomalii");
        Button errorBtn = new Button("Prognoze eronate");

        filters.getChildren().addAll(
            new Label("Oraș (opțional):"), cityCombo,
            new Label("An:"), yearField,
            detectBtn, errorBtn);

        TitledPane anomalyPane = new TitledPane();
        anomalyPane.setText("Anomalii detectate");
        anomalyTable = new TableView<>();
        setupAnomalyTable();
        anomalyPane.setContent(anomalyTable);

        TitledPane errorPane = new TitledPane();
        errorPane.setText("Prognoze cu erori mari");
        errorTable = new TableView<>();
        setupErrorTable();
        errorPane.setContent(errorTable);

        root.getChildren().addAll(filters, anomalyPane, errorPane);

        loadCities();
        detectBtn.setOnAction(e -> detectAnomalies());
        errorBtn.setOnAction(e -> detectErrors());

        return root;
    }

    @SuppressWarnings("unchecked")
    private void setupAnomalyTable() {
        TableColumn<Anomaly, String> orasCol = new TableColumn<>("Oraș");
        orasCol.setCellValueFactory(new PropertyValueFactory<>("oras"));

        TableColumn<Anomaly, String> taraCol = new TableColumn<>("Țară");
        taraCol.setCellValueFactory(new PropertyValueFactory<>("tara"));

        TableColumn<Anomaly, LocalDate> dataCol = new TableColumn<>("Data");
        dataCol.setCellValueFactory(new PropertyValueFactory<>("data"));

        TableColumn<Anomaly, Boolean> tempCol = new TableColumn<>("Anomalie Temp");
        tempCol.setCellValueFactory(new PropertyValueFactory<>("anomalieTemperatura"));

        TableColumn<Anomaly, Boolean> vantCol = new TableColumn<>("Anomalie Vânt");
        vantCol.setCellValueFactory(new PropertyValueFactory<>("anomalieVant"));

        TableColumn<Anomaly, Boolean> umidCol = new TableColumn<>("Anomalie Umid.");
        umidCol.setCellValueFactory(new PropertyValueFactory<>("anomalieUmiditate"));

        TableColumn<Anomaly, Boolean> uvCol = new TableColumn<>("Anomalie UV");
        uvCol.setCellValueFactory(new PropertyValueFactory<>("anomalieUV"));

        TableColumn<Anomaly, String> detailCol = new TableColumn<>("Detalii");
        detailCol.setCellValueFactory(new PropertyValueFactory<>("detaliiAnomalie"));
        detailCol.setPrefWidth(400);

        anomalyTable.getColumns().addAll(orasCol, taraCol, dataCol, tempCol, vantCol, umidCol, uvCol, detailCol);
    }

    @SuppressWarnings("unchecked")
    private void setupErrorTable() {
        TableColumn<Forecast, String> orasCol = new TableColumn<>("Oraș");
        orasCol.setCellValueFactory(new PropertyValueFactory<>("cityName"));

        TableColumn<Forecast, LocalDate> dataCol = new TableColumn<>("Data");
        dataCol.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<Forecast, Long> votCol = new TableColumn<>("Voturi totale");
        votCol.setCellValueFactory(new PropertyValueFactory<>("voteCount"));

        errorTable.getColumns().addAll(orasCol, dataCol, votCol);
    }

    private void loadCities() {
        try {
            List<City> cities = cityService.getAllCities();
            cityCombo.setItems(FXCollections.observableArrayList(cities));
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Eroare: " + e.getMessage()).showAndWait();
        }
    }

    private void detectAnomalies() {
        Integer cityId = null;
        City city = cityCombo.getValue();
        if (city != null) cityId = city.getId();

        int year;
        try {
            year = Integer.parseInt(yearField.getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Anul trebuie să fie un număr valid.").showAndWait();
            return;
        }

        String err = ValidationUtil.validateRange(year, 1900, LocalDate.now().getYear() + 1, "Anul");
        if (err != null) {
            new Alert(Alert.AlertType.ERROR, err).showAndWait();
            return;
        }

        try {
            List<Anomaly> anomalies = statsService.detectAnomalies(cityId, year);
            anomalyTable.setItems(FXCollections.observableArrayList(anomalies));
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Eroare: " + e.getMessage()).showAndWait();
        }
    }

    private void detectErrors() {
        Integer cityId = null;
        City city = cityCombo.getValue();
        if (city != null) cityId = city.getId();

        try {
            List<Forecast> errors = statsService.identifyErrorForecasts(cityId, 50.0);
            errorTable.setItems(FXCollections.observableArrayList(errors));
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Eroare: " + e.getMessage()).showAndWait();
        }
    }
}
