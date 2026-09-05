package com.aether.aether_backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A connection between two knowledge atoms, proactively discovered by the
 * local embedding pipeline (Epic 2). The (source, target) pair is stored in
 * normalized (minId, maxId) order so each unordered pair exists only once.
 */
@Entity
@Table(name = "t_knowledge_connection")
public class KnowledgeConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_atom_id", nullable = false)
    private Long sourceAtomId;

    @Column(name = "target_atom_id", nullable = false)
    private Long targetAtomId;

    @Column(nullable = false)
    private Double similarity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConnectionStatus status;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected KnowledgeConnection() {
        // JPA required no-arg constructor
    }

    public KnowledgeConnection(Long sourceAtomId, Long targetAtomId,
                               Double similarity, ConnectionStatus status, String reason) {
        this.sourceAtomId = sourceAtomId;
        this.targetAtomId = targetAtomId;
        this.similarity = similarity;
        this.status = status;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceAtomId() {
        return sourceAtomId;
    }

    public void setSourceAtomId(Long sourceAtomId) {
        this.sourceAtomId = sourceAtomId;
    }

    public Long getTargetAtomId() {
        return targetAtomId;
    }

    public void setTargetAtomId(Long targetAtomId) {
        this.targetAtomId = targetAtomId;
    }

    public Double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
