-- ============================================================
-- V1: initial schema - t_knowledge_atom
-- Mirrors KnowledgeAtom entity mapping (Hibernate validation friendly).
-- ============================================================
CREATE TABLE t_knowledge_atom (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    content_text TEXT         NOT NULL,
    content_type VARCHAR(20)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    is_deleted   BIT(1)       NOT NULL,
    version      BIGINT       NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
