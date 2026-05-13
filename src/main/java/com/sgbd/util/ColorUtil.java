package com.sgbd.util;

import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Utilitar pentru maparea temperaturilor si a conditiilor meteo la culori.
 * Ofera gradiente moderne si functionalitati de interpolare.
 */
public final class ColorUtil {

    private ColorUtil() {}

    /** Temperatura minima pentru scara de culori. */
    public static final double TEMP_MIN = -20.0;
    /** Temperatura maxima pentru scara de culori. */
    public static final double TEMP_MAX = 45.0;

    /**
     * Returneaza o culoare bazata pe temperatura.
     * Scara: albastru inchis (-20°C) → cyan → verde → galben → portocaliu → rosu (45°C)
     *
     * @param temp temperatura in grade Celsius
     * @return culoarea corespunzatoare
     */
    public static Color temperatureToColor(double temp) {
        double t = Math.max(TEMP_MIN, Math.min(TEMP_MAX, temp));
        double ratio = (t - TEMP_MIN) / (TEMP_MAX - TEMP_MIN);

        if (ratio < 0.17) {
            return interpolate(Color.web("#3b82f6"), Color.web("#06b6d4"), ratio / 0.17);
        } else if (ratio < 0.33) {
            return interpolate(Color.web("#06b6d4"), Color.web("#22c55e"), (ratio - 0.17) / 0.16);
        } else if (ratio < 0.50) {
            return interpolate(Color.web("#22c55e"), Color.web("#eab308"), (ratio - 0.33) / 0.17);
        } else if (ratio < 0.67) {
            return interpolate(Color.web("#eab308"), Color.web("#f97316"), (ratio - 0.50) / 0.17);
        } else if (ratio < 0.83) {
            return interpolate(Color.web("#f97316"), Color.web("#ef4444"), (ratio - 0.67) / 0.17);
        } else {
            return interpolate(Color.web("#ef4444"), Color.web("#991b1b"), (ratio - 0.83) / 0.17);
        }
    }

    /**
     * Returneaza un gradient liniar pentru fundalul unui panel
     * in functie de regimul meteo dominant.
     */
    public static LinearGradient regimeGradient(String regime) {
        if (regime == null) regime = "normal";
        return switch (regime.toLowerCase()) {
            case "canicula", "heatwave" ->
                new LinearGradient(0, 0, 1, 1, true, null,
                    new Stop(0, Color.web("#f97316")), new Stop(1, Color.web("#ef4444")));
            case "furtuna", "storm", "thunderstorm" ->
                new LinearGradient(0, 0, 1, 1, true, null,
                    new Stop(0, Color.web("#6366f1")), new Stop(1, Color.web("#a855f7")));
            case "ceață", "fog" ->
                new LinearGradient(0, 0, 1, 1, true, null,
                    new Stop(0, Color.web("#94a3b8")), new Stop(1, Color.web("#cbd5e1")));
            case "ninsoare", "snow" ->
                new LinearGradient(0, 0, 1, 1, true, null,
                    new Stop(0, Color.web("#e2e8f0")), new Stop(1, Color.web("#bfdbfe")));
            case "ploaie", "rain" ->
                new LinearGradient(0, 0, 1, 1, true, null,
                    new Stop(0, Color.web("#3b82f6")), new Stop(1, Color.web("#1d4ed8")));
            default ->
                new LinearGradient(0, 0, 1, 1, true, null,
                    new Stop(0, Color.web("#0ea5e9")), new Stop(1, Color.web("#0284c7")));
        };
    }

    /**
     * Returneaza culoarea pentru viteza vantului.
     */
    public static Color windToColor(double windSpeed) {
        if (windSpeed < 10) return Color.web("#22c55e");
        if (windSpeed < 30) return Color.web("#eab308");
        if (windSpeed < 50) return Color.web("#f97316");
        return Color.web("#ef4444");
    }

    /**
     * Returneaza culoarea pentru umiditate.
     */
    public static Color humidityToColor(int humidity) {
        double r = humidity / 100.0;
        return interpolate(Color.web("#fcd34d"), Color.web("#3b82f6"), r);
    }

    /**
     * Interpoleaza intre doua culori.
     */
    public static Color interpolate(Color a, Color b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return Color.color(
            a.getRed()   + (b.getRed()   - a.getRed())   * clamped,
            a.getGreen() + (b.getGreen() - a.getGreen()) * clamped,
            a.getBlue()  + (b.getBlue()  - a.getBlue())  * clamped,
            a.getOpacity() + (b.getOpacity() - a.getOpacity()) * clamped
        );
    }

    /**
     * Converteste o culoare JavaFX la string CSS hex.
     */
    public static String toCss(Color c) {
        return String.format("#%02X%02X%02X",
            (int) (c.getRed()   * 255),
            (int) (c.getGreen() * 255),
            (int) (c.getBlue()  * 255));
    }
}
