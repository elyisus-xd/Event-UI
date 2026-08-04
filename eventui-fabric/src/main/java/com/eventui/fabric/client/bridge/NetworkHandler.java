package com.eventui.fabric.client.bridge;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.bridge.MessageType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkHandler.class);

    public static final ResourceLocation CHANNEL_ID =
            ResourceLocation.fromNamespaceAndPath("eventui", "bridge");

    private final ClientEventBridge bridge;

    public NetworkHandler(ClientEventBridge bridge) {
        this.bridge = bridge;
        registerReceiver();
    }

    private void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                EventUIPayload.ID,
                (payload, context) -> {
                    byte[] data = payload.data();

                    context.client().execute(() -> {
                        try {
                            BridgeMessage message = deserializeMessage(data);
                            bridge.handleIncomingMessage(message);
                        } catch (Exception e) {
                            LOGGER.error("Failed to process incoming message", e);
                        }
                    });
                }
        );
    }

    public CompletableFuture<Void> sendMessage(BridgeMessage message) {
        try {
            byte[] data = serializeMessage(message);

            EventUIPayload payload = new EventUIPayload(data);
            ClientPlayNetworking.send(payload);

            LOGGER.debug("Sent message type: {} ({} bytes)", message.getType(), data.length);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            LOGGER.error("Failed to send message", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private byte[] serializeMessage(BridgeMessage message) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(byteOut)) {
            out.writeByte(message.getType().ordinal());
            Map<String, String> payload = message.getPayload();
            out.writeInt(payload.size());
            for (Map.Entry<String, String> entry : payload.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }

            boolean hasPlayerId = message.getPlayerId() != null;
            out.writeBoolean(hasPlayerId);
            if (hasPlayerId) {
                out.writeLong(message.getPlayerId().getMostSignificantBits());
                out.writeLong(message.getPlayerId().getLeastSignificantBits());
            }

            out.writeLong(message.getTimestamp());
            boolean hasMessageId = message.getMessageId() != null;
            out.writeBoolean(hasMessageId);
            if (hasMessageId) {
                out.writeLong(message.getMessageId().getMostSignificantBits());
                out.writeLong(message.getMessageId().getLeastSignificantBits());
            }

            boolean hasReplyTo = message.getReplyToMessageId() != null;
            out.writeBoolean(hasReplyTo);
            if (hasReplyTo) {
                out.writeLong(message.getReplyToMessageId().getMostSignificantBits());
                out.writeLong(message.getReplyToMessageId().getLeastSignificantBits());
            }

            out.flush();
            return byteOut.toByteArray();
        }
    }

    private BridgeMessage deserializeMessage(byte[] data) throws IOException {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(data);

        try (DataInputStream in = new DataInputStream(byteIn)) {
            MessageType type = MessageType.values()[in.readByte()];
            int payloadSize = in.readInt();
            Map<String, String> payload = new HashMap<>();
            for (int i = 0; i < payloadSize; i++) {
                String key = in.readUTF();
                String value = in.readUTF();
                payload.put(key, value);
            }

            UUID playerId = null;
            if (in.readBoolean()) {
                long mostSigBits = in.readLong();
                long leastSigBits = in.readLong();
                playerId = new UUID(mostSigBits, leastSigBits);
            }

            long timestamp = in.readLong();

            UUID messageId = null;
            if (in.readBoolean()) {
                long mostSigBits = in.readLong();
                long leastSigBits = in.readLong();
                messageId = new UUID(mostSigBits, leastSigBits);
            }

            UUID replyToMessageId = null;
            if (in.readBoolean()) {
                long mostSigBits = in.readLong();
                long leastSigBits = in.readLong();
                replyToMessageId = new UUID(mostSigBits, leastSigBits);
            }

            return new BridgeMessageImpl(
                    type,
                    payload,
                    playerId,
                    timestamp,
                    messageId,
                    replyToMessageId
            );
        }
    }

    public record EventUIPayload(byte[] data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<EventUIPayload> ID =
                new CustomPacketPayload.Type<>(CHANNEL_ID);

        public static final StreamCodec<FriendlyByteBuf, EventUIPayload> CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeBytes(payload.data),
                        buf -> {
                            byte[] data = new byte[buf.readableBytes()];
                            buf.readBytes(data);
                            return new EventUIPayload(data);
                        }
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void registerPayloadType() {
        PayloadTypeRegistry.playS2C().register(EventUIPayload.ID, EventUIPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EventUIPayload.ID, EventUIPayload.CODEC);
    }
}
