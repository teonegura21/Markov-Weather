package com.sgbd.util;

import javafx.geometry.Point2D;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Coordonate detaliate pentru conturul Romaniei.
 * Sursa: OpenStreetMap (Nominatim) - ~18600 puncte.
 * Datele sunt incarcate dintr-un fisier binar la runtime.
 * Originea (0,0) = coltul stanga-sus al canvas-ului.
 */
public final class RomaniaMapData {

    private static final Logger logger = LoggerUtil.getLogger(RomaniaMapData.class);
    private static final double[][] BORDER_POLYGON;

    private RomaniaMapData() {}

    static {
        BORDER_POLYGON = loadBorder();
    }

    /** Limitele geografice ale Romaniei. */
    public static final double MIN_LAT = 43.6188114;
    public static final double MAX_LAT = 48.2654738;
    public static final double MIN_LON = 20.2619955;
    public static final double MAX_LON = 30.0454257;

    private static double[][] loadBorder() {
        try (InputStream is = RomaniaMapData.class.getResourceAsStream("/com/sgbd/romania_border.bin");
             DataInputStream dis = new DataInputStream(is)) {
            int count = dis.readInt();
            double[][] poly = new double[count][2];
            for (int i = 0; i < count; i++) {
                poly[i][0] = dis.readDouble(); // lat
                poly[i][1] = dis.readDouble(); // lon
            }
            logger.info("Contur Romania incarcat: " + count + " puncte");
            return poly;
        } catch (IOException e) {
            logger.severe("Eroare la incarcarea conturului Romaniei: " + e.getMessage());
            return new double[0][0];
        }
    }

    /**
     * Returneaza conturul detaliat al Romaniei ca serie de puncte lat/lon.
     * Sursa: OpenStreetMap (Nominatim) - ~18600 puncte.
     */
    public static double[][] getBorderPolygon() {
        return BORDER_POLYGON;
    }

    /**
     * Converteste coordonate geografice (lat, lon) in puncte pe canvas.
     *
     * @param lat  latitudinea
     * @param lon  longitudinea
     * @param w    latimea canvas-ului
     * @param h    inaltimea canvas-ului
     * @param pad  padding in jurul hartii
     * @return punctul 2D pe canvas
     */
    public static Point2D geoToCanvas(double lat, double lon, double w, double h, double pad) {
        double usableW = w - 2 * pad;
        double usableH = h - 2 * pad;
        double x = pad + (lon - MIN_LON) / (MAX_LON - MIN_LON) * usableW;
        double y = pad + (MAX_LAT - lat) / (MAX_LAT - MIN_LAT) * usableH;
        return new Point2D(x, y);
    }
}
