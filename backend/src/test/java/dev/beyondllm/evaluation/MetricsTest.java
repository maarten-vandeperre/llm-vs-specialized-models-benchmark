package dev.beyondllm.evaluation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsTest {

    @Test
    void accuracyAndMacroF1MatchHandComputedValues() {
        // gold: a a b b c ; pred: a b b b __timeout
        List<String> gold = List.of("a", "a", "b", "b", "c");
        List<String> pred = List.of("a", "b", "b", "b", "__timeout");
        Metrics.ClassificationResult r = Metrics.classification(gold, pred, List.of("a", "b", "c"));
        assertEquals(0.6, r.accuracy(), 1e-9);
        // a: P=1 R=.5 F1=.6667 ; b: P=.6667 R=1 F1=.8 ; c: P=0 R=0 F1=0 → macro F1 = .4889
        assertEquals(0.4889, r.macroF1(), 1e-4);
        assertEquals(0.5556, r.macroPrecision(), 1e-4);
        assertEquals(0.5, r.macroRecall(), 1e-4);
        assertEquals(1, r.perLabel().get("c").support());
        assertNotNull(r.macroF1Ci());
        assertTrue(r.accuracyCi()[0] < 0.6 && r.accuracyCi()[1] > 0.6);
    }

    @Test
    void wilsonInterval() {
        double[] ci = Metrics.wilson(8, 10);
        assertEquals(0.4902, ci[0], 1e-3);
        assertEquals(0.9433, ci[1], 1e-3);
        assertNull(Metrics.wilson(0, 0));
    }

    @Test
    void brierAndEce() {
        List<Metrics.ScoredOutcome> s = List.of(
                new Metrics.ScoredOutcome(0.9, true), new Metrics.ScoredOutcome(0.9, false),
                new Metrics.ScoredOutcome(0.2, false), new Metrics.ScoredOutcome(0.2, false));
        Metrics.CalibrationResult c = Metrics.calibration(s);
        // brier = (0.01 + 0.81 + 0.04 + 0.04)/4 = 0.225
        assertEquals(0.225, c.brier(), 1e-9);
        // bins: [0.9]: conf .9 acc .5 → .4*.5 ; [0.2]: conf .2 acc 0 → .2*.5 ; ece = .3
        assertEquals(0.3, c.ece(), 1e-9);
        assertEquals(10, c.reliability().size());
        assertEquals(1.0, c.coverageCurve().getLast().coverage(), 1e-9);
    }

    @Test
    void riskAtThresholdAndThresholdSearch() {
        List<Metrics.ScoredOutcome> s = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            s.add(new Metrics.ScoredOutcome(0.95, true));
        }
        for (int i = 0; i < 20; i++) {
            s.add(new Metrics.ScoredOutcome(0.5, i % 2 == 0));
        }
        Metrics.Risk r = Metrics.riskAt(s, 0.9);
        assertEquals(0.5, r.coverage(), 1e-9);
        assertEquals(0.0, r.risk(), 1e-9);
        assertEquals(0.95, Metrics.thresholdForTargetAccuracy(s, 0.95, 10), 1e-9);
        assertEquals(1.0, Metrics.thresholdForTargetAccuracy(List.of(new Metrics.ScoredOutcome(0.9, false)), 0.95, 1), 1e-9);
    }

    @Test
    void latencyPercentilesAndP99Rule() {
        List<Double> v = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            v.add((double) i);
        }
        Metrics.LatencyStats s = Metrics.latency(v);
        assertEquals(5.5, s.p50(), 1e-9);
        assertEquals(9.1, s.p90(), 1e-9);
        assertNull(s.p99(), "p99 only with n ≥ 100");
        List<Double> big = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            big.add((double) i);
        }
        assertEquals(99.01, Metrics.latency(big).p99(), 1e-9);
        assertNull(Metrics.latency(List.of()));
    }
}
