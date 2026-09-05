package com.aether.aether_backend.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.exception.BusinessException;
import com.aether.aether_backend.common.exception.ErrorCode;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.dto.AtomResponse;
import com.aether.aether_backend.dto.AtomUpdateRequest;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;

/**
 * Knowledge atom domain service. Creation is always done through the Builder
 * (CTO requirement); deletion is logical (see {@code @SQLDelete} on the entity).
 */
@Service
public class KnowledgeAtomService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final KnowledgeAtomRepository repository;

    public KnowledgeAtomService(KnowledgeAtomRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public KnowledgeAtom create(AtomCreateRequest request) {
        KnowledgeAtom atom = new KnowledgeAtom.Builder(request.contentText(), request.contentType()).build();
        return repository.save(atom);
    }

    @Transactional(readOnly = true)
    public KnowledgeAtom getById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "知识原子不存在: id=" + id));
    }

    @Transactional(readOnly = true)
    public PageResult<AtomResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageResult.from(repository.findAll(pageable), AtomResponse::from);
    }

    @Transactional
    public KnowledgeAtom update(long id, AtomUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "更新请求不能同时为空");
        }
        KnowledgeAtom atom = getById(id);
        if (request.contentText() != null) {
            if (request.contentText().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "contentText 不能为空白");
            }
            atom.setContentText(request.contentText());
        }
        if (request.contentType() != null) {
            atom.setContentType(request.contentType());
        }
        return repository.save(atom);
    }

    @Transactional
    public void delete(long id) {
        KnowledgeAtom atom = getById(id);
        repository.delete(atom); // logical delete via @SQLDelete
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
