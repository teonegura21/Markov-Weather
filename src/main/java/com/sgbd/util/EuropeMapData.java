package com.sgbd.util;

import javafx.geometry.Point2D;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Date geografice simplificate pentru Europa.
 * Sursa: Natural Earth 1:110m (public domain).
 * Poligoanele sunt incarcate din fisier binar la runtime.
 */
public final class EuropeMapData {

    private static final Logger logger = LoggerUtil.getLogger(EuropeMapData.class);
    private static final Map<String, double[][]> COUNTRY_POLYGONS = loadPolygons();

    private EuropeMapData() { }

    public static final double MIN_LAT = 36.0;
    public static final double MAX_LAT = 71.0;
    public static final double MIN_LON = -10.0;
    public static final double MAX_LON = 40.0;

    private static Map<String, double[][]> loadPolygons() {
        Map<String, double[][]> map = new LinkedHashMap<>();
        try (InputStream is = EuropeMapData.class.getResourceAsStream(
                "/com/sgbd/europe_polygons.bin");
             DataInputStream dis = new DataInputStream(is)) {
            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                int nameLen = dis.readUnsignedShort();
                byte[] nameBytes = new byte[nameLen];
                dis.readFully(nameBytes);
                String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
                int pts = dis.readInt();
                double[][] poly = new double[pts][2];
                for (int j = 0; j < pts; j++) {
                    poly[j][0] = dis.readDouble();
                    poly[j][1] = dis.readDouble();
                }
                map.put(name, poly);
            }
            logger.info("Poligoane Europa incarcate: " + count + " tari");
        } catch (IOException e) {
            logger.severe("Eroare la incarcarea poligoanelor Europei: " + e.getMessage());
        }
        return map;
    }

    /** Returnează poligoanele țărilor europene. */
    public static Map<String, double[][]> getCountryPolygons() {
        return COUNTRY_POLYGONS;
    }

    /** Orașe importante din Europa pentru hartă. */
    public static final Object[][] IMPORTANT_CITIES = {
        {"Lisbon", "Portugal", 38.7223, -9.1393},
        {"Madrid", "Spain", 40.4168, -3.7038},
        {"Barcelona", "Spain", 41.3851, 2.1734},
        {"Paris", "France", 48.8566, 2.3522},
        {"Lyon", "France", 45.7640, 4.8357},
        {"Marseille", "France", 43.2965, 5.3698},
        {"London", "United Kingdom", 51.5074, -0.1278},
        {"Birmingham", "United Kingdom", 52.4862, -1.8904},
        {"Manchester", "United Kingdom", 53.4808, -2.2426},
        {"Dublin", "Ireland", 53.3498, -6.2603},
        {"Brussels", "Belgium", 50.8503, 4.3517},
        {"Amsterdam", "Netherlands", 52.3676, 4.9041},
        {"Luxembourg", "Luxembourg", 49.6116, 6.1319},
        {"Berlin", "Germany", 52.5200, 13.4050},
        {"Munich", "Germany", 48.1351, 11.5820},
        {"Hamburg", "Germany", 53.5511, 9.9937},
        {"Frankfurt", "Germany", 50.1109, 8.6821},
        {"Copenhagen", "Denmark", 55.6761, 12.5683},
        {"Oslo", "Norway", 59.9139, 10.7522},
        {"Stockholm", "Sweden", 59.3293, 18.0686},
        {"Helsinki", "Finland", 60.1699, 24.9384},
        {"Warsaw", "Poland", 52.2297, 21.0122},
        {"Krakow", "Poland", 50.0647, 19.9450},
        {"Prague", "Czechia", 50.0755, 14.4378},
        {"Vienna", "Austria", 48.2082, 16.3738},
        {"Budapest", "Hungary", 47.4979, 19.0402},
        {"Bucharest", "Romania", 44.4268, 26.1025},
        {"Cluj-Napoca", "Romania", 46.7712, 23.6236},
        {"Timisoara", "Romania", 45.7489, 21.2087},
        {"Iasi", "Romania", 47.1585, 27.6014},
        {"Sofia", "Bulgaria", 42.6977, 23.3219},
        {"Belgrade", "Serbia", 44.7866, 20.4489},
        {"Zagreb", "Croatia", 45.8150, 15.9819},
        {"Sarajevo", "Bosnia and Herzegovina", 43.8563, 18.4131},
        {"Podgorica", "Montenegro", 42.4304, 19.2594},
        {"Tirana", "Albania", 41.3275, 19.8187},
        {"Athens", "Greece", 37.9838, 23.7275},
        {"Thessaloniki", "Greece", 40.6401, 22.9444},
        {"Rome", "Italy", 41.9028, 12.4964},
        {"Milan", "Italy", 45.4642, 9.1900},
        {"Naples", "Italy", 40.8518, 14.2681},
        {"Turin", "Italy", 45.0703, 7.6869},
        {"Bern", "Switzerland", 46.9480, 7.4474},
        {"Zurich", "Switzerland", 47.3769, 8.5417},
        {"Geneva", "Switzerland", 46.2044, 6.1432},
        {"Ljubljana", "Slovenia", 46.0569, 14.5058},
        {"Bratislava", "Slovakia", 48.1486, 17.1077},
        {"Minsk", "Belarus", 53.9045, 27.5615},
        {"Kyiv", "Ukraine", 50.4501, 30.5234},
        {"Odessa", "Ukraine", 46.4825, 30.7233},
        {"Chisinau", "Moldova", 47.0105, 28.8638},
        {"Tallinn", "Estonia", 59.4370, 24.7536},
        {"Riga", "Latvia", 56.9496, 24.1052},
        {"Vilnius", "Lithuania", 54.6872, 25.2797},
        {"Istanbul", "Turkey", 41.0082, 28.9784},
        {"Ankara", "Turkey", 39.9334, 32.8597},
        {"Izmir", "Turkey", 38.4192, 27.1287},
        {"Moscow", "Russia", 55.7558, 37.6173},
        {"Saint Petersburg", "Russia", 59.9311, 30.3609},
        {"Reykjavik", "Iceland", 64.1466, -21.9426},
        {"Valletta", "Malta", 35.8989, 14.5146},
        {"Nicosia", "Cyprus", 35.1856, 33.3823},
        {"Skopje", "North Macedonia", 41.9981, 21.4254}
    };

    /**
     * Convertește coordonate geografice în puncte pe canvas
     * (proiecție equirectangulară).
     */
    public static Point2D geoToCanvas(double lat, double lon,
                                       double w, double h, double pad) {
        double usableW = w - 2 * pad;
        double usableH = h - 2 * pad;
        double x = pad + (lon - MIN_LON) / (MAX_LON - MIN_LON) * usableW;
        double y = pad + (MAX_LAT - lat) / (MAX_LAT - MIN_LAT) * usableH;
        return new Point2D(x, y);
    }
}
