package com.aether.aether_backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.domain.KnowledgeConnection;

public interface KnowledgeConnectionRepository extends JpaRepository<KnowledgeConnection, Long> {

    Optional<KnowledgeConnection> findBySourceAtomIdAndTargetAtomId(long sourceAtomId, long targetAtomId);

    /**
     * Connections involving one atom (as source or target), newest similarity first.
     */
    @Query("""
            select c from KnowledgeConnection c
            where c.sourceAtomId = :atomId or c.targetAtomId = :atomId
            order by c.similarity desc, c.id desc
            """)
    Page<KnowledgeConnection> findByAtomId(@Param("atomId") long atomId, Pageable pageable);

    /**
     * Paginated connections with optional status / similarity filters.
     */
    @Query("""
            select c from KnowledgeConnection c
            where (:status is null or c.status = :status)
              and (:minSimilarity is null or c.similarity >= :minSimilarity)
            order by c.similarity desc, c.id desc
            """)
    Page<KnowledgeConnection> search(@Param("status") ConnectionStatus status,
                                     @Param("minSimilarity") Double minSimilarity,
                                     Pageable pageable);
}