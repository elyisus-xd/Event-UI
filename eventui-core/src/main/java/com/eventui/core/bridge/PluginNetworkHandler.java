package com.eventui.core.bridge;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.bridge.MessageType;
import com.eventui.core.EventUIPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class PluginNetworkHandler implements PluginMessageListener {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final String CHANNEL = "eventui:bridge";

    private final PluginEventBridge bridge;
    private final EventUIPlugin plugin;

    public PluginNetworkHandler(PluginEventBridge bridge, EventUIPlugin plugin) {
        this.bridge = bridge;
        this.plugin = plugin;

        registerChannel();
    }

        private void registerChannel() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] messageData) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        try {
            BridgeMessage bridgeMessage = deserializeMessage(messageData);
            BridgeMessage messageWithPlayer = new PluginBridgeMessage(
                    bridgeMessage.getType(),
                    bridgeMessage.getPayload(),
                    player.getUniqueId(),
                    bridgeMessage.getTimestamp(),
                    bridgeMessage.getMessageId(),
                    bridgeMessage.getReplyToMessageId()
            );

            bridge.handleIncomingMessage(messageWithPlayer, player);

        } catch (Exception e) {
            LOGGER.severe("Failed to process plugin message from " + player.getName());
            e.printStackTrace();
        }
    }

        public CompletableFuture<Void> sendMessage(BridgeMessage message) {
        try {
            Player player = plugin.getServer().getPlayer(message.getPlayerId());

            if (player == null || !player.isOnline()) {
                LOGGER.warning("Cannot send message, player offline: " + message.getPlayerId());
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Player offline")
                );
            }

            byte[] data = serializeMessage(message);

            player.sendPluginMessage(plugin, CHANNEL, data);

            LOGGER.fine("Sent message type " + message.getType() + " to " + player.getName() +
                    " (" + data.length + " bytes)");

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            LOGGER.severe("Failed to send plugin message");
            e.printStackTrace();
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

            return new PluginBridgeMessage(
                    type,
                    payload,
                    playerId,
                    timestamp,
                    messageId,
                    replyToMessageId
            );
        }
    }

        public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);

        LOGGER.info("Unregistered plugin messaging channel");
    }
}
