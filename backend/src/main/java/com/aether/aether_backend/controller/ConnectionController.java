package com.aether.aether_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.api.Result;
import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.dto.ConnectionResponse;
import com.aether.aether_backend.service.KnowledgeConnectionService;

/**
 * Read API for proactively discovered connections (Epic 2 output).
 */
@RestController
@RequestMapping("/api/v1")
public class ConnectionController {

    private final KnowledgeConnectionService service;

    public ConnectionController(KnowledgeConnectionService service) {
        this.service = service;
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
}
