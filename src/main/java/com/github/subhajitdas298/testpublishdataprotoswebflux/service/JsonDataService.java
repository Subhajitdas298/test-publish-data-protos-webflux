package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testdataprotos.Root;
import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import com.github.subhajitdas298.testpublishdataprotoswebflux.util.ETags;
import com.github.subhajitdas298.testpublishdataprotoswebflux.util.Gzip;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Service
public class JsonDataService {

    private final Mono<byte[]> jsonDataset;
    private final Mono<byte[]> jsonDatasetGzip;
    private final Mono<String> etag;

    public JsonDataService(DataRepository dataRepository) {
        this.jsonDataset = dataRepository.findData()
                .map(root -> toJson(root).getBytes(StandardCharsets.UTF_8))
                .cache();
        this.jsonDatasetGzip = jsonDataset.map(Gzip::compress).cache();
        this.etag = jsonDataset.map(ETags::strong).cache();
    }

    public Mono<byte[]> getData() {
        return jsonDataset;
    }

    public Mono<byte[]> getGzippedData() {
        return jsonDatasetGzip;
    }

    public Mono<String> getETag() {
        return etag;
    }

    private static String toJson(Root root) {
        try {
            return JsonFormat.printer().print(root);
        } catch (InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }
}
