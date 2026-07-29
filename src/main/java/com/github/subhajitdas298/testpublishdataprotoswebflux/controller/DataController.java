package com.github.subhajitdas298.testpublishdataprotoswebflux.controller;

import com.github.subhajitdas298.testpublishdataprotoswebflux.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotoswebflux.service.ProtoDataService;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class DataController {

    private final ProtoDataService protoDataService;
    private final JsonDataService jsonDataService;

    public DataController(ProtoDataService protoDataService, JsonDataService jsonDataService) {
        this.protoDataService = protoDataService;
        this.jsonDataService = jsonDataService;
    }

    @GetMapping(value = "/api/data", produces = "application/x-protobuf")
    public Mono<Void> getProtoData(ServerWebExchange exchange) {
        return respond(exchange, MediaType.valueOf("application/x-protobuf"), protoDataService.getData());
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> getJsonData(ServerWebExchange exchange) {
        return respond(exchange, MediaType.APPLICATION_JSON, jsonDataService.getData());
    }

    // Gzip is applied by the server itself, per server.compression in application.yml.
    private Mono<Void> respond(ServerWebExchange exchange, MediaType contentType, Mono<byte[]> data) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(contentType);

        return data.flatMap(bytes -> {
            response.getHeaders().setContentLength(bytes.length);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        });
    }
}
