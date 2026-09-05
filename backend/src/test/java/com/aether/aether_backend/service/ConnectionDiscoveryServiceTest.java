package com.aether.aether_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.KnowledgeConnection;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.repository.KnowledgeConnectionRepository;
import com.aether.aether_backend.service.embedding.EmbeddingClient;
import com.aether.aether_backend.service.vectorstore.ScoredAtom;
import com.aether.aether_backend.service.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class ConnectionDiscoveryServiceTest {

    @Mock
    private KnowledgeAtomRepository atomRepository;
    @Mock
    private KnowledgeConnectionRepository connectionRepository;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private ConnectionDiscoveryService discoveryService;

    private static final float[] VECTOR = new float[8];

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(discoveryService, "minSimilarity", 0.6);
        ReflectionTestUtils.setField(discoveryService, "topK", 5);
    }

    @Test
    void processAtom_indexesAtomAndCreatesConnectionForCloseHits() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("new note", ContentType.TEXT).build();
        atom.setId(2L);
        when(atomRepository.findById(2L)).thenReturn(Optional.of(atom));
        when(embeddingClient.embed("new note")).thenReturn(VECTOR);
        when(vectorStore.search(VECTOR, 5))
                .thenReturn(List.of(new ScoredAtom(1L, 0.92), new ScoredAtom(3L, 0.31)));
        when(atomRepository.findById(1L))
                .thenReturn(Optional.of(new KnowledgeAtom.Builder("old note", ContentType.TEXT).build()));
        when(connectionRepository.findBySourceAtomIdAndTargetAtomId(1L, 2L)).thenReturn(Optional.empty());

        discoveryService.processAtom(2L);

        verify(vectorStore).upsert(2L, VECTOR);
        verify(connectionRepository).save(any(KnowledgeConnection.class));
    }

    @Test
    void processAtom_normalizesPairOrderToMinMax() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("new note", ContentType.TEXT).build();
        atom.setId(1L);
        when(atomRepository.findById(1L)).thenReturn(Optional.of(atom));
        when(embeddingClient.embed("new note")).thenReturn(VECTOR);
        when(vectorStore.search(VECTOR, 5)).thenReturn(List.of(new ScoredAtom(2L, 0.9)));
        when(atomRepository.findById(2L)).thenReturn(Optional.of(new KnowledgeAtom.Builder("x", ContentType.TEXT).build()));
        when(connectionRepository.findBySourceAtomIdAndTargetAtomId(1L, 2L)).thenReturn(Optional.empty());

        discoveryService.processAtom(1L);

        verify(connectionRepository).save(any(KnowledgeConnection.class));
    }

    @Test
    void processAtom_skipsSelfAndSubThresholdHits() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("new note", ContentType.TEXT).build();
        atom.setId(1L);
        when(atomRepository.findById(1L)).thenReturn(Optional.of(atom));
        when(embeddingClient.embed("new note")).thenReturn(VECTOR);
        when(vectorStore.search(VECTOR, 5))
                .thenReturn(List.of(new ScoredAtom(1L, 0.99), new ScoredAtom(2L, 0.2)));

        discoveryService.processAtom(1L);

        verify(connectionRepository, never()).save(any(KnowledgeConnection.class));
    }

    @Test
    void processAtom_skipsAlreadyExistingPair() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("new note", ContentType.TEXT).build();
        atom.setId(2L);
        when(atomRepository.findById(2L)).thenReturn(Optional.of(atom));
        when(embeddingClient.embed("new note")).thenReturn(VECTOR);
        when(vectorStore.search(VECTOR, 5)).thenReturn(List.of(new ScoredAtom(1L, 0.9)));
        when(connectionRepository.findBySourceAtomIdAndTargetAtomId(1L, 2L))
                .thenReturn(Optional.of(new KnowledgeConnection(1L, 2L, 0.9, ConnectionStatus.PENDING, "r")));

        discoveryService.processAtom(2L);

        verify(connectionRepository, never()).save(any(KnowledgeConnection.class));
    }

    @Test
    void processAtom_missingAtom_doesNothing() {
        when(atomRepository.findById(99L)).thenReturn(Optional.empty());

        discoveryService.processAtom(99L);

        verify(vectorStore, never()).upsert(anyLong(), any(float[].class));
    }
}
