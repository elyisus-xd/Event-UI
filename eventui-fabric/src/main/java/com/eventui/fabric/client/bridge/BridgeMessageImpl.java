package com.eventui.fabric.client.bridge;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.bridge.MessageType;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Implementación inmutable de BridgeMessage.
 */
public record BridgeMessageImpl(
        MessageType type,
        Map<String, String> payload,
        UUID playerId,
        long timestamp,
        UUID messageId,
        UUID replyToMessageId
) implements BridgeMessage {

    // Constructor principal con todos los campos
    public BridgeMessageImpl {
        payload = payload != null ? Collections.unmodifiableMap(payload) : Map.of();
    }

    // Constructor conveniente sin IDs de mensaje
    public BridgeMessageImpl(MessageType type, Map<String, String> payload, UUID playerId) {
        this(
                type,
                payload,
                playerId,
                System.currentTimeMillis(),
                UUID.randomUUID(),
                null
        );
    }

    // Constructor para respuestas
    public BridgeMessageImpl(MessageType type, Map<String, String> payload, UUID playerId, UUID replyTo) {
        this(
                type,
                payload,
                playerId,
                System.currentTimeMillis(),
                UUID.randomUUID(),
                replyTo
        );
    }

    @Override
    public MessageType getType() {
        return type;
    }

    @Override
    public Map<String, String> getPayload() {
        return payload;
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public UUID getMessageId() {
        return messageId;
    }

    @Override
    public UUID getReplyToMessageId() {
        return replyToMessageId;
    }
}
