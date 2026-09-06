package com.aether.aether_backend.repository;

import java.util.List;

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

    /**
     * Full-text recall for hybrid search. Returns {@code [atomId, relevance]}
     * rows ranked by {@code MATCH ... AGAINST} relevance (BM25-style lexical
     * score). Native query, so soft-delete is filtered explicitly.
     */
    @Query(value = """
            SELECT a.id, MATCH(a.content_text) AGAINST (:query IN NATURAL LANGUAGE MODE) AS relevance
            FROM t_knowledge_atom a
            WHERE a.is_deleted = 0
              AND MATCH(a.content_text) AGAINST (:query IN NATURAL LANGUAGE MODE) > 0
            ORDER BY relevance DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> fullTextSearchIds(@Param("query") String query, @Param("limit") int limit);
}
