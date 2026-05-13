package com.sgbd.service.prediction;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste pentru POJO-ul AccuracyMetrics.
 */
class AccuracyMetricsTest {

    @Test
    void gettersAndSetters_workCorrectly() {
        AccuracyMetrics m = new AccuracyMetrics();
        LocalDate d = LocalDate.of(2024, 6, 15);

        m.setDate(d);
        m.setHorizonDay(3);
        m.setMaeTempMax(2.5);
        m.setMaeTempMin(1.8);
        m.setRmseTempMax(3.2);
        m.setBiasTempMax(-0.5);
        m.setWindError(4.1);
        m.setHumidityError(8.0);
        m.setPredictedTempMaxP50(25.0);
        m.setPredictedTempMinP50(14.0);
        m.setPredictedWindSpeedP50(10.0);
        m.setPredictedHumidityP50(60.0);
        m.setActualTempMax(27.5);
        m.setActualTempMin(15.8);
        m.setActualWindSpeed(14.1);
        m.setActualHumidity(52.0);
        m.setStormHit(true);
        m.setFogHit(false);
        m.setHeatwaveHit(false);
        m.setPrecipHit(true);

        assertEquals(d, m.getDate());
        assertEquals(3, m.getHorizonDay());
        assertEquals(2.5, m.getMaeTempMax(), 1e-9);
        assertEquals(1.8, m.getMaeTempMin(), 1e-9);
        assertEquals(3.2, m.getRmseTempMax(), 1e-9);
        assertEquals(-0.5, m.getBiasTempMax(), 1e-9);
        assertEquals(4.1, m.getWindError(), 1e-9);
        assertEquals(8.0, m.getHumidityError(), 1e-9);
        assertEquals(25.0, m.getPredictedTempMaxP50(), 1e-9);
        assertEquals(14.0, m.getPredictedTempMinP50(), 1e-9);
        assertEquals(10.0, m.getPredictedWindSpeedP50(), 1e-9);
        assertEquals(60.0, m.getPredictedHumidityP50(), 1e-9);
        assertEquals(27.5, m.getActualTempMax(), 1e-9);
        assertEquals(15.8, m.getActualTempMin(), 1e-9);
        assertEquals(14.1, m.getActualWindSpeed(), 1e-9);
        assertEquals(52.0, m.getActualHumidity(), 1e-9);
        assertTrue(m.isStormHit());
        assertFalse(m.isFogHit());
        assertFalse(m.isHeatwaveHit());
        assertTrue(m.isPrecipHit());
    }

    @Test
    void defaultConstructor_createsEmptyObject() {
        AccuracyMetrics m = new AccuracyMetrics();
        assertNull(m.getDate());
        assertEquals(0, m.getHorizonDay());
        assertEquals(0.0, m.getMaeTempMax(), 1e-9);
    }
}
