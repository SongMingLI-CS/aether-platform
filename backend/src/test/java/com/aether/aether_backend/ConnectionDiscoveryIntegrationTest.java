package com.aether.aether_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.repository.KnowledgeConnectionRepository;
import com.aether.aether_backend.service.ConnectionDiscoveryService;

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
}
