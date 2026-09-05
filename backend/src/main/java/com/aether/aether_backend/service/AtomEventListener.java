package com.aether.aether_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.aether.aether_backend.domain.event.AtomCreatedEvent;

/**
 * Placeholder subscriber for AtomCreatedEvent. Epic 2 will plug the real
 * "vectorise + search + persist connections" pipeline in here.
 */
@Component
public class AtomEventListener {

    private static final Logger log = LoggerFactory.getLogger(AtomEventListener.class);

    @Async
    @EventListener
    public void onAtomCreated(AtomCreatedEvent event) {
        log.info(">>> [async] Connection discovery will process new atom id={}", event.atomId());
    }
}
