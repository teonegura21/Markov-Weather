package com.sgbd.service.prediction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste pentru serviciul de calcul al acuratetii predictiilor.
 */
class AccuracyServiceTest {

    @Test
    void testComputeMetricsKnownValues() {
        AccuracyMetrics m = AccuracyService.computeMetrics(20, 22);
        assertEquals(2.0, m.getMaeTempMax(), 0.001);
        assertEquals(2.0, m.getRmseTempMax(), 0.001);
        assertEquals(-2.0, m.getBiasTempMax(), 0.001);
    }

    @Test
    void testComputeMetricsPerfectPrediction() {
        AccuracyMetrics m = AccuracyService.computeMetrics(25, 25);
        assertEquals(0.0, m.getMaeTempMax(), 0.001);
        assertEquals(0.0, m.getRmseTempMax(), 0.001);
        assertEquals(0.0, m.getBiasTempMax(), 0.001);
    }

    @Test
    void testComputeMetricsNegativeBias() {
        AccuracyMetrics m = AccuracyService.computeMetrics(30, 25);
        assertEquals(5.0, m.getMaeTempMax(), 0.001);
        assertEquals(5.0, m.getRmseTempMax(), 0.001);
        assertEquals(5.0, m.getBiasTempMax(), 0.001);
    }
}
