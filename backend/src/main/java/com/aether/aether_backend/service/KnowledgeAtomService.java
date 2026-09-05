package com.aether.aether_backend.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.api.PageUtil;
import com.aether.aether_backend.common.exception.BusinessException;
import com.aether.aether_backend.common.exception.ErrorCode;
import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.event.AtomCreatedEvent;
import com.aether.aether_backend.domain.event.AtomDeletedEvent;
import com.aether.aether_backend.domain.event.AtomUpdatedEvent;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.dto.AtomResponse;
import com.aether.aether_backend.dto.AtomUpdateRequest;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;

/**
 * Knowledge atom domain service. Creation is always done through the Builder
 * (CTO requirement); deletion is logical (see {@code @SQLDelete} on the entity).
 * A newly created atom raises an {@link AtomCreatedEvent} that Epic 2's
 * proactive connection discovery will consume.
 */
@Service
public class KnowledgeAtomService {

    private final KnowledgeAtomRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public KnowledgeAtomService(KnowledgeAtomRepository repository,
                                ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public KnowledgeAtom create(AtomCreateRequest request) {
        KnowledgeAtom atom = new KnowledgeAtom.Builder(request.contentText(), request.contentType()).build();
        KnowledgeAtom saved = repository.save(atom);
        eventPublisher.publishEvent(new AtomCreatedEvent(saved.getId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public KnowledgeAtom getById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "知识原子不存在: id=" + id));
    }

    @Transactional(readOnly = true)
    public PageResult<AtomResponse> list(int page, int size, ContentType contentType, String keyword) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PageUtil.clampSize(size));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return PageResult.from(
                repository.search(contentType, normalizedKeyword, pageable), AtomResponse::from);
    }

    @Transactional
    public KnowledgeAtom update(long id, AtomUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "更新请求不能同时为空");
        }
        KnowledgeAtom atom = getById(id);
        boolean contentChanged = false;
        if (request.contentText() != null) {
            if (request.contentText().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "contentText 不能为空白");
            }
            atom.setContentText(request.contentText());
            contentChanged = true;
        }
        if (request.contentType() != null) {
            atom.setContentType(request.contentType());
        }
        KnowledgeAtom saved = repository.save(atom);
        if (contentChanged) {
            eventPublisher.publishEvent(new AtomUpdatedEvent(saved.getId()));
        }
        return saved;
    }

    @Transactional
    public void delete(long id) {
        KnowledgeAtom atom = getById(id);
        repository.delete(atom); // logical delete via @SQLDelete
        eventPublisher.publishEvent(new AtomDeletedEvent(id));
    }
}
