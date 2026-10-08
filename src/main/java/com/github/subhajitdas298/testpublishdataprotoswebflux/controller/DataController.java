package com.github.subhajitdas298.testpublishdataprotoswebflux.controller;

import com.github.subhajitdas298.testpublishdataprotoswebflux.repository.DataRepository;
import com.github.subhajitdas298.testpublishdataprotoswebflux.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotoswebflux.service.ProtoDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "*", exposedHeaders = DataController.LENGTH_HEADER)
public class DataController {

    /** Uncompressed body size, readable cross-origin so the UI can show download progress. */
    static final String LENGTH_HEADER = "X-Data-Length";

    private final ProtoDataService protoDataService;
    private final JsonDataService jsonDataService;

    public DataController(ProtoDataService protoDataService, JsonDataService jsonDataService) {
        this.protoDataService = protoDataService;
        this.jsonDataService = jsonDataService;
    }

    @GetMapping(value = "/api/data", produces = "application/x-protobuf")
    public Mono<ResponseEntity<byte[]>> getProtoData(@RequestParam(defaultValue = "10000000") int size) {
        return protoDataService.getData(validSize(size)).map(DataController::withLength);
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<byte[]>> getJsonData(@RequestParam(defaultValue = "10000000") int size) {
        return jsonDataService.getData(validSize(size)).map(DataController::withLength);
    }

    private static int validSize(int size) {
        if (!DataRepository.SAMPLE_SIZES.contains(size)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size must be one of " + new java.util.TreeSet<>(DataRepository.SAMPLE_SIZES));
        }
        return size;
    }

    private static ResponseEntity<byte[]> withLength(byte[] body) {
        return ResponseEntity.ok().header(LENGTH_HEADER, String.valueOf(body.length)).body(body);
    }
}
