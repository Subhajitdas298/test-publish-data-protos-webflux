package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProtoDataService {

    private final DataRepository dataRepository;

    public ProtoDataService(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    // The bundled files already are the wire format, so they are served as they are.
    public Mono<byte[]> getData(int size) {
        return dataRepository.findData(size);
    }
}
