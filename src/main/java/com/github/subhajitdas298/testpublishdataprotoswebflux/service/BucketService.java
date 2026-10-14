package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.BucketRepository;
import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.Buckets;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class BucketService {

    private final BucketRepository bucketRepository;

    public BucketService(BucketRepository bucketRepository) {
        this.bucketRepository = bucketRepository;
    }

    public Mono<Buckets> getBuckets() {
        return Mono.fromSupplier(bucketRepository::findBuckets);
    }
}
