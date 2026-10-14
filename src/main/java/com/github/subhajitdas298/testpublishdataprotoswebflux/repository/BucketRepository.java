package com.github.subhajitdas298.testpublishdataprotoswebflux.repository;

import org.springframework.stereotype.Repository;

import java.util.SplittableRandom;

/**
 * Synthetic score distribution of a fraud model: most transactions get a low score (a long
 * decaying tail), and a small fraud population clusters around a high score. Seeded, so every
 * start serves the same counts.
 */
@Repository
public class BucketRepository {

    private static final int MAX_SCORE = 1000;
    private static final int FINE_BUCKETS = 100;
    private static final int COARSE_BUCKETS = 10;
    private static final long TRANSACTIONS = 1_000_000L;
    private static final double FRAUD_SHARE = 0.03;
    private static final long SEED = 0L;

    private final Buckets buckets = build();

    public Buckets findBuckets() {
        return buckets;
    }

    private static Buckets build() {
        SplittableRandom random = new SplittableRandom(SEED);
        double width = (double) MAX_SCORE / FINE_BUCKETS;

        double[] weight = new double[FINE_BUCKETS];
        double total = 0;
        for (int j = 0; j < FINE_BUCKETS; j++) {
            double score = (j + 0.5) * width;
            double legit = Math.exp(-score / 110.0);
            double fraud = Math.exp(-0.5 * Math.pow((score - 820.0) / 80.0, 2));
            double jitter = 1.0 + (random.nextDouble() - 0.5) * 0.08;
            weight[j] = ((1 - FRAUD_SHARE) * legit / 110.0 + FRAUD_SHARE * fraud / 80.0) * jitter;
            total += weight[j];
        }

        long[] b = new long[FINE_BUCKETS];
        long[] a = new long[COARSE_BUCKETS];
        int perCoarse = FINE_BUCKETS / COARSE_BUCKETS;
        for (int j = 0; j < FINE_BUCKETS; j++) {
            b[j] = Math.round(TRANSACTIONS * weight[j] / total);
            a[j / perCoarse] += b[j];
        }
        return new Buckets(a, b);
    }
}
