package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.CityRanking;
import com.sgbd.model.Country;
import com.sgbd.service.CityService;
import com.sgbd.service.ExportService;
import com.sgbd.service.StatisticsService;
import javafx.stage.FileChooser;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.util.List;

public class RankingsController {
    private final CityService cityService = new CityService();
    private final StatisticsService statsService = new StatisticsService();
    private final ExportService exportService = new ExportService();
    private Button exportBtn;

    private ComboBox<String> criterionCombo;
    private ComboBox<City> cityCombo;
    private TableView<CityRanking> rankingTable;
    private TableView<CityRanking> similarTable;

    public Node getView() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        HBox filters = new HBox(10);
        criterionCombo = new ComboBox<>(FXCollections.observableArrayList(
            "hottest", "coldest", "windiest", "most_humid", "most_warnings", "most_extreme"));
        criterionCombo.setValue("hottest");
        cityCombo = new ComboBox<>();
        Button rankBtn = new Button("Clasament");
        Button similarBtn = new Button("Orașe similare");

        exportBtn = new Button("Export CSV");
        exportBtn.getStyleClass().addAll("button", "secondary");

        filters.getChildren().addAll(
            new Label("Clasament după:"), criterionCombo,
            new Label("Oraș referință (similare):"), cityCombo,
            rankBtn, similarBtn, exportBtn);

        TitledPane rankPane = new TitledPane();
        rankPane.setText("Clasament orașe");
        rankingTable = new TableView<>();
        setupRankingTable();
        rankPane.setContent(rankingTable);

        TitledPane similarPane = new TitledPane();
        similarPane.setText("Orașe cu prognoze similare");
        similarTable = new TableView<>();
        setupSimilarTable();
        similarPane.setContent(similarTable);

        root.getChildren().addAll(filters, rankPane, similarPane);

        loadAllCities();
        rankBtn.setOnAction(e -> loadRankings());
        similarBtn.setOnAction(e -> loadSimilar());
        exportBtn.setOnAction(e -> exportRankings());

        return root;
    }

    @SuppressWarnings("unchecked")
    private void setupRankingTable() {
        TableColumn<CityRanking, Long> pozCol = new TableColumn<>("Poziție");
        pozCol.setCellValueFactory(new PropertyValueFactory<>("pozitie"));
        pozCol.setPrefWidth(60);

        TableColumn<CityRanking, String> orasCol = new TableColumn<>("Oraș");
        orasCol.setCellValueFactory(new PropertyValueFactory<>("oras"));

        TableColumn<CityRanking, String> taraCol = new TableColumn<>("Țară");
        taraCol.setCellValueFactory(new PropertyValueFactory<>("tara"));

        TableColumn<CityRanking, Double> valCol = new TableColumn<>("Valoare");
        valCol.setCellValueFactory(new PropertyValueFactory<>("valoare"));

        TableColumn<CityRanking, String> unitCol = new TableColumn<>("Unitate");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unitate"));

        rankingTable.getColumns().addAll(pozCol, orasCol, taraCol, valCol, unitCol);
    }

    @SuppressWarnings("unchecked")
    private void setupSimilarTable() {
        TableColumn<CityRanking, String> orasCol = new TableColumn<>("Oraș");
        orasCol.setCellValueFactory(new PropertyValueFactory<>("oras"));

        TableColumn<CityRanking, String> taraCol = new TableColumn<>("Țară");
        taraCol.setCellValueFactory(new PropertyValueFactory<>("tara"));

        TableColumn<CityRanking, Double> distCol = new TableColumn<>("Distanță euclidiană");
        distCol.setCellValueFactory(new PropertyValueFactory<>("valoare"));

        similarTable.getColumns().addAll(orasCol, taraCol, distCol);
    }

    private void loadAllCities() {
        try {
            List<City> cities = cityService.getAllCities();
            cityCombo.setItems(FXCollections.observableArrayList(cities));
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Eroare: " + e.getMessage()).showAndWait();
        }
    }

    private void loadRankings() {
        try {
            List<CityRanking> rankings = statsService.getCityRankings(criterionCombo.getValue(), 30);
            rankingTable.setItems(FXCollections.observableArrayList(rankings));
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Eroare: " + e.getMessage()).showAndWait();
        }
    }

    private void loadSimilar() {
        City city = cityCombo.getValue();
        if (city == null) return;
        try {
            List<CityRanking> similar = statsService.classifySimilarCities(city.getId(), 30);
            similarTable.setItems(FXCollections.observableArrayList(similar));
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Eroare: " + e.getMessage()).showAndWait();
        }
    }

    private void exportRankings() {
        List<CityRanking> items = rankingTable.getItems();
        if (items == null || items.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Nu există clasamente de exportat!").showAndWait();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV clasamente");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("clasamente_" + java.time.LocalDate.now() + ".csv");
        java.io.File file = chooser.showSaveDialog(null);
        if (file == null) return;
        try {
            exportService.exportCityRankingsToCsv(items, file.toPath());
            new Alert(Alert.AlertType.INFORMATION, "✅ Exportat la: " + file.getAbsolutePath()).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Eroare export: " + ex.getMessage()).showAndWait();
        }
    }
}
