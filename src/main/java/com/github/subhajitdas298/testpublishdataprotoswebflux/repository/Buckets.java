package com.github.subhajitdas298.testpublishdataprotoswebflux.repository;

/**
 * A fraud-model score distribution over scores 0-1000: {@code a} holds 10 <em>expected</em>
 * transaction counts (bucket i covers scores 100*i .. 100*i+99) and {@code b} holds 100
 * <em>observed</em> counts (bucket j covers 10*j .. 10*j+9), which follow the same curve with some
 * noise and some buckets above their expected value.
 */
public record Buckets(long[] a, long[] b) {
}
