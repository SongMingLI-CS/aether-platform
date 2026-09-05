package com.aether.aether_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.KnowledgeConnection;
import com.aether.aether_backend.dto.AtomUpdateRequest;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.repository.KnowledgeConnectionRepository;
import com.aether.aether_backend.service.ConnectionDiscoveryService;
import com.aether.aether_backend.service.KnowledgeAtomService;
import com.aether.aether_backend.service.vectorstore.VectorStore;

/**
 * End-to-end Epic 2 flow against a real MySQL: two similar atoms in the vector
 * store produce a persisted KnowledgeConnection after the discovery run.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ConnectionDiscoveryIntegrationTest {

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
    private KnowledgeConnectionRepository connectionRepository;

    @Autowired
    private ConnectionDiscoveryService discoveryService;

    @Autowired
    private KnowledgeAtomService atomService;

    @Autowired
    private VectorStore vectorStore;

    @BeforeEach
    void clearVectorStore() {
        // The in-memory store is a singleton and survives the per-test DB rollback,
        // so reset it to keep each test's similarity search deterministic.
        vectorStore.clear();
    }

    @Test
    void similarAtoms_producePersistedConnection() {
        KnowledgeAtom first = atomRepository.save(new KnowledgeAtom.Builder(
                "Spring Boot 微服务实践笔记 第一版 完整记录", ContentType.TEXT).build());
        KnowledgeAtom second = atomRepository.save(new KnowledgeAtom.Builder(
                "Spring Boot 微服务实践笔记 第二版 扩展记录", ContentType.TEXT).build());

        discoveryService.processAtom(first.getId());
        discoveryService.processAtom(second.getId());

        List<KnowledgeConnection> connections = connectionRepository.findAll();
        assertThat(connections).hasSize(1);
        KnowledgeConnection connection = connections.get(0);
        assertThat(connection.getSourceAtomId()).isIn(first.getId(), second.getId());
        assertThat(connection.getTargetAtomId()).isIn(first.getId(), second.getId());
        assertThat(connection.getStatus()).isNotNull();
        assertThat(connection.getCreatedAt()).isNotNull();
    }

    @Test
    void unrelatedAtoms_produceNoConnection() {
        KnowledgeAtom a = atomRepository.save(new KnowledgeAtom.Builder(
                "线性代数的本质 第一讲", ContentType.TEXT).build());
        KnowledgeAtom b = atomRepository.save(new KnowledgeAtom.Builder(
                "深度神经网络训练完全指南", ContentType.TEXT).build());

        discoveryService.processAtom(a.getId());
        discoveryService.processAtom(b.getId());

        assertThat(connectionRepository.findAll()).isEmpty();
    }

    @Test
    void update_reembedsAndDiscoversNewConnection() {
        KnowledgeAtom a = atomRepository.save(new KnowledgeAtom.Builder(
                "Spring Boot 微服务实践笔记 第一版 完整记录", ContentType.TEXT).build());
        KnowledgeAtom b = atomRepository.save(new KnowledgeAtom.Builder(
                "量子计算与拓扑绝缘体物理", ContentType.TEXT).build());

        // Unrelated content -> no connection yet.
        discoveryService.processAtom(a.getId());
        discoveryService.processAtom(b.getId());
        assertThat(connectionRepository.findAll()).isEmpty();

        // Change b to something similar to a, then re-embed + re-discover
        // (the same code path the AtomUpdatedEvent listener triggers).
        atomService.update(b.getId(),
                new AtomUpdateRequest("Spring Boot 微服务实践笔记 第二版 扩展记录", null));
        discoveryService.processAtom(b.getId());

        List<KnowledgeConnection> connections = connectionRepository.findAll();
        assertThat(connections).hasSize(1);
        KnowledgeConnection connection = connections.get(0);
        assertThat(connection.getSourceAtomId()).isIn(a.getId(), b.getId());
        assertThat(connection.getTargetAtomId()).isIn(a.getId(), b.getId());
    }

    @Test
    void delete_removesVectorSoNoConnectionToDeletedAtom() {
        KnowledgeAtom a = atomRepository.save(new KnowledgeAtom.Builder(
                "Spring Boot 微服务实践笔记 第一版 完整记录", ContentType.TEXT).build());
        KnowledgeAtom b = atomRepository.save(new KnowledgeAtom.Builder(
                "Spring Boot 微服务实践笔记 第二版 扩展记录", ContentType.TEXT).build());

        discoveryService.processAtom(a.getId());
        discoveryService.processAtom(b.getId());
        assertThat(connectionRepository.findAll()).hasSize(1); // a <-> b

        // Soft-delete a and remove its vector (the effect of the AtomDeletedEvent listener,
        // which is @Async and therefore non-deterministic to await here).
        atomService.delete(a.getId());
        vectorStore.remove(a.getId());

        // A new, similar atom must connect to b but NOT to the deleted a.
        KnowledgeAtom c = atomRepository.save(new KnowledgeAtom.Builder(
                "Spring Boot 微服务实践笔记 第三版 全新记录", ContentType.TEXT).build());
        discoveryService.processAtom(c.getId());

        long minAC = Math.min(a.getId(), c.getId());
        long maxAC = Math.max(a.getId(), c.getId());
        assertThat(connectionRepository.findBySourceAtomIdAndTargetAtomId(minAC, maxAC)).isEmpty();

        long minBC = Math.min(b.getId(), c.getId());
        long maxBC = Math.max(b.getId(), c.getId());
        assertThat(connectionRepository.findBySourceAtomIdAndTargetAtomId(minBC, maxBC)).isPresent();
    }
}
