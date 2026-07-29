package com.github.subhajitdas298.testpublishdataprotoswebflux.controller;

import com.github.subhajitdas298.testpublishdataprotoswebflux.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotoswebflux.service.ProtoDataService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;

@RestController
public class DataController {

    private static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final ProtoDataService protoDataService;
    private final JsonDataService jsonDataService;

    public DataController(ProtoDataService protoDataService, JsonDataService jsonDataService) {
        this.protoDataService = protoDataService;
        this.jsonDataService = jsonDataService;
    }

    @GetMapping(value = "/api/data", produces = "application/x-protobuf")
    public Mono<Void> getProtoData(ServerWebExchange exchange) {
        return respond(exchange, MediaType.valueOf("application/x-protobuf"),
                protoDataService.getData(), protoDataService.getGzippedData(), protoDataService.getETag());
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> getJsonData(ServerWebExchange exchange) {
        return respond(exchange, MediaType.APPLICATION_JSON,
                jsonDataService.getData(), jsonDataService.getGzippedData(), jsonDataService.getETag());
    }

    // plain/gzipped/etag are all precomputed once upstream; this only picks which to serve.
    private Mono<Void> respond(ServerWebExchange exchange, MediaType contentType,
                                Mono<byte[]> plain, Mono<byte[]> gzipped, Mono<String> etagMono) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        response.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        response.getHeaders().setCacheControl(CACHE_CONTROL);

        return etagMono.flatMap(etag -> {
            response.getHeaders().setETag(etag);

            if (request.getHeaders().getIfNoneMatch().stream().anyMatch(value -> value.equals("*") || value.equals(etag))) {
                response.setStatusCode(HttpStatus.NOT_MODIFIED);
                return response.setComplete();
            }

            boolean acceptsGzip = acceptsGzip(request);
            response.getHeaders().setContentType(contentType);
            if (acceptsGzip) {
                response.getHeaders().set(HttpHeaders.CONTENT_ENCODING, "gzip");
            }

            return (acceptsGzip ? gzipped : plain).flatMap(bytes -> {
                response.getHeaders().setContentLength(bytes.length);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
            });
        });
    }

    private static boolean acceptsGzip(ServerHttpRequest request) {
        String acceptEncoding = request.getHeaders().getFirst(HttpHeaders.ACCEPT_ENCODING);
        return acceptEncoding != null && acceptEncoding.toLowerCase(Locale.ROOT).contains("gzip");
    }
}
