package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testdataprotos.Root;
import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import com.github.subhajitdas298.testpublishdataprotoswebflux.util.ETags;
import com.github.subhajitdas298.testpublishdataprotoswebflux.util.Gzip;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProtoDataService {

    private final Mono<byte[]> protoDataset;
    private final Mono<byte[]> protoDatasetGzip;
    private final Mono<String> etag;

    public ProtoDataService(DataRepository dataRepository) {
        this.protoDataset = dataRepository.findData()
                .map(Root::toByteArray)
                .cache();
        this.protoDatasetGzip = protoDataset.map(Gzip::compress).cache();
        this.etag = protoDataset.map(ETags::strong).cache();
    }

    public Mono<byte[]> getData() {
        return protoDataset;
    }

    public Mono<byte[]> getGzippedData() {
        return protoDatasetGzip;
    }

    public Mono<String> getETag() {
        return etag;
    }
}
