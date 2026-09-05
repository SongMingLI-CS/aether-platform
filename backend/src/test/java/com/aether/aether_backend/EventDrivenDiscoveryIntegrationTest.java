package com.aether.aether_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.KnowledgeConnection;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.repository.KnowledgeConnectionRepository;
import com.aether.aether_backend.service.KnowledgeAtomService;
import com.aether.aether_backend.service.vectorstore.VectorStore;

/**
 * Verifies the real event-driven path of Epic 2: {@code KnowledgeAtomService.create()}
 * publishes {@code AtomCreatedEvent}, and the AFTER_COMMIT + @Async listener re-embeds
 * and discovers connections. Intentionally NOT @Transactional so the AFTER_COMMIT
 * listeners actually fire during the test (instead of being deferred to the test's commit).
 */
@SpringBootTest
@Testcontainers
class EventDrivenDiscoveryIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("aether_db")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private KnowledgeAtomService atomService;

    @Autowired
    private KnowledgeConnectionRepository connectionRepository;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void creatingSimilarAtoms_viaEvent_discoversConnection() {
        KnowledgeAtom a = atomService.create(new AtomCreateRequest(
                "Spring Boot 微服务实践笔记 第一版 完整记录", ContentType.TEXT));
        KnowledgeAtom b = atomService.create(new AtomCreateRequest(
                "Spring Boot 微服务实践笔记 第二版 扩展记录", ContentType.TEXT));

        awaitConnections(1);

        List<KnowledgeConnection> connections = connectionRepository.findAll();
        assertThat(connections).hasSize(1);
        KnowledgeConnection connection = connections.get(0);
        assertThat(connection.getSourceAtomId()).isIn(a.getId(), b.getId());
        assertThat(connection.getTargetAtomId()).isIn(a.getId(), b.getId());
        assertThat(vectorStore.size()).isGreaterThanOrEqualTo(2);
    }

    private void awaitConnections(int expected) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (connectionRepository.count() >= expected) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
