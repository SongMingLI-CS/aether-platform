package com.aether.aether_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aether.aether_backend.common.api.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * System-level endpoints (liveness / version), always behind the unified Result envelope.
 */
@RestController
@Tag(name = "系统")
public class SystemController {

    @Operation(summary = "存活探测")
    @GetMapping("/api/ping")
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of("status", "Aether Core-Zero Online"));
    }
}
