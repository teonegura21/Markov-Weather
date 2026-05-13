package com.sgbd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WeatherApiService {
    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WeatherApiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public static class DailyWeather {
        public LocalDate date;
        public double tempMin;
        public double tempMax;
        public double windSpeed;
        public int uvIndex;
        public int humidity;
        public double precipSum;
    }

    public List<DailyWeather> fetchHistorical(double lat, double lon, LocalDate start, LocalDate end) {
        String url = String.format(
            "%s?latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s" +
            "&daily=temperature_2m_max,temperature_2m_min,wind_speed_10m_max,uv_index_max,precipitation_sum,relative_humidity_2m_mean" +
            "&timezone=auto",
            ARCHIVE_URL, lat, lon, start, end
        );
        return fetchAndParse(url, start);
    }

    public List<DailyWeather> fetchForecast(double lat, double lon, int days) {
        String url = String.format(
            "%s?latitude=%.4f&longitude=%.4f" +
            "&daily=temperature_2m_max,temperature_2m_min,wind_speed_10m_max,uv_index_max,precipitation_sum,relative_humidity_2m_mean" +
            "&timezone=auto&forecast_days=%d",
            FORECAST_URL, lat, lon, days
        );
        return fetchAndParse(url, null);
    }

    private List<DailyWeather> fetchAndParse(String url, LocalDate startDate) {
        List<DailyWeather> result = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("API error: HTTP " + response.statusCode() + " - " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode daily = root.get("daily");
            if (daily == null) throw new RuntimeException("No 'daily' block in API response");

            JsonNode dates = daily.get("time");
            JsonNode tmax = daily.get("temperature_2m_max");
            JsonNode tmin = daily.get("temperature_2m_min");
            JsonNode wind = daily.get("wind_speed_10m_max");
            JsonNode uv = daily.get("uv_index_max");
            JsonNode hum = daily.get("relative_humidity_2m_mean");
            JsonNode precip = daily.get("precipitation_sum");

            if (dates == null || tmax == null || tmin == null) {
                throw new RuntimeException("Missing required fields in API response");
            }

            for (int i = 0; i < dates.size(); i++) {
                DailyWeather dw = new DailyWeather();
                dw.date = LocalDate.parse(dates.get(i).asText());
                dw.tempMax = tmax.get(i).isNull() ? 0 : tmax.get(i).asDouble();
                dw.tempMin = tmin.get(i).isNull() ? 0 : tmin.get(i).asDouble();
                dw.windSpeed = wind != null && !wind.get(i).isNull() ? wind.get(i).asDouble() : 0;

                if (uv != null && !uv.get(i).isNull()) {
                    dw.uvIndex = (int) Math.round(uv.get(i).asDouble());
                } else {
                    dw.uvIndex = 0;
                }

                if (hum != null && !hum.get(i).isNull()) {
                    dw.humidity = (int) Math.round(hum.get(i).asDouble());
                } else {
                    dw.humidity = 0;
                }

                if (precip != null && !precip.get(i).isNull()) {
                    dw.precipSum = precip.get(i).asDouble();
                } else {
                    dw.precipSum = 0;
                }

                result.add(dw);
            }

        } catch (java.io.IOException | InterruptedException e) {
            throw new RuntimeException("API request failed: " + e.getMessage(), e);
        }

        return result;
    }
}
