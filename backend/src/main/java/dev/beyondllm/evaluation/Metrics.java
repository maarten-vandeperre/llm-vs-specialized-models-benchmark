package dev.beyondllm.evaluation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Pure metric functions (no framework dependencies) so they are trivially unit-testable. */
public final class Metrics {

    public static final long BOOTSTRAP_SEED = 1234L;
    public static final int BOOTSTRAP_ITERATIONS = 1000;

    private Metrics() {
    }

    // ---- proportions --------------------------------------------------------------------------------------

    /** Wilson score interval (95%). */
    public static double[] wilson(long successes, long n) {
        if (n == 0) {
            return null;
        }
        double z = 1.959963984540054;
        double p = (double) successes / n;
        double denom = 1 + z * z / n;
        double centre = (p + z * z / (2.0 * n)) / denom;
        double half = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / denom;
        return new double[]{round(Math.max(0, centre - half)), round(Math.min(1, centre + half))};
    }

    public static Double rate(long k, long n) {
        return n == 0 ? null : round((double) k / n);
    }

    // ---- classification ------------------------------------------------------------------------------------

    public record LabelStats(double precision, double recall, double f1, long support) {
    }

    public record ClassificationResult(long n, double accuracy, double[] accuracyCi, double macroF1, double[] macroF1Ci,
                                       double macroPrecision, double macroRecall, Map<String, LabelStats> perLabel) {
    }

    /**
     * Accuracy and macro-averaged P/R/F1 over the label vocabulary. Labels with zero support and zero predictions are
     * excluded from the macro average; predictions outside the vocabulary (including failures) count as errors.
     */
    public static ClassificationResult classification(List<String> gold, List<String> pred, List<String> vocabulary) {
        long n = gold.size();
        long correct = 0;
        for (int i = 0; i < n; i++) {
            if (gold.get(i).equals(pred.get(i))) {
                correct++;
            }
        }
        Map<String, LabelStats> per = perLabel(gold, pred, vocabulary);
        double[] macro = macro(per);
        double[] f1Ci = bootstrapMacroF1(gold, pred, vocabulary);
        return new ClassificationResult(n, n == 0 ? 0 : round((double) correct / n), wilson(correct, n), round(macro[2]), f1Ci,
                round(macro[0]), round(macro[1]), per);
    }

    static Map<String, LabelStats> perLabel(List<String> gold, List<String> pred, List<String> vocabulary) {
        Map<String, LabelStats> out = new LinkedHashMap<>();
        for (String label : vocabulary) {
            long tp = 0, fp = 0, fn = 0;
            for (int i = 0; i < gold.size(); i++) {
                boolean g = label.equals(gold.get(i));
                boolean p = label.equals(pred.get(i));
                if (g && p) {
                    tp++;
                } else if (p) {
                    fp++;
                } else if (g) {
                    fn++;
                }
            }
            if (tp + fp + fn == 0) {
                continue;
            }
            double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
            double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
            double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
            out.put(label, new LabelStats(round(precision), round(recall), round(f1), tp + fn));
        }
        return out;
    }

    static double[] macro(Map<String, LabelStats> per) {
        if (per.isEmpty()) {
            return new double[]{0, 0, 0};
        }
        double p = 0, r = 0, f = 0;
        for (LabelStats s : per.values()) {
            p += s.precision();
            r += s.recall();
            f += s.f1();
        }
        return new double[]{p / per.size(), r / per.size(), f / per.size()};
    }

    static double[] bootstrapMacroF1(List<String> gold, List<String> pred, List<String> vocab) {
        int n = gold.size();
        if (n < 2) {
            return null;
        }
        Random r = new Random(BOOTSTRAP_SEED);
        double[] stats = new double[BOOTSTRAP_ITERATIONS];
        List<String> g = new ArrayList<>(n);
        List<String> p = new ArrayList<>(n);
        for (int b = 0; b < BOOTSTRAP_ITERATIONS; b++) {
            g.clear();
            p.clear();
            for (int i = 0; i < n; i++) {
                int k = r.nextInt(n);
                g.add(gold.get(k));
                p.add(pred.get(k));
            }
            stats[b] = macro(perLabel(g, p, vocab))[2];
        }
        Arrays.sort(stats);
        return new double[]{round(stats[(int) (0.025 * BOOTSTRAP_ITERATIONS)]), round(stats[(int) (0.975 * BOOTSTRAP_ITERATIONS) - 1])};
    }

    // ---- calibration --------------------------------------------------------------------------------------

    public record ScoredOutcome(double confidence, boolean correct) {
    }

    public record ReliabilityBin(double binLower, double binUpper, long count, Double meanConfidence, Double accuracy) {
    }

    public record CoveragePoint(double coverage, double accuracy, double threshold) {
    }

    public record Risk(double threshold, double coverage, Double risk) {
    }

    public record CalibrationResult(long n, double brier, double ece, List<ReliabilityBin> reliability,
                                    List<CoveragePoint> coverageCurve) {
    }

