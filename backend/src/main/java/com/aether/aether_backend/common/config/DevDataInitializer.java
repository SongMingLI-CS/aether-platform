package com.aether.aether_backend.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;

/**
 * Seeds one demo atom on startup so a fresh dev database is never empty.
 * Active ONLY under the "dev" profile - never in production or tests.
 */
@Configuration
@Profile("dev")
public class DevDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    @Bean
    public CommandLineRunner seedDemoAtom(KnowledgeAtomRepository repository) {
        return args -> {
            long count = repository.count();
            if (count > 0) {
                log.info("Dev data initializer: {} atom(s) present, skip seeding.", count);
                return;
            }
            KnowledgeAtom demo = new KnowledgeAtom.Builder(
                    "Aether Core-Zero - DB Connection Test - OK", "TEXT").build();
            KnowledgeAtom saved = repository.save(demo);
            log.info("Dev data initializer: seeded demo atom id={}", saved.getId());
        };
    }
}
