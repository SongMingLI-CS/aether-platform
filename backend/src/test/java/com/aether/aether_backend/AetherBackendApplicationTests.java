package com.aether.aether_backend;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.service.KnowledgeAtomService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests running against a real MySQL (Testcontainers).
 * They verify JPA mapping, persistence and the logical-delete behaviour.
 */
@SpringBootTest
@Testcontainers
class AetherBackendApplicationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("aether_db")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private KnowledgeAtomRepository repository;

    @Autowired
    private KnowledgeAtomService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void create_persistsAtomWithAuditFields() {
        KnowledgeAtom created = service.create(new AtomCreateRequest("spring data rocks", ContentType.TEXT));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getDeleted()).isFalse();
        assertThat(created.getVersion()).isEqualTo(0L);
        assertThat(created.getCreatedAt()).isNotNull();

        Optional<KnowledgeAtom> found = repository.findById(created.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getContentText()).isEqualTo("spring data rocks");
    }

    @Test
    void logicalDelete_hidesRowButKeepsPhysicalRecord() {
        KnowledgeAtom atom = service.create(new AtomCreateRequest("to be deleted", ContentType.TEXT));

        service.delete(atom.getId());

        // @SQLRestriction hides the row from JPA reads...
        assertThat(repository.findById(atom.getId())).isEmpty();

        // ...but the row still exists in MySQL with is_deleted = 1.
        Integer physicalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_knowledge_atom WHERE id = ? AND is_deleted = 1",
                Integer.class, atom.getId());
        assertThat(physicalRows).isEqualTo(1);
    }
}

