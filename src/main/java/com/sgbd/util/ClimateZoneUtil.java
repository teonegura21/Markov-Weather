package com.sgbd.util;

/**
 * Utilitar pentru clasificarea zonelor climatice pe baza coordonatelor geografice.
 * Folosește o clasificare simplificată Köppen adaptată pentru Europa.
 */
public final class ClimateZoneUtil {

    /** Zonă climatică generică pentru întregul set de date europene. */
    public static final String EUROPE_WIDE = "europe";

    private ClimateZoneUtil() {
    }

    /**
     * Clasifică zona climatică pe baza latitudinii și longitudinii.
     *
     * @param latitudine  latitudinea în grade (-90 .. 90)
     * @param longitudine longitudinea în grade (-180 .. 180)
     * @return identificatorul zonei climatice
     */
    public static String classify(double latitudine, double longitudine) {
        // Valori absolute pentru latitudine (funcționează și pentru emisfera sudică)
        double lat = Math.abs(latitudine);

        if (lat < 42.0) {
            return "mediterranean";
        }
        if (lat > 55.0) {
            return "nordic";
        }
        // Longitudinea relativă la Greenwich; pragul ~8°E aproximează
        // granița dintre influența oceanică și cea continentală în Europa
        if (longitudine < 8.0) {
            return "oceanic";
        }
        return "continental";
    }
}
