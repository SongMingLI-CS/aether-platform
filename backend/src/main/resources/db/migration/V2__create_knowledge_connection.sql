-- ============================================================
-- V2: knowledge connections discovered by the AI pipeline
-- One row per ordered (source, target) atom pair; uniqueness is
-- enforced on the normalized (minId, maxId) order.
-- ============================================================
CREATE TABLE t_knowledge_connection (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    source_atom_id BIGINT       NOT NULL,
    target_atom_id BIGINT       NOT NULL,
    similarity     DOUBLE       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    reason         VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_conn_source (source_atom_id),
    KEY idx_conn_target (target_atom_id),
    CONSTRAINT uk_conn_pair UNIQUE (source_atom_id, target_atom_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
