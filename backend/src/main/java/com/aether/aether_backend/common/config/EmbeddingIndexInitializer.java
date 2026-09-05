package com.aether.aether_backend.common.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.service.embedding.EmbeddingClient;
import com.aether.aether_backend.service.vectorstore.VectorStore;

/**
 * Boot-time indexer: ensures all existing (non-deleted) atoms are embedded and
 * present in the vector store so the discovery pipeline can find them. The
 * vector store is in-memory by default, so this runs on every startup.
 */
@Component
public class EmbeddingIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexInitializer.class);

    private final KnowledgeAtomRepository atomRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public EmbeddingIndexInitializer(KnowledgeAtomRepository atomRepository,
                                     EmbeddingClient embeddingClient,
                                     VectorStore vectorStore) {
        this.atomRepository = atomRepository;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexExistingAtoms() {
        if (vectorStore.size() > 0) {
            log.info("Vector store already contains {} item(s); skip boot index.", vectorStore.size());
            return;
        }
        List<KnowledgeAtom> atoms = atomRepository.findAll();
        for (KnowledgeAtom atom : atoms) {
            vectorStore.upsert(atom.getId(), embeddingClient.embed(atom.getContentText()));
        }
        log.info("Vector store boot index complete: {} atom(s) embedded ({} dims).",
                atoms.size(), embeddingClient.dimensions());
    }
}
