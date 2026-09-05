package com.aether.aether_backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.exception.BusinessException;
import com.aether.aether_backend.common.exception.ErrorCode;
import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.KnowledgeConnection;
import com.aether.aether_backend.dto.ConnectionResponse;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.repository.KnowledgeConnectionRepository;

/**
 * Read-side queries for discovered connections.
 */
@Service
public class KnowledgeConnectionService {

    private static final int SNIPPET_LENGTH = 80;

    private final KnowledgeConnectionRepository connectionRepository;
    private final KnowledgeAtomRepository atomRepository;

    public KnowledgeConnectionService(KnowledgeConnectionRepository connectionRepository,
                                      KnowledgeAtomRepository atomRepository) {
        this.connectionRepository = connectionRepository;
        this.atomRepository = atomRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<ConnectionResponse> list(int page, int size,
                                               ConnectionStatus status, Double minSimilarity) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return map(connectionRepository.search(status, minSimilarity, pageable));
    }

    @Transactional(readOnly = true)
    public PageResult<ConnectionResponse> listForAtom(long atomId, int page, int size) {
        if (!atomRepository.existsById(atomId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识原子不存在: id=" + atomId);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return map(connectionRepository.findByAtomId(atomId, pageable));
    }

    private PageResult<ConnectionResponse> map(Page<KnowledgeConnection> connections) {
        Map<Long, String> snippets = fetchSnippets(connections.getContent());
        return PageResult.from(connections, connection -> ConnectionResponse.from(
                connection,
                snippets.getOrDefault(connection.getSourceAtomId(), ""),
                snippets.getOrDefault(connection.getTargetAtomId(), "")));
    }

    private Map<Long, String> fetchSnippets(List<KnowledgeConnection> connections) {
        List<Long> atomIds = connections.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getSourceAtomId(), c.getTargetAtomId()))
                .distinct()
                .toList();
        return atomRepository.findAllById(atomIds).stream()
                .collect(Collectors.toMap(KnowledgeAtom::getId, this::snippet));
    }

    private String snippet(KnowledgeAtom atom) {
        String text = atom.getContentText();
        return text == null ? "" : text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "…";
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return KnowledgeAtomService.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, KnowledgeAtomService.MAX_PAGE_SIZE);
    }
}
