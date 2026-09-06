-- ============================================================
-- V4: full-text index for hybrid (BM25-style) recall
-- ngram parser tokenizes CJK (and mixed Latin) into bigrams,
-- which is required for Chinese full-text search on MySQL.
-- ============================================================
ALTER TABLE t_knowledge_atom
    ADD FULLTEXT INDEX ft_atom_content (content_text) WITH PARSER ngram;
