package com.github.subhajitdas298.testpublishdataprotoswebflux.service;

import com.github.subhajitdas298.testdataprotos.Root;
import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProtoDataService {

    private final Mono<byte[]> protoDataset;

    public ProtoDataService(DataRepository dataRepository) {
        this.protoDataset = dataRepository.findData()
                .map(Root::toByteArray)
                .cache();
    }

    public Mono<byte[]> getData() {
        return protoDataset;
    }
}
