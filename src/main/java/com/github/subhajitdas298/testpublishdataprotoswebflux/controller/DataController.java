package com.github.subhajitdas298.testpublishdataprotoswebflux.controller;

import com.github.subhajitdas298.testpublishdataprotoswebflux.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotoswebflux.service.ProtoDataService;
import com.github.subhajitdas298.testpublishdataprotoswebflux.util.Gzip;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Locale;

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

    // Compresses fresh per request (offloaded off the Netty event loop); no-store means no HTTP caching.
    private Mono<Void> respond(ServerWebExchange exchange, MediaType contentType, Mono<byte[]> data) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(contentType);
        response.getHeaders().setCacheControl(CacheControl.noStore());

        boolean acceptsGzip = acceptsGzip(request);
        Mono<byte[]> body = data;
        if (acceptsGzip) {
            response.getHeaders().set(HttpHeaders.CONTENT_ENCODING, "gzip");
            body = data.flatMap(bytes -> Mono.fromCallable(() -> Gzip.compress(bytes))
                    .subscribeOn(Schedulers.boundedElastic()));
        }

        return body.flatMap(bytes -> {
            response.getHeaders().setContentLength(bytes.length);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        });
    }

    private static boolean acceptsGzip(ServerHttpRequest request) {
        String acceptEncoding = request.getHeaders().getFirst(HttpHeaders.ACCEPT_ENCODING);
        return acceptEncoding != null && acceptEncoding.toLowerCase(Locale.ROOT).contains("gzip");
    }
}
