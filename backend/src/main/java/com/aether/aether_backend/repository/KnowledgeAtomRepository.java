package com.aether.aether_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;

/**
 * Spring Data JPA repository for KnowledgeAtom.
 */
public interface KnowledgeAtomRepository extends JpaRepository<KnowledgeAtom, Long> {

    /**
     * Paginated search with optional filters. Deleted rows are excluded by the
     * entity-level @SQLRestriction.
     */
    @Query("""
            select a from KnowledgeAtom a
            where (:contentType is null or a.contentType = :contentType)
              and (:keyword is null or lower(a.contentText) like lower(concat('%', :keyword, '%')))
            """)
    Page<KnowledgeAtom> search(@Param("contentType") ContentType contentType,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}