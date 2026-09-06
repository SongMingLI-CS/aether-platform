package com.aether.aether_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.SearchHitResponse;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.service.embedding.EmbeddingClient;
import com.aether.aether_backend.service.vectorstore.ScoredAtom;
import com.aether.aether_backend.service.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private KnowledgeAtomRepository atomRepository;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private HybridSearchService service;

    @Test
    void search_fusesVectorAndTextRanksWithRrf() {
        when(embeddingClient.embed("q")).thenReturn(new float[8]);
        when(vectorStore.search(any(float[].class), eq(20)))
                .thenReturn(List.of(new ScoredAtom(1L, 0.9), new ScoredAtom(2L, 0.8)));
        when(atomRepository.fullTextSearchIds("q", 20))
                .thenReturn(List.of(new Object[]{2L, 5.0}, new Object[]{3L, 4.0}));
        when(atomRepository.findAllById(any()))
                .thenReturn(List.of(atom(1L, "one"), atom(2L, "two"), atom(3L, "three")));

        List<SearchHitResponse> result = service.search("q", 20);

        assertThat(result).hasSize(3);
        // id 2 appears in both recall lists, so its fused RRF score ranks first.
        assertThat(result.get(0).atom().id()).isEqualTo(2L);
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }

    @Test
    void search_blankQuery_returnsEmpty() {
        assertThat(service.search("   ", 20)).isEmpty();
    }

    @Test
    void search_clampsLimitToMax() {
        when(embeddingClient.embed("q")).thenReturn(new float[8]);
        when(vectorStore.search(any(float[].class), eq(50)))
                .thenReturn(List.of(new ScoredAtom(1L, 0.9)));
        when(atomRepository.fullTextSearchIds("q", 50)).thenReturn(List.of());
        when(atomRepository.findAllById(any())).thenReturn(List.of(atom(1L, "one")));

        List<SearchHitResponse> result = service.search("q", 999);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).atom().id()).isEqualTo(1L);
    }

    private static KnowledgeAtom atom(long id, String contentText) {
        KnowledgeAtom atom = new KnowledgeAtom.Builder(contentText, ContentType.TEXT).build();
        atom.setId(id);
        atom.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        atom.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        atom.setVersion(0L);
        return atom;
    }
}
