package com.aether.aether_backend.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aether.aether_backend.domain.event.ConnectionDiscoveredEvent;

import jakarta.annotation.PreDestroy;

/**
 * Holds live SSE connections and fans newly discovered connections out to them.
 * The Electron desktop app subscribes to this stream and raises a native
 * notification popup for each {@code connection-discovered} event. A periodic
 * comment frame keeps the long-lived connections alive through proxies.
 */
@Service
public class ConnectionStreamService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionStreamService.class);
    private static final long NO_TIMEOUT = 0L;
    private static final long HEARTBEAT_SECONDS = 15L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);
        ScheduledFuture<?> beat = heartbeat.scheduleAtFixedRate(
                () -> send(emitter, SseEmitter.event().comment("ping")),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        Runnable cleanup = () -> {
            emitters.remove(emitter);
            beat.cancel(false);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    @EventListener
    public void onConnectionDiscovered(ConnectionDiscoveredEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name("connection-discovered").data(event.connection()));
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE emitter send failed, dropping client: {}", e.getMessage());
            emitters.remove(emitter);
            emitter.complete();
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeat.shutdownNow();
    }
}
