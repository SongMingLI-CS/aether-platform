-- ============================================================
-- V3: distinguish manually-created connections from auto-discovered ones
-- ============================================================
ALTER TABLE t_knowledge_connection
    ADD COLUMN origin VARCHAR(10) NOT NULL DEFAULT 'AUTO';
