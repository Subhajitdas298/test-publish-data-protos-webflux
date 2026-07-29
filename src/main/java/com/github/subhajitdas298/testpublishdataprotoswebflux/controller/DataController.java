package com.github.subhajitdas298.testpublishdataprotoswebflux.controller;

import com.github.subhajitdas298.testpublishdataprotoswebflux.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotoswebflux.service.ProtoDataService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "*")
public class DataController {

    private final ProtoDataService protoDataService;
    private final JsonDataService jsonDataService;

    public DataController(ProtoDataService protoDataService, JsonDataService jsonDataService) {
        this.protoDataService = protoDataService;
        this.jsonDataService = jsonDataService;
    }

    @GetMapping(value = "/api/data", produces = "application/x-protobuf")
    public Mono<byte[]> getProtoData() {
        return protoDataService.getData();
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getJsonData() {
        return jsonDataService.getData();
    }
}
