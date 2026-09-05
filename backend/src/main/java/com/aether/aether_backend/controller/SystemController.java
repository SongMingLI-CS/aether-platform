package com.aether.aether_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aether.aether_backend.common.api.Result;

/**
 * System-level endpoints (liveness / version), always behind the unified Result envelope.
 */
@RestController
public class SystemController {

    @GetMapping("/api/ping")
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of("status", "Aether Core-Zero Online"));
    }
}
