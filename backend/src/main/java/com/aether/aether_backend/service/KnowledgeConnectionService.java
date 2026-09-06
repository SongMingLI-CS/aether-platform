package com.aether.aether_backend.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.api.PageUtil;
import com.aether.aether_backend.common.exception.BusinessException;
import com.aether.aether_backend.common.exception.ErrorCode;
import com.aether.aether_backend.domain.ConnectionOrigin;
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
        Pageable pageable = PageRequest.of(Math.max(page, 0), PageUtil.clampSize(size));
        return map(connectionRepository.search(status, minSimilarity, pageable));
    }

    @Transactional(readOnly = true)
    public PageResult<ConnectionResponse> listForAtom(long atomId, int page, int size) {
        if (!atomRepository.existsById(atomId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识原子不存在: id=" + atomId);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), PageUtil.clampSize(size));
        return map(connectionRepository.findByAtomId(atomId, pageable));
    }

    /**
     * Transitions a connection out of the PENDING state. The only valid targets
     * are CONFIRMED and IGNORED; PENDING is the AI-discovered initial state and
     * cannot be requested by clients.
     */
    @Transactional
    public ConnectionResponse updateStatus(long id, ConnectionStatus status) {
        if (status == ConnectionStatus.PENDING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 仅支持 CONFIRMED 或 IGNORED");
        }
        KnowledgeConnection connection = connectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "连接不存在: id=" + id));
        connection.setStatus(status);
        connectionRepository.save(connection);
        return toResponse(connection);
    }

    /**
     * Creates (or revives) a connection from an explicit user action (drag & drop).
     * Manual connections carry the highest confidence: status = CONFIRMED and
     * similarity = 1.0. The unordered pair is normalized and deduplicated in
     * both directions; an existing PENDING/IGNORED row is overwritten to CONFIRMED
     * instead of raising a conflict.
     */
    @Transactional
    public ConnectionResponse createManual(long sourceAtomId, long targetAtomId) {
        if (sourceAtomId == targetAtomId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能连接同一个知识原子");
        }
        if (!atomRepository.existsById(sourceAtomId) || !atomRepository.existsById(targetAtomId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识原子不存在");
        }
        long a = Math.min(sourceAtomId, targetAtomId);
        long b = Math.max(sourceAtomId, targetAtomId);

        Optional<KnowledgeConnection> existing = connectionRepository.findByAtomPair(a, b);
        KnowledgeConnection connection;
        if (existing.isPresent()) {
            // Revive/overwrite: PENDING or IGNORED becomes CONFIRMED.
            connection = existing.get();
            connection.setStatus(ConnectionStatus.CONFIRMED);
            connection.setOrigin(ConnectionOrigin.MANUAL);
            connection.setSimilarity(1.0);
            connection.setReason("手动建立连接");
            connectionRepository.save(connection);
        } else {
            connection = connectionRepository.save(new KnowledgeConnection(
                    a, b, 1.0, ConnectionStatus.CONFIRMED, "手动建立连接", ConnectionOrigin.MANUAL));
        }
        return toResponse(connection);
    }

    private ConnectionResponse toResponse(KnowledgeConnection connection) {
        Map<Long, String> snippets = fetchSnippets(List.of(connection));
        return ConnectionResponse.from(connection,
                snippets.getOrDefault(connection.getSourceAtomId(), ""),
                snippets.getOrDefault(connection.getTargetAtomId(), ""));
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
}
