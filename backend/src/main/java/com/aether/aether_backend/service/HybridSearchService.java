package com.aether.aether_backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.AtomResponse;
import com.aether.aether_backend.dto.SearchHitResponse;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.service.embedding.EmbeddingClient;
import com.aether.aether_backend.service.vectorstore.ScoredAtom;
import com.aether.aether_backend.service.vectorstore.VectorStore;

/**
 * Hybrid semantic search (roadmap #2).
 *
 * <p>Fuses two recall channels with Reciprocal Rank Fusion (RRF):
 * <ol>
 *   <li>Vector recall - {@link EmbeddingClient} + {@link VectorStore} (semantic).</li>
 *   <li>Full-text recall - MySQL {@code MATCH ... AGAINST} (BM25-style lexical).</li>
 * </ol>
 * RRF rewards an atom for ranking high in <em>either</em> list, so a natural
 * language query matches both literal keywords and semantically related atoms.
 * Everything stays local (CTO constraint: no cloud AI).
 */
@Service
public class HybridSearchService {

    private static final double RRF_K = 60.0;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final KnowledgeAtomRepository atomRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public HybridSearchService(KnowledgeAtomRepository atomRepository,
                               EmbeddingClient embeddingClient,
                               VectorStore vectorStore) {
        this.atomRepository = atomRepository;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public List<SearchHitResponse> search(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int effectiveLimit = clampLimit(limit);
        String q = query.trim();

        // Dual recall.
        List<Long> vectorRank = vectorStore.search(embeddingClient.embed(q), effectiveLimit)
                .stream().map(ScoredAtom::atomId).toList();
        List<Long> textRank = atomRepository.fullTextSearchIds(q, effectiveLimit)
                .stream().map(row -> ((Number) row[0]).longValue()).toList();

        // RRF merge (rank 1 contributes 1/(k+1), rank 2 contributes 1/(k+2), ...).
        Map<Long, Double> fused = new LinkedHashMap<>();
        addRrf(fused, vectorRank);
        addRrf(fused, textRank);

        List<Long> ordered = fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(effectiveLimit)
                .map(Map.Entry::getKey)
                .toList();
        if (ordered.isEmpty()) {
            return List.of();
        }

        // Preserve RRF order (findAllById does not guarantee order).
        Map<Long, KnowledgeAtom> byId = atomRepository.findAllById(ordered).stream()
                .collect(Collectors.toMap(KnowledgeAtom::getId, atom -> atom));
        List<SearchHitResponse> result = new ArrayList<>(ordered.size());
        for (Long id : ordered) {
            KnowledgeAtom atom = byId.get(id);
            if (atom != null) {
                result.add(new SearchHitResponse(AtomResponse.from(atom), fused.get(id)));
            }
        }
        return result;
    }

    private void addRrf(Map<Long, Double> fused, List<Long> rankedIds) {
        for (int i = 0; i < rankedIds.size(); i++) {
            double score = 1.0 / (RRF_K + (i + 1));
            fused.merge(rankedIds.get(i), score, Double::sum);
        }
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
