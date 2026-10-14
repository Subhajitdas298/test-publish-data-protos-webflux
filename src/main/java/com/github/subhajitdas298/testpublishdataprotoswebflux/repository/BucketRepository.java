package com.github.subhajitdas298.testpublishdataprotoswebflux.repository;

import org.springframework.stereotype.Repository;

import java.util.SplittableRandom;

/**
 * Synthetic score distribution of a fraud model: most transactions get a low score (a long
 * decaying tail), and a small fraud population clusters around a high score. {@code a} holds
 * the <em>expected</em> counts, {@code b} the finer-grained counts actually observed: the same
 * curve, with a little noise and some buckets well above their expected value. Seeded, so every
 * start serves the same counts.
 */
@Repository
public class BucketRepository {

    private static final int MAX_SCORE = 1000;
    private static final int FINE_BUCKETS = 100;
    private static final int COARSE_BUCKETS = 10;
    private static final long TRANSACTIONS = 1_000_000L;
    private static final double FRAUD_SHARE = 0.03;
    private static final double SPIKE_CHANCE = 1.0 / 6;
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
            weight[j] = (1 - FRAUD_SHARE) * legit / 110.0 + FRAUD_SHARE * fraud / 80.0;
            total += weight[j];
        }

        long[] b = new long[FINE_BUCKETS];
        double[] expected = new double[COARSE_BUCKETS];
        int perCoarse = FINE_BUCKETS / COARSE_BUCKETS;
        for (int j = 0; j < FINE_BUCKETS; j++) {
            double expectedCount = TRANSACTIONS * weight[j] / total;
            expected[j / perCoarse] += expectedCount;
            // Observed = expected with +-6% noise; about one bucket in six runs 30-90% hot.
            double factor = 1.0 + (random.nextDouble() - 0.5) * 0.12;
            if (random.nextDouble() < SPIKE_CHANCE) {
                factor += 0.3 + random.nextDouble() * 0.6;
            }
            b[j] = Math.round(expectedCount * factor);
        }

        long[] a = new long[COARSE_BUCKETS];
        for (int i = 0; i < COARSE_BUCKETS; i++) {
            a[i] = Math.round(expected[i]);
        }
        return new Buckets(a, b);
    }
}
