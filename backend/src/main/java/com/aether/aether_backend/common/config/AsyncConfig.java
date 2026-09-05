package com.aether.aether_backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async so domain-event listeners (e.g. future AI connection
 * discovery) run off the HTTP request thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
