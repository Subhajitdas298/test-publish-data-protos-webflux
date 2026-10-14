package com.github.subhajitdas298.testpublishdataprotoswebflux.repository;

/**
 * A fraud-model score distribution over scores 0-1000: {@code a} holds 10 transaction
 * counts (bucket i covers scores 100*i .. 100*i+99) and {@code b} holds 100 counts (bucket j
 * covers 10*j .. 10*j+9). Each {@code a} value is the sum of its ten {@code b} values.
 */
public record Buckets(long[] a, long[] b) {
}
