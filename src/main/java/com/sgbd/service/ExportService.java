package com.sgbd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sgbd.model.City;
import com.sgbd.model.CityRanking;
import com.sgbd.service.prediction.AccuracyMetrics;
import com.sgbd.service.prediction.CityAccuracyRanking;
import com.sgbd.service.prediction.PredictionEngineService;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviciu pentru exportul datelor în formate CSV și JSON.
 * Folosește Apache Commons CSV și Jackson.
 */
public class ExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ObjectMapper objectMapper;

    public ExportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Exportă metricile de acuratețe într-un fișier CSV.
     * Coloane: data, orizont, tempPrezisa, tempReala, mae, rmse, bias, eroareVant, eroareUmiditate.
     */
    public void exportAccuracyToCsv(List<AccuracyMetrics> metrics, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                 .setHeader("data", "orizont", "tempPrezisa", "tempReala",
                              "mae", "rmse", "bias", "eroareVant", "eroareUmiditate")
                 .build())) {
            for (AccuracyMetrics m : metrics) {
                printer.printRecord(
                    m.getDate() != null ? m.getDate().format(DATE_FMT) : "",
                    m.getHorizonDay(),
                    String.format("%.2f", m.getPredictedTempMaxP50()),
                    String.format("%.2f", m.getActualTempMax()),
                    String.format("%.4f", m.getMaeTempMax()),
                    String.format("%.4f", m.getRmseTempMax()),
                    String.format("%.4f", m.getBiasTempMax()),
                    String.format("%.2f", m.getWindError()),
                    String.format("%.2f", m.getHumidityError())
                );
            }
        }
    }

    /**
     * Exportă metricile de acuratețe într-un fișier JSON (array de obiecte).
     */
    public void exportAccuracyToJson(List<AccuracyMetrics> metrics, Path filePath) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AccuracyMetrics m : metrics) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("data", m.getDate() != null ? m.getDate().format(DATE_FMT) : null);
            row.put("orizont", m.getHorizonDay());
            row.put("tempPrezisa", m.getPredictedTempMaxP50());
            row.put("tempReala", m.getActualTempMax());
            row.put("mae", m.getMaeTempMax());
            row.put("rmse", m.getRmseTempMax());
            row.put("bias", m.getBiasTempMax());
            row.put("eroareVant", m.getWindError());
            row.put("eroareUmiditate", m.getHumidityError());
            rows.add(row);
        }
        objectMapper.writeValue(filePath.toFile(), rows);
    }

    /**
     * Exportă predicțiile Monte Carlo într-un fișier JSON.
     * Include P10/P50/P90, probabilități și ensemble spread.
     */
    public void exportPredictionsToJson(City city,
                                        List<PredictionEngineService.MonteCarloResult> results,
                                        Path filePath) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PredictionEngineService.MonteCarloResult r : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("data", r.getForecastDate() != null ? r.getForecastDate().format(DATE_FMT) : null);
            row.put("orizont", r.getHorizonDay());
            row.put("tempMinP10", r.getTempMinP10());
            row.put("tempMinP50", r.getTempMinP50());
            row.put("tempMinP90", r.getTempMinP90());
            row.put("tempMaxP10", r.getTempMaxP10());
            row.put("tempMaxP50", r.getTempMaxP50());
            row.put("tempMaxP90", r.getTempMaxP90());
            row.put("windSpeedP50", r.getWindSpeedP50());
            row.put("humidityP50", r.getHumidityP50());
            row.put("precipProb", r.getPrecipProb());
            row.put("stormProb", r.getStormProb());
            row.put("fogProb", r.getFogProb());
            row.put("heatwaveProb", r.getHeatwaveProb());
            row.put("ensembleSpread", r.getEnsembleSpread());
            rows.add(row);
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("oras", city != null ? city.getName() : null);
        wrapper.put("orasId", city != null ? city.getId() : null);
        wrapper.put("dataExport", java.time.LocalDate.now().format(DATE_FMT));
        wrapper.put("predictii", rows);

        objectMapper.writeValue(filePath.toFile(), wrapper);
    }

    /**
     * Exportă clasamentul orașelor (acuratețe) într-un fișier CSV.
     */
    public void exportRankingsToCsv(List<CityAccuracyRanking> rankings, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                 .setHeader("pozitie", "oras", "mae", "rmse", "totalComparatii")
                 .build())) {
            int pozitie = 1;
            for (CityAccuracyRanking r : rankings) {
                printer.printRecord(
                    pozitie++,
                    r.getCityName(),
                    String.format("%.4f", r.getMae()),
                    String.format("%.4f", r.getRmse()),
                    r.getTotalComparisons()
                );
            }
        }
    }

    /**
     * Exportă clasamentul orașelor (model CityRanking) într-un fișier CSV.
     * Utilitar pentru exportul direct al datelor afișate în tabel.
     */
    public void exportCityRankingsToCsv(List<CityRanking> rankings, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                 .setHeader("pozitie", "oras", "tara", "valoare", "unitate")
                 .build())) {
            for (CityRanking r : rankings) {
                printer.printRecord(
                    r.getPozitie(),
                    r.getOras(),
                    r.getTara(),
                    String.format("%.4f", r.getValoare()),
                    r.getUnitate()
                );
            }
        }
    }
}
