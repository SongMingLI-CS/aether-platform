package com.aether.aether_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.SearchHitResponse;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.service.HybridSearchService;
import com.aether.aether_backend.service.embedding.EmbeddingClient;
import com.aether.aether_backend.service.vectorstore.VectorStore;

/**
 * Hybrid search (roadmap #2) against a real MySQL: validates the V4 full-text
 * index (ngram parser), the {@code MATCH ... AGAINST} native query, and the RRF
 * fusion of full-text + vector recall.
 *
 * <p>No {@code @Transactional}: InnoDB only indexes committed rows into the
 * FULLTEXT index, so rows must be flushed/committed before {@code MATCH ... AGAINST}
 * can see them.
 */
@SpringBootTest
@Testcontainers
class HybridSearchIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("aether_db")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private KnowledgeAtomRepository atomRepository;
    @Autowired
    private HybridSearchService hybridSearchService;
    @Autowired
    private EmbeddingClient embeddingClient;
    @Autowired
    private VectorStore vectorStore;

    @BeforeEach
    void clearVectorStore() {
        // In-memory vector store is a singleton across the test context; reset
        // it so each test's semantic recall is deterministic.
        vectorStore.clear();
    }

    @AfterEach
    void cleanDb() {
        atomRepository.deleteAll(); // logical delete (excluded from reads/FTS by is_deleted)
    }

    @Test
    void fullTextQuery_recallsLiteralMatches() {
        atomRepository.saveAndFlush(new KnowledgeAtom.Builder(
                "Redis 缓存击穿 CAS 锁 方案", ContentType.TEXT).build());
        KnowledgeAtom queue = atomRepository.saveAndFlush(new KnowledgeAtom.Builder(
                "高并发异步队列 Outbox 模式 事务消息", ContentType.TEXT).build());
        atomRepository.saveAndFlush(new KnowledgeAtom.Builder(
                "线性代数的矩阵乘法与特征值", ContentType.TEXT).build());

        List<Object[]> hits = atomRepository.fullTextSearchIds("高并发异步队列", 10);

        assertThat(hits).isNotEmpty();
        List<Long> ids = hits.stream().map(row -> ((Number) row[0]).longValue()).toList();
        assertThat(ids.get(0)).isEqualTo(queue.getId());
    }

    @Test
    void hybridSearch_ranksRelevantAtomFirst() {
        KnowledgeAtom redis = atomRepository.saveAndFlush(new KnowledgeAtom.Builder(
                "Redis 缓存穿透与缓存雪崩的解决方案", ContentType.TEXT).build());
        KnowledgeAtom queue = atomRepository.saveAndFlush(new KnowledgeAtom.Builder(
                "如何设计高并发异步消息队列", ContentType.TEXT).build());
        atomRepository.saveAndFlush(new KnowledgeAtom.Builder(
                "线性代数的矩阵乘法", ContentType.TEXT).build());

        vectorStore.upsert(redis.getId(), embeddingClient.embed(redis.getContentText()));
        vectorStore.upsert(queue.getId(), embeddingClient.embed(queue.getContentText()));

        List<SearchHitResponse> results = hybridSearchService.search("高并发异步队列设计", 10);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).atom().id()).isEqualTo(queue.getId());
    }
}
