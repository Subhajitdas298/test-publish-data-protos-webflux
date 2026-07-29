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

    private final Mono<byte[]> jsonDataset;

    public JsonDataService(DataRepository dataRepository) {
        this.jsonDataset = dataRepository.findData()
                .map(root -> toJson(root).getBytes(StandardCharsets.UTF_8))
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
