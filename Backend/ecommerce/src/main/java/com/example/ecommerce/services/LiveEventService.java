package com.example.ecommerce.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.ecommerce.enums.Role;

import lombok.extern.slf4j.Slf4j;

/**
 * Live updates over Server-Sent Events. Each signed-in browser tab keeps one stream open
 * (GET /api/live/stream); when something changes for a user — an order status, a new order, a new
 * pickup — an event is pushed and the page refreshes the affected data at once.
 * <p>
 * Events are sent only after the surrounding transaction commits, so a page that reloads on an event
 * always sees the new data. Streams are held in memory: fine for one server; behind several servers
 * this would move to a message broker.
 */
@Slf4j
@Service
public class LiveEventService {

    /** Re-open after 30 minutes; the browser reconnects automatically. */
    private static final long STREAM_TIMEOUT_MS = 30L * 60 * 1000;

    private record Subscriber(Long userId, Role role, SseEmitter emitter) {
    }

    private final Map<Long, List<Subscriber>> byUser = new ConcurrentHashMap<>();

    private void remove(Subscriber subscriber) {
        List<Subscriber> list = byUser.get(subscriber.userId());
        if (list != null) {
            list.remove(subscriber);
            if (list.isEmpty()) {
                byUser.remove(subscriber.userId(), list);
            }
        }
    }

    public SseEmitter subscribe(Long userId, Role role) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Subscriber subscriber = new Subscriber(userId, role, emitter);
        byUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(subscriber);
        emitter.onCompletion(() -> remove(subscriber));
        // Close the stream cleanly when it expires; without complete() Spring raises AsyncRequestTimeoutException.
        emitter.onTimeout(() -> {
            remove(subscriber);
            emitter.complete();
        });
        emitter.onError(e -> remove(subscriber));
        send(subscriber, "connected", Map.of("userId", userId));
        return emitter;
    }

    /** Pushes an event to every open tab of one user (after commit). */
    public void toUser(Long userId, String event, Object data) {
        afterCommit(() -> byUser.getOrDefault(userId, List.of()).forEach(s -> send(s, event, data)));
    }

    /** Pushes an event to every connected user with the given role, e.g. all delivery partners (after commit). */
    public void toRole(Role role, String event, Object data) {
        afterCommit(() -> byUser.values().forEach(list -> list.stream()
                .filter(s -> s.role() == role).forEach(s -> send(s, event, data))));
    }

    /** Comment line every 25 s keeps proxies and the browser from closing idle streams. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        byUser.values().forEach(list -> list.forEach(s -> deliver(s, SseEmitter.event().comment("ping"))));
    }

    public int connectedTabs() {
        return byUser.values().stream().mapToInt(List::size).sum();
    }

    private void send(Subscriber s, String event, Object data) {
        deliver(s, SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
    }

    /**
     * Writes to one tab. A failure means the tab was closed or refreshed — normal for a live stream —
     * so the subscriber is simply dropped. We deliberately do not call completeWithError: that would
     * re-dispatch the request as an error and log a stack trace for an everyday disconnect.
     */
    private void deliver(Subscriber s, SseEmitter.SseEventBuilder event) {
        try {
            s.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            remove(s);
            log.debug("Live stream closed for user {}: {}", s.userId(), e.getMessage());
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