    /** Top-label calibration: Brier score of (confidence, correct) pairs, 10-bin ECE, reliability bins, accuracy–coverage curve. */
    public static CalibrationResult calibration(List<ScoredOutcome> s) {
        if (s.isEmpty()) {
            return null;
        }
        double brier = 0;
        for (ScoredOutcome o : s) {
            double y = o.correct() ? 1 : 0;
            brier += (o.confidence() - y) * (o.confidence() - y);
        }
        brier /= s.size();
        int bins = 10;
        long[] count = new long[bins];
        double[] conf = new double[bins];
        double[] acc = new double[bins];
        for (ScoredOutcome o : s) {
            int b = Math.min(bins - 1, (int) Math.floor(o.confidence() * bins));
            count[b]++;
            conf[b] += o.confidence();
            acc[b] += o.correct() ? 1 : 0;
        }
        double ece = 0;
        List<ReliabilityBin> rel = new ArrayList<>();
        for (int b = 0; b < bins; b++) {
            Double mc = count[b] == 0 ? null : round(conf[b] / count[b]);
            Double ma = count[b] == 0 ? null : round(acc[b] / count[b]);
            if (count[b] > 0) {
                ece += (double) count[b] / s.size() * Math.abs(conf[b] / count[b] - acc[b] / count[b]);
            }
            rel.add(new ReliabilityBin(round((double) b / bins), round((double) (b + 1) / bins), count[b], mc, ma));
        }
        return new CalibrationResult(s.size(), round(brier), round(ece), rel, coverageCurve(s));
    }

    /** Selective accuracy: sort by confidence desc and report accuracy of the top-k for k = 10%..100%. */
    public static List<CoveragePoint> coverageCurve(List<ScoredOutcome> s) {
        List<ScoredOutcome> sorted = new ArrayList<>(s);
        sorted.sort(Comparator.comparingDouble(ScoredOutcome::confidence).reversed());
        List<CoveragePoint> out = new ArrayList<>();
        for (int step = 1; step <= 10; step++) {
            int k = Math.max(1, (int) Math.round(sorted.size() * step / 10.0));
            long correct = sorted.subList(0, k).stream().filter(ScoredOutcome::correct).count();
            out.add(new CoveragePoint(round((double) k / sorted.size()), round((double) correct / k), round(sorted.get(k - 1).confidence())));
        }
        return out;
    }

    /** Risk (error rate) among predictions automated at confidence ≥ threshold, and the resulting coverage. */
    public static Risk riskAt(List<ScoredOutcome> s, double threshold) {
        if (s.isEmpty()) {
            return null;
        }
        long accepted = 0, errors = 0;
        for (ScoredOutcome o : s) {
            if (o.confidence() >= threshold) {
                accepted++;
                if (!o.correct()) {
                    errors++;
                }
            }
        }
        return new Risk(threshold, round((double) accepted / s.size()), accepted == 0 ? null : round((double) errors / accepted));
    }

    /**
     * Smallest threshold whose accepted set reaches the target selective accuracy (used on the calibration split
     * only). Returns 1.0 (route everything) when the target is unreachable.
     */
    public static double thresholdForTargetAccuracy(List<ScoredOutcome> s, double target, int minAccepted) {
        Set<Double> candidates = new LinkedHashSet<>();
        s.stream().map(ScoredOutcome::confidence).sorted().forEach(candidates::add);
        for (double t : candidates) {
            long acc = 0, ok = 0;
            for (ScoredOutcome o : s) {
                if (o.confidence() >= t) {
                    acc++;
                    ok += o.correct() ? 1 : 0;
                }
            }
            if (acc >= minAccepted && (double) ok / acc >= target) {
                return round(t);
            }
        }
        return 1.0;
    }

    // ---- latency ------------------------------------------------------------------------------------------

    public record LatencyStats(long n, double p50, double p90, double p95, Double p99, double mean, double min, double max, double[] p50Ci) {
    }

    /** Percentiles with linear interpolation; p99 only reported when n ≥ 100. */
    public static LatencyStats latency(List<Double> millis) {
        if (millis.isEmpty()) {
            return null;
        }
        double[] v = millis.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double mean = Arrays.stream(v).average().orElse(0);
        return new LatencyStats(v.length, round2(pct(v, 50)), round2(pct(v, 90)), round2(pct(v, 95)),
                v.length >= 100 ? round2(pct(v, 99)) : null, round2(mean), round2(v[0]), round2(v[v.length - 1]), bootstrapMedian(v));
    }

    public static double pct(double[] sorted, double p) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = p / 100.0 * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo);
    }

    static double[] bootstrapMedian(double[] v) {
        if (v.length < 5) {
            return null;
        }
        Random r = new Random(BOOTSTRAP_SEED);
        double[] stats = new double[BOOTSTRAP_ITERATIONS];
        double[] sample = new double[v.length];
        for (int b = 0; b < BOOTSTRAP_ITERATIONS; b++) {
            for (int i = 0; i < v.length; i++) {
                sample[i] = v[r.nextInt(v.length)];
            }
            Arrays.sort(sample);
            stats[b] = pct(sample, 50);
        }
        Arrays.sort(stats);
        return new double[]{round2(stats[(int) (0.025 * BOOTSTRAP_ITERATIONS)]), round2(stats[(int) (0.975 * BOOTSTRAP_ITERATIONS) - 1])};
    }

    public static double round(double d) {
        return Math.round(d * 10000.0) / 10000.0;
    }

    public static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
