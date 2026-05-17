package com.sgbd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgbd.util.LoggerUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class WeatherApiService {
    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_DELAYS_MS = {2000, 4000, 8000};
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000;
    private static final int MAX_RETRY_AFTER_SECONDS = 30;

    private static volatile int consecutiveFailures = 0;
    private static volatile long lastFailureTime = 0;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Logger logger;

    public WeatherApiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.logger = LoggerUtil.getLogger(WeatherApiService.class);
    }

    public static class DailyWeather {
        public LocalDate date;
        public double tempMin;
        public double tempMax;
        public double windSpeed;
        public int uvIndex;
        public int humidity;
        public double precipSum;
        public double pressureMean;
        public double sunshineHours;
        public double precipitationHours;
        public double dewPoint;
    }

    public static class HourlyWeather {
        public LocalDate date;
        public int hour;
        public double temperature;
        public int humidity;
        public double windSpeed;
        public int precipProbability;
        public int weatherCode;
    }

    public List<DailyWeather> fetchHistorical(double lat, double lon, LocalDate start, LocalDate end) {
        checkCircuitBreaker();
        String url = String.format(
            "%s?latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s" +
            "&daily=temperature_2m_max,temperature_2m_min,wind_speed_10m_max,uv_index_max," +
            "precipitation_sum,precipitation_hours,pressure_msl_mean,sunshine_duration,dew_point_2m_mean," +
            "relative_humidity_2m_mean" +
            "&timezone=auto",
            ARCHIVE_URL, lat, lon, start, end
        );
        try {
            List<DailyWeather> result = fetchAndParse(url, start);
            resetCircuitBreaker();
            return result;
        } catch (RuntimeException e) {
            recordFailure();
            throw e;
        }
    }

    public List<DailyWeather> fetchForecast(double lat, double lon, int days) {
        checkCircuitBreaker();
        String url = String.format(
            "%s?latitude=%.4f&longitude=%.4f" +
            "&daily=temperature_2m_max,temperature_2m_min,wind_speed_10m_max,uv_index_max," +
            "precipitation_sum,precipitation_hours,pressure_msl_mean,sunshine_duration,dew_point_2m_mean," +
            "relative_humidity_2m_mean" +
            "&timezone=auto&forecast_days=%d",
            FORECAST_URL, lat, lon, days
        );
        try {
            List<DailyWeather> result = fetchAndParse(url, null);
            resetCircuitBreaker();
            return result;
        } catch (RuntimeException e) {
            recordFailure();
            throw e;
        }
    }

    public List<HourlyWeather> fetchHourlyForecast(double lat, double lon, int days) {
        checkCircuitBreaker();
        String url = String.format(
            "%s?latitude=%.4f&longitude=%.4f" +
            "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation_probability,weather_code" +
            "&timezone=auto&forecast_days=%d",
            FORECAST_URL, lat, lon, days
        );
        try {
            List<HourlyWeather> result = fetchAndParseHourly(url);
            resetCircuitBreaker();
            return result;
        } catch (RuntimeException e) {
            recordFailure();
            throw e;
        }
    }

    public static boolean isApiAvailable() {
        if (consecutiveFailures < CIRCUIT_BREAKER_THRESHOLD) {
            return true;
        }
        return System.currentTimeMillis() - lastFailureTime > CIRCUIT_BREAKER_TIMEOUT_MS;
    }

    private void checkCircuitBreaker() {
        if (!isApiAvailable()) {
            throw new IllegalStateException("API Open-Meteo indisponibil temporar");
        }
    }

    private static synchronized void recordFailure() {
        consecutiveFailures++;
        lastFailureTime = System.currentTimeMillis();
    }

    private static synchronized void resetCircuitBreaker() {
        consecutiveFailures = 0;
        lastFailureTime = 0;
    }

    private List<DailyWeather> fetchAndParse(String url, LocalDate startDate) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    long delayMs = getRetryAfterMs(response);
                    if (attempt < MAX_RETRIES) {
                        logger.warning("API rate limit (HTTP 429) la încercarea " + (attempt + 1) + 
                                      ", aștept " + delayMs + " ms înainte de retry");
                        Thread.sleep(delayMs);
                        continue;
                    }
                    throw new RuntimeException("API error: HTTP 429 - " + response.body());
                }

                if (response.statusCode() >= 500) {
                    if (attempt < MAX_RETRIES) {
                        long delayMs = BACKOFF_DELAYS_MS[attempt];
                        logger.warning("Eroare server API (HTTP " + response.statusCode() + 
                                      ") la încercarea " + (attempt + 1) + 
                                      ", retry peste " + delayMs + " ms");
                        Thread.sleep(delayMs);
                        continue;
                    }
                    throw new RuntimeException("API error: HTTP " + response.statusCode() + " - " + response.body());
                }

                if (response.statusCode() != 200) {
                    throw new RuntimeException("API error: HTTP " + response.statusCode() + " - " + response.body());
                }

                return parseResponse(response.body(), startDate);

            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delayMs = BACKOFF_DELAYS_MS[attempt];
                    logger.warning("Eroare IO la API (încercarea " + (attempt + 1) + 
                                  "): " + e.getMessage() + ", retry peste " + delayMs + " ms");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry întrerupt", ie);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Cerere API întreruptă", e);
            }
        }

        if (lastException != null) {
            throw new RuntimeException("API request failed după " + (MAX_RETRIES + 1) + " încercări: " + lastException.getMessage(), lastException);
        }
        throw new RuntimeException("API request failed după " + (MAX_RETRIES + 1) + " încercări");
    }

    private long getRetryAfterMs(HttpResponse<String> response) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                int seconds = Integer.parseInt(retryAfter.trim());
                return Math.min(seconds, MAX_RETRY_AFTER_SECONDS) * 1000L;
            } catch (NumberFormatException e) {
                // Ignoră valoarea invalidă, folosește delay default
            }
        }
        return BACKOFF_DELAYS_MS[0];
    }

    private List<DailyWeather> parseResponse(String body, LocalDate startDate) throws IOException {
        List<DailyWeather> result = new ArrayList<>();

        JsonNode root = objectMapper.readTree(body);
        JsonNode daily = root.get("daily");
        if (daily == null) throw new RuntimeException("No 'daily' block in API response");

        JsonNode dates = daily.get("time");
        JsonNode tmax = daily.get("temperature_2m_max");
        JsonNode tmin = daily.get("temperature_2m_min");
        JsonNode wind = daily.get("wind_speed_10m_max");
        JsonNode uv = daily.get("uv_index_max");
        JsonNode hum = daily.get("relative_humidity_2m_mean");
        JsonNode precip = daily.get("precipitation_sum");
        JsonNode precipHours = daily.get("precipitation_hours");
        JsonNode pressure = daily.get("pressure_msl_mean");
        JsonNode sunshine = daily.get("sunshine_duration");
        JsonNode dew = daily.get("dew_point_2m_mean");

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

            if (precipHours != null && !precipHours.get(i).isNull()) {
                dw.precipitationHours = precipHours.get(i).asDouble();
            } else {
                dw.precipitationHours = 0;
            }

            if (pressure != null && !pressure.get(i).isNull()) {
                dw.pressureMean = pressure.get(i).asDouble();
            } else {
                dw.pressureMean = 1013.25;
            }

            if (sunshine != null && !sunshine.get(i).isNull()) {
                dw.sunshineHours = sunshine.get(i).asDouble() / 3600.0;
            } else {
                dw.sunshineHours = 0;
            }

            if (dew != null && !dew.get(i).isNull()) {
                dw.dewPoint = dew.get(i).asDouble();
            } else {
                dw.dewPoint = (dw.tempMin + dw.tempMax) / 2.0;
            }

            result.add(dw);
        }

        return result;
    }

    private List<HourlyWeather> fetchAndParseHourly(String url) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429) {
                    long delayMs = getRetryAfterMs(response);
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(delayMs);
                        continue;
                    }
                    throw new RuntimeException("API error: HTTP 429");
                }
                if (response.statusCode() >= 500) {
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(BACKOFF_DELAYS_MS[attempt]);
                        continue;
                    }
                    throw new RuntimeException("API error: HTTP " + response.statusCode());
                }
                if (response.statusCode() != 200) {
                    throw new RuntimeException("API error: HTTP " + response.statusCode());
                }
                return parseHourlyResponse(response.body());
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(BACKOFF_DELAYS_MS[attempt]); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry întrerupt", ie);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Cerere API întreruptă", e);
            }
        }
        throw new RuntimeException("API request failed după " + (MAX_RETRIES + 1) + " încercări", lastException);
    }

    private List<HourlyWeather> parseHourlyResponse(String body) throws IOException {
        List<HourlyWeather> result = new ArrayList<>();
        JsonNode root = objectMapper.readTree(body);
        JsonNode hourly = root.get("hourly");
        if (hourly == null) throw new RuntimeException("No 'hourly' block in API response");

        JsonNode times = hourly.get("time");
        JsonNode temps = hourly.get("temperature_2m");
        JsonNode hums = hourly.get("relative_humidity_2m");
        JsonNode winds = hourly.get("wind_speed_10m");
        JsonNode precips = hourly.get("precipitation_probability");
        JsonNode codes = hourly.get("weather_code");

        if (times == null || temps == null) {
            throw new RuntimeException("Missing required hourly fields in API response");
        }

        for (int i = 0; i < times.size(); i++) {
            HourlyWeather hw = new HourlyWeather();
            String ts = times.get(i).asText();
            hw.date = LocalDate.parse(ts.substring(0, 10));
            hw.hour = Integer.parseInt(ts.substring(11, 13));
            hw.temperature = temps.get(i).isNull() ? 0 : temps.get(i).asDouble();
            hw.humidity = hums != null && !hums.get(i).isNull() ? hums.get(i).asInt() : 0;
            hw.windSpeed = winds != null && !winds.get(i).isNull() ? winds.get(i).asDouble() : 0;
            hw.precipProbability = precips != null && !precips.get(i).isNull() ? precips.get(i).asInt() : 0;
            hw.weatherCode = codes != null && !codes.get(i).isNull() ? codes.get(i).asInt() : 0;
            result.add(hw);
        }
        return result;
    }
}
