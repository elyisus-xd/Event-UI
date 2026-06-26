package com.eventui.fabric.client.bridge;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.bridge.EventBridge;
import com.eventui.api.bridge.MessageType;
import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventProgress;
import com.eventui.api.ui.UIConfig;
import com.eventui.api.ui.UIElement;
import com.eventui.api.ui.UIElementType;
import com.eventui.core.config.UIConfigImpl;
import com.eventui.fabric.client.keybinds.EventUIKeybinds;
import com.eventui.fabric.client.ui.ConfigurableUIScreen;
import com.eventui.fabric.client.ui.QuestTrackerHUD;
import com.eventui.fabric.client.ui.custom.CustomEventScreen;
import com.eventui.fabric.client.ui.tooltip.RecipeCache;
import com.eventui.fabric.client.viewmodel.EventViewModel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientEventBridge implements EventBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventBridge.class);
    private static ClientEventBridge instance;
    private final Map<MessageType, List<MessageHandler>> handlers;
    private final ClientEventCache cache;
    private final NetworkHandler network;
    private boolean connected;
    private EventViewModel globalViewModel;

    public ClientEventBridge() {
        this.handlers = new ConcurrentHashMap<>();
        this.cache = new ClientEventCache();
        this.network = new NetworkHandler(this);
        this.connected = true;

        registerDefaultHandlers();

        LOGGER.info("ClientEventBridge initialized and connected");
    }



    public static ClientEventBridge getInstance() {
        if (instance == null) {
            instance = new ClientEventBridge();
        }
        return instance;
    }

    public EventViewModel getOrCreateViewModel(UUID playerId) {
        if (globalViewModel == null) {
            globalViewModel = new EventViewModel(playerId);
        }
        return globalViewModel;
    }

    @Override
    public CompletableFuture<Void> sendMessage(BridgeMessage message) {
        LOGGER.debug("Sending message type: {}", message.getType());
        return network.sendMessage(message);
    }

    @Override
    public void registerMessageHandler(MessageType type, MessageHandler handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
        LOGGER.debug("Registered handler for message type: {}", type);
    }

    public void removeHandler(MessageType type, MessageHandler handler) {
        List<MessageHandler> list = handlers.get(type);
        if (list != null) {
            list.remove(handler);
            if (list.isEmpty()) {
                handlers.remove(type);
            }
        }
    }

    public void unregisterMessageHandler(MessageType type) {
        handlers.remove(type);
        LOGGER.debug("Unregistered all handlers for message type: {}", type);
    }

    @Override
    public CompletableFuture<EventDefinition> requestEventData(String eventId) {
        UUID playerId = getLocalPlayerId();

        BridgeMessage request = new BridgeMessageImpl(
                MessageType.REQUEST_EVENT_DATA,
                Map.of("event_id", eventId),
                playerId
        );

        CompletableFuture<EventDefinition> future = new CompletableFuture<>();
        UUID messageId = request.getMessageId();
        cache.registerPendingRequest(messageId, future);

        sendMessage(request).exceptionally(ex -> {
            future.completeExceptionally(ex);
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<EventProgress> requestEventProgress(String eventId, UUID playerId) {
        BridgeMessage request = new BridgeMessageImpl(
                MessageType.REQUEST_EVENT_PROGRESS,
                Map.of(
                        "event_id", eventId,
                        "player_uuid", playerId.toString()
                ),
                playerId
        );

        CompletableFuture<EventProgress> future = new CompletableFuture<>();
        cache.registerPendingRequest(request.getMessageId(), future);

        sendMessage(request).exceptionally(ex -> {
            future.completeExceptionally(ex);
            return null;
        });

        return future;
    }

    public CompletableFuture<UIConfig> requestUIConfig(String screenId) {
        UUID playerId = getLocalPlayerId();

        Map<String, String> payload = Map.of(
                "screen_id", screenId,
                "player_id", playerId.toString()
        );

        BridgeMessage request = new BridgeMessageImpl(
                MessageType.REQUEST_UI_CONFIG_WITH_DATA,
                payload,
                playerId
        );

        CompletableFuture<UIConfig> future = new CompletableFuture<>();
        UUID messageId = request.getMessageId();
        cache.registerPendingRequest(messageId, future);

        sendMessage(request).exceptionally(ex -> {
            future.completeExceptionally(ex);
            return null;
        });

        return future;
    }


    @Override
    public void notifyButtonClick(String buttonId, String eventId, UUID playerId) {
        BridgeMessage message = new BridgeMessageImpl(
                MessageType.UI_BUTTON_CLICKED,
                Map.of(
                        "button_id", buttonId,
                        "event_id", eventId
                ),
                playerId
        );

        sendMessage(message);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public void handleIncomingMessage(BridgeMessage message) {
        List<MessageHandler> handlerList = handlers.get(message.getType());

        if (handlerList != null && !handlerList.isEmpty()) {
            for (MessageHandler handler : handlerList) {
                try {
                    handler.handle(message);
                } catch (Exception e) {
                    LOGGER.error("Error handling message type: {}", message.getType(), e);
                }
            }
        } else {
            LOGGER.warn("No handler registered for message type: {}", message.getType());
        }
    }

    private void registerDefaultHandlers() {
        registerMessageHandler(MessageType.EVENT_DATA_RESPONSE, cache::handleEventDataResponse);
        registerMessageHandler(MessageType.EVENT_PROGRESS_RESPONSE, cache::handleProgressResponse);

        registerMessageHandler(MessageType.PROGRESS_UPDATE, message -> {
            LOGGER.debug("Progress update: {}", message.getPayload());
            cache.invalidateProgress(message.getPayload().get("event_id"));
            QuestTrackerHUD.forceUpdate();
        });

        registerMessageHandler(MessageType.EVENT_STATE_CHANGED, message -> {
            LOGGER.debug("Event state changed: {}", message.getPayload());
            cache.invalidateEvent(message.getPayload().get("event_id"));
            QuestTrackerHUD.forceUpdate();
        });

        registerMessageHandler(MessageType.UI_CONFIG_RESPONSE, message -> {
            LOGGER.debug("Received UI_CONFIG_RESPONSE");
            handleUIConfigResponse(message);
        });

        registerMessageHandler(MessageType.EVENT_RELOAD_NOTIFICATION, message -> {
            String reason = message.getPayload().get("reason");
            String screenId = message.getPayload().get("screenId");
            LOGGER.info("[RELOAD_NOTIF] === EVENT_RELOAD_NOTIFICATION === reason='{}', screenId='{}'", reason, screenId);

            if ("hotreload".equals(reason) && screenId != null) {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    if (client.screen instanceof ConfigurableUIScreen activeScreen
                            && screenId.equals(activeScreen.getCurrentScreenId())) {
                        LOGGER.info("[RELOAD_NOTIF] Hot reloading active screen: {}", screenId);
                        client.setScreen(new CustomEventScreen(screenId, new java.util.Stack<>()));
                    } else {
                        LOGGER.info("[RELOAD_NOTIF] Screen '{}' not currently open, invalidating keybind cache", screenId);
                        EventUIKeybinds.invalidateCache();
                        RecipeCache.clear();
                    }
                });
            } else {
                LOGGER.info("[RELOAD_NOTIF] Config reload (non-hotreload), invalidating keybind cache");
                EventUIKeybinds.invalidateCache();
                RecipeCache.clear();
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    if (client.player != null)
                        client.player.sendSystemMessage(Component.literal("§eEventUI: Configuration reloaded."));
                });
            }
        });


        registerMessageHandler(MessageType.UI_STATE_UPDATE, message -> {
            String variablesJson = message.getPayload().get("variables");
            if (variablesJson == null || variablesJson.isEmpty()) return;

            boolean replace = "true".equals(message.getPayload().get("replace"));
            try {
                Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> variables = new Gson().fromJson(variablesJson, mapType);
                Minecraft.getInstance().execute(() -> {
                    if (replace) UIStateCache.getInstance().replaceAll(variables);
                    else UIStateCache.getInstance().updateVariables(variables);
                    LOGGER.debug("UI state {}: {} variables", replace ? "replaced" : "updated", variables.size());
                });
            } catch (Exception e) {
                LOGGER.error("Failed to parse UI_STATE_UPDATE", e);
            }
        });

        registerMessageHandler(MessageType.OPEN_UI_COMMAND, message -> {
            String screenId = message.getPayload().get("screen_id");
            if (screenId == null || screenId.isEmpty()) {
                LOGGER.warn("OPEN_UI_COMMAND received without screen_id");
                return;
            }
            LOGGER.info("OPEN_UI_COMMAND received — opening screen: {}", screenId);
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (client.player != null) {
                    client.setScreen(new CustomEventScreen(screenId, new java.util.Stack<>()));
                }
            });
        });
    }


    public void requestUIState() {
        UUID playerId = getLocalPlayerId();
        BridgeMessage request = new BridgeMessageImpl(
                MessageType.REQUEST_UI_STATE,
                Map.of(),
                playerId
        );
        sendMessage(request);
    }

    private UUID getLocalPlayerId() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("Local player not available");
        }
        return player.getUUID();
    }

    public void onConnect() {
        this.connected = true;
        LOGGER.info("ClientEventBridge connected");
    }


    public void onDisconnect() {
        this.connected = false;
        cache.clear();
        LOGGER.info("ClientEventBridge disconnected");
    }

    public ClientEventCache getCache() {
        return cache;
    }

    public CompletableFuture<Map<String, String>> requestUIMode() {
        CompletableFuture<Map<String, String>> future = new CompletableFuture<>();

        UUID messageId = UUID.randomUUID();
        UUID playerId = getLocalPlayerId();

        MessageHandler[] handlerRef = new MessageHandler[1];
        handlerRef[0] = message -> {
            if (message.getReplyToMessageId() != null &&
                    message.getReplyToMessageId().equals(messageId)) {
                future.complete(message.getPayload());
                removeHandler(MessageType.UI_MODE_RESPONSE, handlerRef[0]);
            }
        };

        registerMessageHandler(MessageType.UI_MODE_RESPONSE, handlerRef[0]);
        Map<String, String> payload = Map.of();
        BridgeMessage request = new BridgeMessageImpl(
                MessageType.REQUEST_UI_MODE,
                payload,
                playerId,
                System.currentTimeMillis(),
                messageId,
                null
        );

        sendMessage(request);

        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("UI mode request timed out"));
                removeHandler(MessageType.UI_MODE_RESPONSE, handlerRef[0]);
            }
        });

        return future;
    }

    private void handleUIConfigResponse(BridgeMessage message) {
        UUID replyTo = message.getReplyToMessageId();
        if (replyTo == null) {
            LOGGER.warn("UI_CONFIG_RESPONSE without replyTo ID");
            return;
        }

        try {
            String uiConfigJson = message.getPayload().get("ui_config");
            if (uiConfigJson == null || uiConfigJson.isEmpty()) {
                LOGGER.error("Empty ui_config in response");
                cache.failPendingRequest(replyTo, new IllegalStateException("Empty UI config"));
                return;
            }

            UIConfig uiConfig = deserializeUIConfig(uiConfigJson);
            LOGGER.info("✓ UIConfig parsed: {} with {} elements",
                    uiConfig.getId(), uiConfig.getRootElements().size());
            cache.completePendingRequest(replyTo, uiConfig);

        } catch (Exception e) {
            LOGGER.error("════════════════════════════════════════");
            LOGGER.error("  Failed to parse UI_CONFIG_RESPONSE");
            LOGGER.error("════════════════════════════════════════");
            LOGGER.error("  Cause : {}", e.getMessage());
            Throwable cause = e.getCause();
            int depth = 1;
            while (cause != null && depth < 5) {
                LOGGER.error("  Cause[{}]: {}", depth, cause.getMessage());
                cause = cause.getCause();
                depth++;
            }
            LOGGER.error("  Tip: Check your UI YAML for:");
            LOGGER.error("    - Indentation errors (mixed tabs/spaces)");
            LOGGER.error("    - Comments at wrong indentation level");
            LOGGER.error("    - Missing 'properties:' block");
            LOGGER.error("    - Unknown element type");
            LOGGER.error("════════════════════════════════════════");
            cache.failPendingRequest(replyTo, e);
        }
    }
    @SuppressWarnings("unchecked")
    private UIConfig deserializeUIConfig(String json) {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(json, mapType);

        String id = (String) data.get("id");
        String title = (String) data.getOrDefault("title", "Custom UI");

        int screenWidth = data.containsKey("screen_width")
                ? ((Number) data.get("screen_width")).intValue()
                : ((Number) data.getOrDefault("screenWidth", 320)).intValue();

        int screenHeight = data.containsKey("screen_height")
                ? ((Number) data.get("screen_height")).intValue()
                : ((Number) data.getOrDefault("screenHeight", 240)).intValue();


        Map<String, String> screenProperties = new HashMap<>();
        Object propsObj = data.get("screen_properties");
        if (propsObj instanceof Map) {
            ((Map<?, ?>) propsObj).forEach((k, v) ->
                    screenProperties.put(k.toString(), v.toString())
            );
        }

        List<UIElement> elements = new ArrayList<>();
        Object elementsObj = data.get("elements");
        if (elementsObj instanceof List) {
            for (Object elementObj : (List<?>) elementsObj) {
                if (elementObj instanceof Map) {
                    elements.add(parseUIElement((Map<String, Object>) elementObj));
                }
            }
        }

        String associatedEventId = (String) data.get("associated_event_id");

        return new UIConfigImpl(id, title, screenWidth, screenHeight,
                elements, associatedEventId, screenProperties);
    }

    private UIElement parseUIElement(Map<String, Object> data) {
        String id = (String) data.get("id");
        String typeStr = (String) data.get("type");
        UIElementType type = UIElementType.valueOf(typeStr);

        int x = ((Number) data.get("x")).intValue();
        int y = ((Number) data.get("y")).intValue();
        int width = ((Number) data.getOrDefault("width", 100)).intValue();
        int height = ((Number) data.getOrDefault("height", 20)).intValue();
        int zIndex = ((Number) data.getOrDefault("zIndex", 0)).intValue();
        boolean visible = (Boolean) data.getOrDefault("visible", true);

        Map<String, String> properties = new HashMap<>();
        Object propsObj = data.get("properties");
        if (propsObj instanceof Map) {
            ((Map<?, ?>) propsObj).forEach((k, v) ->
                    properties.put(k.toString(), v.toString())
            );
        }

        List<UIElement> children = new ArrayList<>();
        Object childrenObj = data.get("children");
        if (childrenObj instanceof List) {
            for (Object childObj : (List<?>) childrenObj) {
                if (childObj instanceof Map) {
                    children.add(parseUIElement((Map<String, Object>) childObj));
                }
            }
        }

        return new com.eventui.core.config.UIElementImpl(
                id, type, x, y, width, height,
                properties, children, visible, zIndex
        );
    }

}
