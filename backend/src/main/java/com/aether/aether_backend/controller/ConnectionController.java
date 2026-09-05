package com.aether.aether_backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.api.Result;
import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.dto.ConnectionResponse;
import com.aether.aether_backend.dto.ConnectionStatusUpdateRequest;
import com.aether.aether_backend.service.ConnectionStreamService;
import com.aether.aether_backend.service.KnowledgeConnectionService;

import jakarta.validation.Valid;

/**
 * Read API for proactively discovered connections (Epic 2 output).
 */
@RestController
@RequestMapping("/api/v1")
public class ConnectionController {

    private final KnowledgeConnectionService service;
    private final ConnectionStreamService streamService;

    public ConnectionController(KnowledgeConnectionService service,
                                ConnectionStreamService streamService) {
        this.service = service;
        this.streamService = streamService;
    }

    @GetMapping("/connections")
    public Result<PageResult<ConnectionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ConnectionStatus status,
            @RequestParam(required = false) Double minSimilarity) {
        return Result.ok(service.list(page, size, status, minSimilarity));
    }

    @GetMapping("/atoms/{id}/connections")
    public Result<PageResult<ConnectionResponse>> listForAtom(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listForAtom(id, page, size));
    }

    @PatchMapping("/connections/{id}")
    public Result<ConnectionResponse> updateStatus(@PathVariable long id,
                                                   @Valid @RequestBody ConnectionStatusUpdateRequest request) {
        return Result.ok(service.updateStatus(id, request.status()));
    }

    @GetMapping(value = "/connections/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamService.subscribe();
    }
}
