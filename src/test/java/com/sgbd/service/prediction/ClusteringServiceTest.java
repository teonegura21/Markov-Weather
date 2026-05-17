package com.sgbd.service.prediction;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste pentru logica numerică a ClusteringService (k-means).
 * Nu necesită bază de date — testează doar algoritmii puri.
 */
class ClusteringServiceTest {

    private final ClusteringService service = new ClusteringService();

    @Test
    void testComputeStatsCalculatesMeanCorrectly() {
        List<ClusteringService.WeatherDataPoint> points = new ArrayList<>();
        points.add(createPoint(new double[]{1.0, 2.0}));
        points.add(createPoint(new double[]{3.0, 4.0}));
        points.add(createPoint(new double[]{5.0, 6.0}));

        double[] means = new double[39];
        double[] stds = new double[39];
        service.computeStats(points, means, stds);

        assertEquals(3.0, means[0], 1e-9, "Media dimensiunii 0 trebuie să fie 3.0");
        assertEquals(4.0, means[1], 1e-9, "Media dimensiunii 1 trebuie să fie 4.0");
    }

    @Test
    void testComputeStatsCalculatesStdCorrectly() {
        List<ClusteringService.WeatherDataPoint> points = new ArrayList<>();
        points.add(createPoint(new double[]{0.0, 0.0}));
        points.add(createPoint(new double[]{2.0, 2.0}));
        points.add(createPoint(new double[]{4.0, 4.0}));

        double[] means = new double[39];
        double[] stds = new double[39];
        service.computeStats(points, means, stds);

        // Std dev pentru [0, 2, 4] = sqrt(((0-2)^2 + (2-2)^2 + (4-2)^2) / 3) = sqrt(8/3) ≈ 1.633
        double expectedStd = Math.sqrt(8.0 / 3.0);
        assertEquals(expectedStd, stds[0], 1e-9, "Std dev dimensiunii 0 incorectă");
        assertEquals(expectedStd, stds[1], 1e-9, "Std dev dimensiunii 1 incorectă");
    }

    @Test
    void testStandardizeProducesZeroMean() {
        List<ClusteringService.WeatherDataPoint> points = new ArrayList<>();
        points.add(createPoint(new double[]{10.0, 100.0}));
        points.add(createPoint(new double[]{20.0, 200.0}));
        points.add(createPoint(new double[]{30.0, 300.0}));

        double[] means = new double[39];
        double[] stds = new double[39];
        service.computeStats(points, means, stds);
        service.standardize(points, means, stds);

        // După standardizare, media trebuie să fie ~0
        double sum0 = 0, sum1 = 0;
        for (ClusteringService.WeatherDataPoint p : points) {
            sum0 += p.getPoint()[0];
            sum1 += p.getPoint()[1];
        }
        assertEquals(0.0, sum0 / points.size(), 1e-9, "Media standardizată trebuie să fie 0");
        assertEquals(0.0, sum1 / points.size(), 1e-9, "Media standardizată trebuie să fie 0");
    }

    @Test
    void testStandardizeProducesUnitVariance() {
        List<ClusteringService.WeatherDataPoint> points = new ArrayList<>();
        points.add(createPoint(new double[]{1.0, 10.0}));
        points.add(createPoint(new double[]{2.0, 20.0}));
        points.add(createPoint(new double[]{3.0, 30.0}));
        points.add(createPoint(new double[]{4.0, 40.0}));
        points.add(createPoint(new double[]{5.0, 50.0}));

        double[] means = new double[39];
        double[] stds = new double[39];
        service.computeStats(points, means, stds);
        service.standardize(points, means, stds);

        // Calculează varianța după standardizare (trebuie să fie ~1.0)
        double mean0 = 0, mean1 = 0;
        for (ClusteringService.WeatherDataPoint p : points) {
            mean0 += p.getPoint()[0];
            mean1 += p.getPoint()[1];
        }
        mean0 /= points.size();
        mean1 /= points.size();

        double var0 = 0, var1 = 0;
        for (ClusteringService.WeatherDataPoint p : points) {
            var0 += (p.getPoint()[0] - mean0) * (p.getPoint()[0] - mean0);
            var1 += (p.getPoint()[1] - mean1) * (p.getPoint()[1] - mean1);
        }
        var0 /= points.size();
        var1 /= points.size();

        assertEquals(1.0, var0, 0.01, "Varianța standardizată trebuie să fie ~1.0");
        assertEquals(1.0, var1, 0.01, "Varianța standardizată trebuie să fie ~1.0");
    }

