package com.sgbd.util;

/**
 * Utilitar pentru determinarea gradientului de fundal în funcție de condițiile meteo.
 */
public class WeatherGradient {

    public static String getStyleForIcon(String icon) {
        if (icon == null) return "weather-clear";
        String i = icon.toLowerCase();

        if (i.contains("storm") || i.contains("furtun") || i.contains("thunder")) {
            return "weather-storm";
        }
        if (i.contains("rain") || i.contains("ploaie")) {
            return "weather-rain";
        }
        if (i.contains("snow") || i.contains("ninsoare")) {
            return "weather-snow";
        }
        if (i.contains("fog") || i.contains("ceata") || i.contains("cloud") || i.contains("innorat")) {
            return "weather-cloudy";
        }
        if (i.contains("night") || i.contains("noapte")) {
            return "weather-night";
        }
        return "weather-clear";
    }

    public static String getGradientStyle(String weatherStyle) {
        return switch (weatherStyle) {
            case "weather-storm" ->
                "-fx-background-color: linear-gradient(to bottom, #312e81, #1e1b4b);";
            case "weather-rain" ->
                "-fx-background-color: linear-gradient(to bottom, #1e3a5f, #0f172a);";
            case "weather-snow" ->
                "-fx-background-color: linear-gradient(to bottom, #475569, #1e293b);";
            case "weather-cloudy" ->
                "-fx-background-color: linear-gradient(to bottom, #334155, #1e293b);";
            case "weather-night" ->
                "-fx-background-color: linear-gradient(to bottom, #0c1220, #020617);";
            default ->
                "-fx-background-color: linear-gradient(to bottom, #0c4a6e, #0f172a);";
        };
    }
}
