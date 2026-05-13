package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.ComparisonResult;
import com.sgbd.model.Forecast;
import com.sgbd.service.CityService;
import com.sgbd.service.ForecastService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ComparisonController {
    private final CityService cityService = new CityService();
    private final ForecastService forecastService = new ForecastService();

    private ComboBox<City> cityCombo;
    private DatePicker datePicker;
    private ComboBox<String> compareType;
    private TextField monthField;
    private LineChart<String, Number> chart;
    private Label infoLabel;

    public Node getView() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        HBox filters = new HBox(10);
        cityCombo = new ComboBox<>();
        datePicker = new DatePicker(LocalDate.now());
        compareType = new ComboBox<>(FXCollections.observableArrayList(
            "Aceeași zi (ani diferiți)", "Sezonier", "Lunar", "Anual"));
        compareType.setValue("Aceeași zi (ani diferiți)");
        monthField = new TextField("1");
        monthField.setPrefWidth(50);
        Button compareBtn = new Button("Compară");

        filters.getChildren().addAll(
            new Label("Oraș:"), cityCombo,
            new Label("Dată:"), datePicker,
            new Label("Tip:"), compareType,
            new Label("Lună:"), monthField,
            compareBtn);

        infoLabel = new Label();

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Categorie");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Temperatură (°C)");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setPrefHeight(500);

        root.getChildren().addAll(filters, infoLabel, chart);

        loadCities();
        compareBtn.setOnAction(e -> runComparison());

        return root;
    }

    private void loadCities() {
        try {
            List<City> cities = cityService.getAllCities();
            cityCombo.setItems(FXCollections.observableArrayList(cities));
        } catch (SQLException e) {
            infoLabel.setText("Eroare: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void runComparison() {
        City city = cityCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (city == null || date == null) return;

        chart.getData().clear();

        try {
            String type = compareType.getValue();

            if ("Aceeași zi (ani diferiți)".equals(type) || "Sezonier".equals(type)) {
                List<ComparisonResult> results = forecastService.compareSameDay(city.getId(), date);

                XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
                actualSeries.setName("Actual");

                XYChart.Series<String, Number> avgSeries = new XYChart.Series<>();
                avgSeries.setName("Medie istorică");

                for (ComparisonResult cr : results) {
                    actualSeries.getData().add(new XYChart.Data<>(cr.getTipComparatie() + " min", cr.getTempMinActuala()));
                    actualSeries.getData().add(new XYChart.Data<>(cr.getTipComparatie() + " max", cr.getTempMaxActuala()));
                    avgSeries.getData().add(new XYChart.Data<>(cr.getTipComparatie() + " min", cr.getTempMinMedie()));
                    avgSeries.getData().add(new XYChart.Data<>(cr.getTipComparatie() + " max", cr.getTempMaxMedie()));
                }

                chart.getData().addAll(actualSeries, avgSeries);

            } else if ("Lunar".equals(type)) {
                int month;
                try {
                    month = Integer.parseInt(monthField.getText());
                } catch (NumberFormatException ex) {
                    infoLabel.setText("Luna trebuie să fie un număr între 1 și 12.");
                    return;
                }
                if (month < 1 || month > 12) {
                    infoLabel.setText("Luna trebuie să fie între 1 și 12.");
                    return;
                }
                int year = date.getYear();
                List<ComparisonResult> results = forecastService.compareMonthly(city.getId(), year, month);

                XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
                actualSeries.setName("Temp max actuală");
                XYChart.Series<String, Number> histSeries = new XYChart.Series<>();
                histSeries.setName("Temp max istorică");

                int day = 1;
                for (ComparisonResult cr : results) {
                    actualSeries.getData().add(new XYChart.Data<>(String.valueOf(day), cr.getTempMaxActuala()));
                    histSeries.getData().add(new XYChart.Data<>(String.valueOf(day), cr.getTempMaxMedie()));
                    day++;
                }

                chart.getData().addAll(actualSeries, histSeries);

            } else if ("Anual".equals(type)) {
                List<ComparisonResult> results = forecastService.compareAnnual(city.getId(), date.getYear());

                XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
                actualSeries.setName("Media an selectat");
                XYChart.Series<String, Number> histSeries = new XYChart.Series<>();
                histSeries.setName("Media istorică");

                for (ComparisonResult cr : results) {
                    actualSeries.getData().add(new XYChart.Data<>("Min", cr.getMedieAnSelectatTempMin()));
                    actualSeries.getData().add(new XYChart.Data<>("Max", cr.getMedieAnSelectatTempMax()));
                    actualSeries.getData().add(new XYChart.Data<>("Medie", cr.getMedieAnSelectatTempAvg()));
                    histSeries.getData().add(new XYChart.Data<>("Min", cr.getMedieIstoricaTempMin()));
                    histSeries.getData().add(new XYChart.Data<>("Max", cr.getMedieIstoricaTempMax()));
                    histSeries.getData().add(new XYChart.Data<>("Medie", cr.getMedieIstoricaTempAvg()));
                }

                chart.getData().addAll(actualSeries, histSeries);
            }

            infoLabel.setText("Comparație realizată pentru " + city.getName());

        } catch (SQLException e) {
            infoLabel.setText("Eroare: " + e.getMessage());
        }
    }
}
