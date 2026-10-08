package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testdataprotos.Root;
import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProtoDataService {

    private final DataRepository dataRepository;
    private final Map<Integer, Mono<byte[]>> datasets = new ConcurrentHashMap<>();

    public ProtoDataService(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    public Mono<byte[]> getData(int size) {
        return datasets.computeIfAbsent(size, s -> dataRepository.findData(s)
                .map(Root::toByteArray)
                .cache());
    }
}
