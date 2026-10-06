package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testdataprotos.Root;
import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Service
public class JsonDataService {

    // Cached as bytes rather than a String: encoding a ~57 MB String makes Netty reserve a
    // worst-case-sized direct buffer, which exceeds the container's direct-memory limit.
    private final Mono<byte[]> jsonDataset;

    public JsonDataService(DataRepository dataRepository) {
        this.jsonDataset = dataRepository.findData()
                .map(JsonDataService::toJson)
                .map(json -> json.getBytes(StandardCharsets.UTF_8))
                .cache();
    }

    public Mono<byte[]> getData() {
        return jsonDataset;
    }

    private static String toJson(Root root) {
        try {
            return JsonFormat.printer().print(root);
        } catch (InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }
}
