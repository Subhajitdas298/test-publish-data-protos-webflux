package com.github.subhajitdas298.testpublishdataprotoswebflux.repository;

import com.github.subhajitdas298.testdataprotos.DataEntry;
import com.github.subhajitdas298.testdataprotos.DateRecord;
import com.github.subhajitdas298.testdataprotos.Root;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.SplittableRandom;

@Repository
public class DataRepository {

    private static final int DAYS = 1;
    private static final int RECORDS_PER_FIELD_PER_DAY = 10_000_000;
    private static final long SEED = 0L;
    private static final double MAX_VALUE = 1000.0;

    // The dataset is generated into a Root message only once: this Mono is built with
    // .cache() so every subscriber downstream (both services, every request) replays the
    // single cached result instead of regenerating it. The generation runs on the
    // boundedElastic scheduler so it never occupies an event-loop thread.
    private final Mono<Root> dataset = Mono.fromCallable(this::buildDataset)
            .subscribeOn(Schedulers.boundedElastic())
            .cache();

    public Mono<Root> findData() {
        return dataset;
    }

    private Root buildDataset() {
        SplittableRandom random = new SplittableRandom(SEED);

        DataEntry.Builder entryBuilder = DataEntry.newBuilder();
        for (int day = 0; day < DAYS; day++) {
            entryBuilder.addDates(generateDayRecord(random));
        }

        return Root.newBuilder().addData(entryBuilder.build()).build();
    }

    // Only field `a` is populated; the rest of the proto's fields are left empty.
    private DateRecord generateDayRecord(SplittableRandom random) {
        DateRecord.Builder recordBuilder = DateRecord.newBuilder();

        for (int i = 0; i < RECORDS_PER_FIELD_PER_DAY; i++) {
            recordBuilder.addA(random.nextDouble() * MAX_VALUE);
        }

        return recordBuilder.build();
    }
}
