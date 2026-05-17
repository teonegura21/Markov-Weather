package com.sgbd.service.prediction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste pentru logica numerică a HMM (Baum-Welch).
 * Nu necesită bază de date — testează doar algoritmii puri.
 */
class HmmTrainingServiceTest {

    private final HmmTrainingService service = new HmmTrainingService();

    @Test
    void testForwardAlgorithmProducesValidProbabilities() {
        int[] O = {0, 1, 0};
        double[][] A = {
            {0.7, 0.3},
            {0.4, 0.6}
        };
        double[][] B = {
            {0.5, 0.5},
            {0.1, 0.9}
        };
        double[] pi = {0.6, 0.4};

        double[][] alpha = service.forwardAlgorithm(O, A, B, pi);

        assertEquals(3, alpha.length); // T = 3
        assertEquals(2, alpha[0].length); // N = 2

        // Probabilitățile forward trebuie să fie pozitive
        for (int t = 0; t < alpha.length; t++) {
            for (int i = 0; i < alpha[t].length; i++) {
                assertTrue(alpha[t][i] >= 0, "alpha[" + t + "][" + i + "] trebuie >= 0");
            }
        }

        // Likelihood-ul total trebuie să fie pozitiv
        double likelihood = alpha[alpha.length - 1][0] + alpha[alpha.length - 1][1];
        assertTrue(likelihood > 0, "Likelihood-ul total trebuie să fie pozitiv");
    }

    @Test
    void testBackwardAlgorithmProducesValidProbabilities() {
        int[] O = {0, 1, 0};
        double[][] A = {
            {0.7, 0.3},
            {0.4, 0.6}
        };
        double[][] B = {
            {0.5, 0.5},
            {0.1, 0.9}
        };

        double[][] beta = service.backwardAlgorithm(O, A, B);

        assertEquals(3, beta.length);
        assertEquals(2, beta[0].length);

        // beta[T-1][i] trebuie să fie 1.0 pentru toate stările
        for (int i = 0; i < beta[beta.length - 1].length; i++) {
            assertEquals(1.0, beta[beta.length - 1][i], 1e-9,
                "beta[T-1][" + i + "] trebuie să fie 1.0");
        }

        // Toate valorile trebuie să fie pozitive
        for (int t = 0; t < beta.length; t++) {
            for (int i = 0; i < beta[t].length; i++) {
                assertTrue(beta[t][i] >= 0, "beta[" + t + "][" + i + "] trebuie >= 0");
            }
        }
    }

    @Test
    void testBaumWelchStepProducesValidMatrices() {
        int[] O = {0, 1, 0};
        double[][] A = {
            {0.7, 0.3},
            {0.4, 0.6}
        };
        double[][] B = {
            {0.5, 0.5},
            {0.1, 0.9}
        };
        double[] pi = {0.6, 0.4};

        double[][] alpha = service.forwardAlgorithm(O, A, B, pi);
        double[][] beta = service.backwardAlgorithm(O, A, B);

        Object[] result = service.baumWelchStep(O, A, B, pi, alpha, beta);

        double[][] ANew = (double[][]) result[0];
        double[][] BNew = (double[][]) result[1];
        double[] piNew = (double[]) result[2];

        // Verificăm dimensiunile
        assertEquals(2, ANew.length);
        assertEquals(2, ANew[0].length);
        assertEquals(2, BNew.length);
        assertEquals(2, BNew[0].length);
        assertEquals(2, piNew.length);

        // Verificăm că rândurile lui A sumează la 1.0
        for (int i = 0; i < ANew.length; i++) {
            double sum = 0;
            for (int j = 0; j < ANew[i].length; j++) {
                sum += ANew[i][j];
                assertTrue(ANew[i][j] >= 0, "A[" + i + "][" + j + "] trebuie >= 0");
            }
            assertEquals(1.0, sum, 0.01, "Suma rândului " + i + " din A trebuie să fie 1.0");
        }

        // Verificăm că rândurile lui B sumează la 1.0
        for (int i = 0; i < BNew.length; i++) {
            double sum = 0;
            for (int j = 0; j < BNew[i].length; j++) {
                sum += BNew[i][j];
                assertTrue(BNew[i][j] >= 0, "B[" + i + "][" + j + "] trebuie >= 0");
            }
            assertEquals(1.0, sum, 0.01, "Suma rândului " + i + " din B trebuie să fie 1.0");
        }

        // Verificăm că pi sumează la 1.0
        double piSum = 0;
        for (double v : piNew) {
            piSum += v;
            assertTrue(v >= 0, "pi trebuie >= 0");
        }
        assertEquals(1.0, piSum, 0.01, "Suma lui pi trebuie să fie 1.0");
    }

    @Test
    void testBaumWelchConverges() {
        // Secvență de observații simple
        int[] O = {0, 1, 0, 1, 0, 1, 0, 1};
        int N = 2;
        int M = 2;

        double[][] A = {
            {0.5, 0.5},
            {0.5, 0.5}
        };
        double[][] B = {
            {0.5, 0.5},
            {0.5, 0.5}
        };
        double[] pi = {0.5, 0.5};

        // Rulăm câțiva pași de Baum-Welch
        double prevLogLikelihood = Double.NEGATIVE_INFINITY;
        for (int iter = 0; iter < 10; iter++) {
            double[][] alpha = service.forwardAlgorithm(O, A, B, pi);
            double[][] beta = service.backwardAlgorithm(O, A, B);

            double likelihood = 0;
            for (double v : alpha[alpha.length - 1]) {
                likelihood += v;
            }
            double logLikelihood = Math.log(likelihood + 1e-100);

            // Log-likelihood-ul trebuie să crească sau să rămână stabil
            assertTrue(logLikelihood >= prevLogLikelihood - 0.01,
                "Log-likelihood trebuie să crească: " + prevLogLikelihood + " -> " + logLikelihood);
            prevLogLikelihood = logLikelihood;

            Object[] result = service.baumWelchStep(O, A, B, pi, alpha, beta);
            A = (double[][]) result[0];
            B = (double[][]) result[1];
            pi = (double[]) result[2];
        }
    }

    @Test
    void testForwardBackwardConsistency() {
        int[] O = {0, 1, 0, 1};
        double[][] A = {
            {0.6, 0.4},
            {0.3, 0.7}
        };
        double[][] B = {
            {0.8, 0.2},
            {0.2, 0.8}
        };
        double[] pi = {0.5, 0.5};

        double[][] alpha = service.forwardAlgorithm(O, A, B, pi);
        double[][] beta = service.backwardAlgorithm(O, A, B);

        // P(O) calculat prin forward trebuie să fie egal cu P(O) calculat prin backward
        double forwardLikelihood = alpha[alpha.length - 1][0] + alpha[alpha.length - 1][1];

        double backwardLikelihood = 0;
        for (int i = 0; i < pi.length; i++) {
            backwardLikelihood += pi[i] * B[i][O[0]] * beta[0][i];
        }

        assertEquals(forwardLikelihood, backwardLikelihood, 1e-9,
            "Forward și backward trebuie să dea același likelihood");
    }
}