    @Test
    void testInferLabelDetectsHeatwave() {
        // Centroid standardizat care sugerează caniculă
        Double[] centroid = new Double[39];
        for (int i = 0; i < centroid.length; i++) centroid[i] = 0.0;
        centroid[1] = 2.5;  // temp_max foarte mare
        centroid[37] = 0.8; // heatwave_score ridicat

        String label = service.inferLabel(centroid);
        assertEquals("Caniculă", label, "Trebuie detectată canicula");
    }

    @Test
    void testInferLabelDetectsFog() {
        Double[] centroid = new Double[39];
        for (int i = 0; i < centroid.length; i++) centroid[i] = 0.0;
        centroid[33] = 0.8; // fog_score ridicat
        centroid[8] = 1.2;  // humidity ridicată

        String label = service.inferLabel(centroid);
        assertEquals("Ceață densă", label, "Trebuie detectată ceața");
    }

    @Test
    void testInferLabelDetectsNormal() {
        Double[] centroid = new Double[39];
        for (int i = 0; i < centroid.length; i++) centroid[i] = 0.0;

        String label = service.inferLabel(centroid);
        assertEquals("Normal", label, "Centroid neutru trebuie etichetat Normal");
    }

    @Test
    void testFindNearestClusterReturnsClosest() {
        // Creăm 2 clustere simple
        List<CentroidCluster<ClusteringService.WeatherDataPoint>> clusters = new ArrayList<>();

        // Cluster 0: centroid la (0, 0) în dimensiunile 0 și 1
        CentroidCluster<ClusteringService.WeatherDataPoint> cluster0 = createFakeCluster(new double[]{0.0, 0.0});
        clusters.add(cluster0);

        // Cluster 1: centroid la (10, 10) în dimensiunile 0 și 1
        CentroidCluster<ClusteringService.WeatherDataPoint> cluster1 = createFakeCluster(new double[]{10.0, 10.0});
        clusters.add(cluster1);

        // Punct la (1, 1) — trebuie să fie mai aproape de cluster 0
        ClusteringService.WeatherDataPoint p1 = createPoint(new double[]{1.0, 1.0});
        assertEquals(0, service.findNearestCluster(p1, clusters), "Punct (1,1) trebuie în cluster 0");

        // Punct la (9, 9) — trebuie să fie mai aproape de cluster 1
        ClusteringService.WeatherDataPoint p2 = createPoint(new double[]{9.0, 9.0});
        assertEquals(1, service.findNearestCluster(p2, clusters), "Punct (9,9) trebuie în cluster 1");
    }

    @Test
    void testFindNearestClusterWithExactMatch() {
        List<CentroidCluster<ClusteringService.WeatherDataPoint>> clusters = new ArrayList<>();
        clusters.add(createFakeCluster(new double[]{5.0, 5.0}));
        clusters.add(createFakeCluster(new double[]{-5.0, -5.0}));

        ClusteringService.WeatherDataPoint p = createPoint(new double[]{5.0, 5.0});
        assertEquals(0, service.findNearestCluster(p, clusters), "Punct exact pe centroid");
    }

    // Helper methods

    private ClusteringService.WeatherDataPoint createPoint(double[] smallVec) {
        // Extinde vectorul la 39 dimensiuni (DIMENSIONS din ClusteringService)
        double[] vec = new double[39];
        for (int i = 0; i < smallVec.length && i < vec.length; i++) {
            vec[i] = smallVec[i];
        }
        try {
            java.lang.reflect.Constructor<?> ctor =
                ClusteringService.class.getDeclaredClasses()[0].getDeclaredConstructor(int.class, LocalDate.class, double[].class);
            ctor.setAccessible(true);
            return (ClusteringService.WeatherDataPoint) ctor.newInstance(1, LocalDate.now(), vec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private CentroidCluster<ClusteringService.WeatherDataPoint> createFakeCluster(double[] smallCenter) {
        double[] center = new double[39];
        for (int i = 0; i < smallCenter.length && i < center.length; i++) {
            center[i] = smallCenter[i];
        }
        return new FakeCluster(center);
    }

    @SuppressWarnings("unchecked")
    private static class FakeCluster extends CentroidCluster<ClusteringService.WeatherDataPoint> {
        private final double[] center;

        FakeCluster(double[] center) {
            super(null); // constructorul are un singur parametru Clusterable
            this.center = center;
        }

        @Override
        public org.apache.commons.math3.ml.clustering.Clusterable getCenter() {
            return new org.apache.commons.math3.ml.clustering.Clusterable() {
                @Override
                public double[] getPoint() {
                    return center;
                }
            };
        }

        @Override
        public java.util.List<ClusteringService.WeatherDataPoint> getPoints() {
            return new ArrayList<>();
        }
    }
}
