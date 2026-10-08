package com.github.subhajitdas298.testpublishdataprotoswebflux.repository;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves the precomputed protobuf-encoded datasets bundled in {@code data/dataset-<size>.bin}:
 * one {@code Root} message each, with a single day whose field {@code a} holds the first
 * {@code size} values of the same seeded sequence (uniform in [0, 1000)).
 */
@Repository
public class DataRepository {

    public static final int FULL_SIZE = 10_000_000;
    /** Sample sizes a client may request; one bundled file each. */
    public static final Set<Integer> SAMPLE_SIZES = Set.of(10_000, 100_000, 1_000_000, FULL_SIZE);

    // Each file is read only once (on first request): the Mono is built with .cache() so
    // every subscriber downstream replays the single result. The blocking read runs on the
    // boundedElastic scheduler so it never occupies an event-loop thread.
    private final Map<Integer, Mono<byte[]>> datasets = new ConcurrentHashMap<>();

    public Mono<byte[]> findData(int size) {
        return datasets.computeIfAbsent(size, s -> Mono.fromCallable(() -> load(s))
                .subscribeOn(Schedulers.boundedElastic())
                .cache());
    }

    private static byte[] load(int size) {
        String resource = "data/dataset-" + size + ".bin";
        try (var in = new ClassPathResource(resource).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load precomputed dataset: " + resource, e);
        }
    }
}
