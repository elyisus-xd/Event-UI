package com.eventui.fabric.client.bridge;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ClientEventCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventCache.class);
    private final Map<String, String> uiConfigs;
    private final Map<String, EventDefinition> eventDefinitions;
    private final Map<String, EventProgress> eventProgress;
    private final Map<UUID, CompletableFuture<?>> pendingRequests;

    public ClientEventCache() {
        this.eventDefinitions = new ConcurrentHashMap<>();
        this.eventProgress = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.uiConfigs = new ConcurrentHashMap<>();
    }

    public void cacheUIConfig(String uiId, String uiDataJson) {
        uiConfigs.put(uiId, uiDataJson);
        LOGGER.info("Cached UI config: {}", uiId);
    }

    public String getCachedUIConfig(String uiId) {
        return uiConfigs.get(uiId);
    }

    public void registerPendingRequest(UUID messageId, CompletableFuture<?> future) {
        pendingRequests.put(messageId, future);
    }

    <T> void completePendingRequest(UUID messageId, T data) {
        CompletableFuture<?> future = pendingRequests.remove(messageId);

        if (future != null) {
            ((CompletableFuture<T>) future).complete(data);
        }
    }

    void failPendingRequest(UUID messageId, Throwable error) {
        CompletableFuture<?> future = pendingRequests.remove(messageId);

        if (future != null) {
            future.completeExceptionally(error);
        }
    }

    public void handleEventDataResponse(BridgeMessage message) {
        UUID replyTo = message.getReplyToMessageId();

        if (replyTo == null) {
            LOGGER.warn("EVENT_DATA_RESPONSE without replyTo ID");
            return;
        }

        try {
            String eventId = message.getPayload().get("event_id");
            LOGGER.info("Received event data for: {}", eventId);

        } catch (Exception e) {
            LOGGER.error("Failed to parse EVENT_DATA_RESPONSE", e);
            failPendingRequest(replyTo, e);
        }
    }

    public void handleProgressResponse(BridgeMessage message) {
        UUID replyTo = message.getReplyToMessageId();

        if (replyTo == null) {
            LOGGER.warn("EVENT_PROGRESS_RESPONSE without replyTo ID");
            return;
        }

        try {
            String eventId = message.getPayload().get("event_id");
            LOGGER.info("Received progress data for: {}", eventId);

        } catch (Exception e) {
            LOGGER.error("Failed to parse EVENT_PROGRESS_RESPONSE", e);
            failPendingRequest(replyTo, e);
        }
    }

    public void invalidateEvent(String eventId) {
        eventDefinitions.remove(eventId);
        LOGGER.debug("Invalidated event cache: {}", eventId);
    }

    public void invalidateProgress(String eventId) {
        eventProgress.remove(eventId);
        LOGGER.debug("Invalidated progress cache: {}", eventId);
    }

    public void clear() {
        eventDefinitions.clear();
        eventProgress.clear();
        pendingRequests.values().forEach(future ->
                future.completeExceptionally(new IllegalStateException("Bridge disconnected"))
        );
        pendingRequests.clear();

        LOGGER.info("Cache cleared");
    }

    public EventDefinition getCachedEvent(String eventId) {
        return eventDefinitions.get(eventId);
    }

    public EventProgress getCachedProgress(String eventId) {
        return eventProgress.get(eventId);
    }
}
