package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testdataprotos.Root;
import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JsonDataService {

    // Cached as bytes rather than a String: encoding a ~57 MB String makes Netty reserve a
    // worst-case-sized direct buffer, which exceeds the container's direct-memory limit.
    private final DataRepository dataRepository;
    private final Map<Integer, Mono<byte[]>> datasets = new ConcurrentHashMap<>();

    public JsonDataService(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    public Mono<byte[]> getData(int size) {
        return datasets.computeIfAbsent(size, s -> dataRepository.findData(s)
                .map(JsonDataService::toJson)
                .map(json -> json.getBytes(StandardCharsets.UTF_8))
                .cache());
    }

    private static String toJson(byte[] proto) {
        try {
            return JsonFormat.printer().print(Root.parseFrom(proto));
        } catch (InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }
}
