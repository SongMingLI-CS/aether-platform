package com.aether.aether_backend.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.KnowledgeConnection;
import com.aether.aether_backend.domain.event.AtomCreatedEvent;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.repository.KnowledgeConnectionRepository;
import com.aether.aether_backend.service.embedding.EmbeddingClient;
import com.aether.aether_backend.service.vectorstore.ScoredAtom;
import com.aether.aether_backend.service.vectorstore.VectorStore;

/**
 * Proactive connection discovery (Epic 2).
 *
 * <p>When a knowledge atom is created it is: 1) embedded into a local vector,
 * 2) upserted into the vector store, 3) used to search the most similar
 * existing atoms, and 4) each hit above the similarity threshold is persisted
 * as a {@link KnowledgeConnection}. Everything runs locally and off the HTTP
 * request thread.
 */
@Service
public class ConnectionDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionDiscoveryService.class);
    private static final int SNIPPET_LENGTH = 60;

    private final KnowledgeAtomRepository atomRepository;
    private final KnowledgeConnectionRepository connectionRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    @Value("${aether.discovery.min-similarity:0.6}")
    private double minSimilarity;

    @Value("${aether.discovery.top-k:5}")
    private int topK;

    public ConnectionDiscoveryService(KnowledgeAtomRepository atomRepository,
                                      KnowledgeConnectionRepository connectionRepository,
                                      EmbeddingClient embeddingClient,
                                      VectorStore vectorStore) {
        this.atomRepository = atomRepository;
        this.connectionRepository = connectionRepository;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAtomCreated(AtomCreatedEvent event) {
        processAtom(event.atomId());
    }

    /**
     * Embeds + indexes the atom and searches for similar existing atoms.
     * Kept public (non-event path) so tests and batch jobs can run it directly.
     */
    public void processAtom(long atomId) {
        KnowledgeAtom atom = atomRepository.findById(atomId).orElse(null);
        if (atom == null) {
            log.warn("Discovery skipped: atom {} not found.", atomId);
            return;
        }

        float[] vector = embeddingClient.embed(atom.getContentText());
        vectorStore.upsert(atomId, vector);

        List<ScoredAtom> hits = vectorStore.search(vector, topK);
        int created = 0;
        int skipped = 0;
        for (ScoredAtom hit : hits) {
            if (hit.atomId() == atomId || hit.score() < minSimilarity) {
                continue;
            }
            long sourceId = Math.min(atomId, hit.atomId());
            long targetId = Math.max(atomId, hit.atomId());
            if (connectionRepository.findBySourceAtomIdAndTargetAtomId(sourceId, targetId).isPresent()) {
                skipped++;
                continue;
            }
            String reason = buildReason(sourceId, targetId);
            connectionRepository.save(new KnowledgeConnection(
                    sourceId, targetId, hit.score(), ConnectionStatus.PENDING, reason));
            created++;
        }
        log.info(">>> Discovery: atom {} embedded+indexed; {} new connection(s), {} skipped/existing",
                atomId, created, skipped);
    }

    private String buildReason(long sourceId, long targetId) {
        String source = snippet(atomRepository.findById(sourceId).map(KnowledgeAtom::getContentText).orElse(""));
        String target = snippet(atomRepository.findById(targetId).map(KnowledgeAtom::getContentText).orElse(""));
        return "「" + source + "」与「" + target + "」内容语义相近，疑似相关";
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "…";
    }
}
