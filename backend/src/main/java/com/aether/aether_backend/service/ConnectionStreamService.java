package com.aether.aether_backend.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aether.aether_backend.domain.event.ConnectionDiscoveredEvent;

/**
 * Holds live SSE connections and fans newly discovered connections out to them.
 * The Electron desktop app subscribes to this stream and raises a native
 * notification popup for each {@code connection-discovered} event.
 */
@Service
public class ConnectionStreamService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionStreamService.class);
    private static final long NO_TIMEOUT = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @EventListener
    public void onConnectionDiscovered(ConnectionDiscoveredEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("connection-discovered")
                        .data(event.connection()));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE emitter send failed, dropping client: {}", e.getMessage());
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }
}
