package com.aether.aether_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.api.Result;
import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.dto.AtomResponse;
import com.aether.aether_backend.dto.AtomUpdateRequest;
import com.aether.aether_backend.service.KnowledgeAtomService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API for knowledge atoms (Epic 1).
 */
@RestController
@RequestMapping("/api/v1/atoms")
@Tag(name = "知识原子", description = "知识原子 CRUD（Epic 1）：创建走 Builder、纯文本/Markdown、逻辑删除、乐观锁")
public class AtomController {

    private final KnowledgeAtomService service;

    public AtomController(KnowledgeAtomService service) {
        this.service = service;
    }

    @Operation(summary = "创建知识原子")
    @PostMapping
    public ResponseEntity<Result<AtomResponse>> create(@Valid @RequestBody AtomCreateRequest request) {
        KnowledgeAtom created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(AtomResponse.from(created)));
    }

    @Operation(summary = "分页查询知识原子", description = "支持 contentType 过滤与 keyword 关键词搜索")
    @GetMapping
    public Result<PageResult<AtomResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ContentType contentType,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(page, size, contentType, keyword));
    }

    @Operation(summary = "查询知识原子详情")
    @GetMapping("/{id}")
    public Result<AtomResponse> getById(@PathVariable long id) {
        return Result.ok(AtomResponse.from(service.getById(id)));
    }

    @Operation(summary = "更新知识原子", description = "contentText / contentType 至少提供一个")
    @PatchMapping("/{id}")
    public Result<AtomResponse> update(@PathVariable long id,
                                       @Valid @RequestBody AtomUpdateRequest request) {
        return Result.ok(AtomResponse.from(service.update(id, request)));
    }

    @Operation(summary = "删除知识原子（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        service.delete(id);
        return Result.ok();
    }
}
