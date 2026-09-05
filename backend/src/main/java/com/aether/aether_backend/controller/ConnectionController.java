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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read API for proactively discovered connections (Epic 2 output).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "连接发现", description = "主动连接发现结果查询、状态流转与 SSE 实时推送（Epic 2/3）")
public class ConnectionController {

    private final KnowledgeConnectionService service;
    private final ConnectionStreamService streamService;

    public ConnectionController(KnowledgeConnectionService service,
                                ConnectionStreamService streamService) {
        this.service = service;
        this.streamService = streamService;
    }

    @Operation(summary = "分页查询连接", description = "可按 status 与 minSimilarity 过滤")
    @GetMapping("/connections")
    public Result<PageResult<ConnectionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ConnectionStatus status,
            @RequestParam(required = false) Double minSimilarity) {
        return Result.ok(service.list(page, size, status, minSimilarity));
    }

    @Operation(summary = "查询某个知识原子的连接")
    @GetMapping("/atoms/{id}/connections")
    public Result<PageResult<ConnectionResponse>> listForAtom(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listForAtom(id, page, size));
    }

    @Operation(summary = "流转连接状态", description = "PENDING → CONFIRMED / IGNORED；不允许设回 PENDING")
    @PatchMapping("/connections/{id}")
    public Result<ConnectionResponse> updateStatus(@PathVariable long id,
                                                   @Valid @RequestBody ConnectionStatusUpdateRequest request) {
        return Result.ok(service.updateStatus(id, request.status()));
    }

    @Operation(summary = "订阅连接发现事件（SSE）",
            description = "以 Server-Sent Events 实时推送新发现的连接，事件名 `connection-discovered`，"
                    + "负载为 ConnectionResponse JSON。Electron 桌面弹窗即消费此流。")
    @GetMapping(value = "/connections/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamService.subscribe();
    }
}
