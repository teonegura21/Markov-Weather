package com.sgbd.service.prediction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste pentru serviciul de reinforcement learning.
 */
class ReinforcementServiceTest {

    @Test
    void testClampProbabilityWithinBounds() {
        assertEquals(0.01, ReinforcementService.clampProbability(-0.5), 0.001);
        assertEquals(0.01, ReinforcementService.clampProbability(0.0), 0.001);
        assertEquals(0.01, ReinforcementService.clampProbability(0.005), 0.001);
        assertEquals(0.5, ReinforcementService.clampProbability(0.5), 0.001);
        assertEquals(0.99, ReinforcementService.clampProbability(0.99), 0.001);
        assertEquals(0.99, ReinforcementService.clampProbability(1.0), 0.001);
        assertEquals(0.99, ReinforcementService.clampProbability(2.0), 0.001);
    }

    @Test
    void testClampProbabilityAtBoundaries() {
        assertEquals(0.01, ReinforcementService.clampProbability(0.01), 0.0001);
        assertEquals(0.99, ReinforcementService.clampProbability(0.99), 0.0001);
    }
}
