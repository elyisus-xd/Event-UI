package com.eventui.core.bridge;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.bridge.EventBridge;
import com.eventui.api.bridge.MessageType;
import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventProgress;
import com.eventui.api.event.EventState;
import com.eventui.api.objective.ObjectiveDefinition;
import com.eventui.api.objective.ObjectiveProgress;
import com.eventui.api.ui.UIConfig;
import com.eventui.core.EventUIPlugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PluginEventBridge implements EventBridge {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final EventUIPlugin plugin;
    private final Map<MessageType, MessageHandler> handlers;
    private final PluginNetworkHandler network;

    public PluginEventBridge(EventUIPlugin plugin) {
        this.plugin = plugin;
        this.handlers = new ConcurrentHashMap<>();
        this.network = new PluginNetworkHandler(this, plugin);

        registerDefaultHandlers();
    }

    @Override
    public CompletableFuture<Void> sendMessage(BridgeMessage message) {
        return network.sendMessage(message);
    }

    @Override
    public void registerMessageHandler(MessageType type, MessageHandler handler) {
        handlers.put(type, handler);
        LOGGER.fine("Registered handler for message type: " + type);
    }

    @Override
    public CompletableFuture<EventDefinition> requestEventData(String eventId) {
        return CompletableFuture.completedFuture(
                plugin.getStorage().getEventDefinition(eventId).orElse(null)
        );
    }

    @Override
    public CompletableFuture<EventProgress> requestEventProgress(String eventId, UUID playerId) {
        return CompletableFuture.completedFuture(
                plugin.getStorage().getProgress(playerId, eventId).orElse(null)
        );
    }

    @Override
    public CompletableFuture<UIConfig> requestUIConfig(String eventId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void notifyButtonClick(String buttonId, String eventId, UUID playerId) {
        LOGGER.fine("Button clicked: " + buttonId + " on event " + eventId + " by " + playerId);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    public void handleIncomingMessage(BridgeMessage message, Player player) {
        MessageHandler handler = handlers.get(message.getType());

        if (handler != null) {
            try {
                handler.handle(message);
            } catch (Exception e) {
                LOGGER.severe("Error handling message type: " + message.getType());
                e.printStackTrace();
                sendErrorResponse(message, e.getMessage(), player);
            }
        } else {
            LOGGER.warning("No handler for message type: " + message.getType());
            sendErrorResponse(message, "Unknown message type", player);
        }
    }

    private void registerDefaultHandlers() {
        registerMessageHandler(MessageType.REQUEST_EVENT_DATA, message -> {
            UUID playerId = message.getPlayerId();
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                LOGGER.warning("Player not found for REQUEST_EVENT_DATA: " + playerId);
                return;
            }
            handleRequestEventData(player, message);
        });
        registerMessageHandler(MessageType.REQUEST_UI_STATE, message -> {
            UUID playerId = message.getPlayerId();
            plugin.getUIStateManager().pushStateToClient(playerId, true);

            // Also push dismissed badges for this player
            java.util.List<String> list = new java.util.ArrayList<>(plugin.getPlayerDataManager().getDismissedBadges(playerId));
            String badgesJson = new com.google.gson.Gson().toJson(list);
            Map<String, String> payload = Map.of("badges", badgesJson);
            BridgeMessage response = new PluginBridgeMessage(MessageType.BADGES_UPDATE, payload, playerId);
            sendMessage(response);
        });
        registerMessageHandler(MessageType.REQUEST_EVENT_PROGRESS, message -> {
            String eventId = message.getPayload().get("event_id");
            UUID playerId = message.getPlayerId();

            plugin.getStorage().getProgress(playerId, eventId).ifPresentOrElse(
                    progress -> {
                        Map<String, String> payload = Map.of(
                                "event_id", eventId,
                                "state", progress.getState().name(),
                                "overall_progress", String.valueOf(progress.getOverallProgress()),
                                "started_at", String.valueOf(progress.getStartedAt())
                        );

                        BridgeMessage response = new PluginBridgeMessage(
                                MessageType.EVENT_PROGRESS_RESPONSE,
                                payload,
                                playerId,
                                message.getMessageId()
                        );

                        sendMessage(response);
                    },
                    () -> LOGGER.warning("Progress not found for event: " + eventId)
            );
        });

        registerMessageHandler(MessageType.UI_BUTTON_CLICKED, message -> {
            String buttonId = message.getPayload().get("button_id");
            String screenId = message.getPayload().get("screen_id");
            String eventId = message.getPayload().get("event_id");
            String actionFromPayload = message.getPayload().get("action");
            UUID playerId = message.getPlayerId();

            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                LOGGER.warning("Player not found for button click: " + playerId);
                return;
            }

            if (screenId == null || screenId.isEmpty()) {
                screenId = eventId;
            }

            LOGGER.fine("Processing button click: button=" + buttonId + ", screen=" + screenId +
                    ", player=" + player.getName());

            String action = null;

            if (actionFromPayload != null && !actionFromPayload.isEmpty()) {
                action = actionFromPayload;
                LOGGER.fine("Using action from payload: " + action);

            } else {
                UIConfig uiConfig = plugin.getUIConfigs().get(screenId);

                if (uiConfig == null) {
                    LOGGER.warning("UI config not found: " + screenId);
                    player.sendMessage("§cFailed to execute action");
                    return;
                }

                com.eventui.api.ui.UIElement button = findElementById(uiConfig.getRootElements(), buttonId);

                if (button == null) {
                    LOGGER.warning("Button not found: " + buttonId);
                    player.sendMessage("§cButton not found");
                    return;
                }

                action = button.getProperties().get("action");
            }

            if (action == null || action.isEmpty()) {
                LOGGER.warning("No action found for button: " + buttonId);
                player.sendMessage("§cNo action defined");
                return;
            }

            LOGGER.fine("Executing action: " + action);
            handleButtonAction(player, action, buttonId);
        });

        // Handle badge dismissals from client
        registerMessageHandler(MessageType.BADGE_DISMISS, message -> {
            UUID playerId = message.getPlayerId();
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) return;

            String screenId = message.getPayload().get("screen_id");
            String elementId = message.getPayload().get("element_id");
            if (elementId == null || elementId.isEmpty()) return;

            String badgeKey = (screenId == null || screenId.isEmpty()) ? ("unknown:" + elementId) : (screenId + ":" + elementId);
            plugin.getPlayerDataManager().addDismissedBadge(playerId, badgeKey);

            // Send updated badges list back to player
            java.util.List<String> list = new java.util.ArrayList<>(plugin.getPlayerDataManager().getDismissedBadges(playerId));
            String badgesJson = new com.google.gson.Gson().toJson(list);
            Map<String, String> payload = Map.of("badges", badgesJson);
            BridgeMessage response = new PluginBridgeMessage(MessageType.BADGES_UPDATE, payload, playerId);
            sendMessage(response);
        });

        registerMessageHandler(MessageType.REQUEST_UI_CONFIG, message -> {
            UUID playerId = message.getPlayerId();
            Player player = plugin.getServer().getPlayer(playerId);

            if (player == null) {
                LOGGER.warning("Player not found for REQUEST_UI_CONFIG: " + playerId);
                return;
            }

            handleRequestUIConfig(player, message);
        });
        registerMessageHandler(MessageType.REQUEST_UI_MODE, message -> {
            UUID playerId = message.getPlayerId();
            Player player = plugin.getServer().getPlayer(playerId);

            if (player == null) {
                LOGGER.warning("Player not found for REQUEST_UI_MODE: " + playerId);
                return;
            }

            Map<String, String> uiModeData = plugin.getUIConfigManager().getUIModeResponse();
            BridgeMessage response = new PluginBridgeMessage(
                    MessageType.UI_MODE_RESPONSE,
                    uiModeData,
                    playerId,
                    message.getMessageId()
            );

            sendMessage(response);

            boolean debugLog = plugin.getConfig().getBoolean("debug.logUIConfig", false);
            if (debugLog) {
                LOGGER.info("Sent UI mode to " + player.getName() + ": " + uiModeData.get("mode"));
            }
        });

        registerMessageHandler(MessageType.REQUEST_UI_CONFIG_WITH_DATA, message -> {
            UUID playerId = message.getPlayerId();
            Player player = plugin.getServer().getPlayer(playerId);

            if (player == null) {
                LOGGER.warning("Player not found for REQUEST_UI_CONFIG_WITH_DATA: " + playerId);
                return;
            }

            String screenId = message.getPayload().get("screen_id");

            LOGGER.info("Player " + player.getName() + " requested UI config: " + screenId);

            handleUIConfigRequest(message, player, screenId);
        });

        registerMessageHandler(MessageType.REQUEST_SKILL_DATA, message -> {
            UUID playerId = message.getPlayerId();
            Player player = plugin.getServer().getPlayer(playerId);

            if (player == null) {
                LOGGER.warning("Player not found for REQUEST_SKILL_DATA: " + playerId);
                return;
            }

            handleRequestSkillData(player, message);
        });

        registerMessageHandler(MessageType.REQUEST_SKILL_SPEND, message -> {
            UUID playerId = message.getPlayerId();
            Player player = plugin.getServer().getPlayer(playerId);

            if (player == null) {
                LOGGER.warning("Player not found for REQUEST_SKILL_SPEND: " + playerId);
                return;
            }

            handleRequestSkillSpend(player, message);
        });

    }

        private com.eventui.api.ui.UIElement findElementById(
            List<com.eventui.api.ui.UIElement> elements,
            String id
    ) {
        for (com.eventui.api.ui.UIElement element : elements) {
            if (element.getId().equals(id)) {
                return element;
            }
            com.eventui.api.ui.UIElement found = findElementById(element.getChildren(), id);
            if (found != null) {
                return found;
            }
        }

        return null;
    }


        private void handleRequestEventData(Player player, BridgeMessage message) {
        UUID playerId = player.getUniqueId();

        List<EventDefinition> events = plugin.getStorage().getAllEventDefinitions()
                .values()
                .stream()
                .toList();

        List<Map<String, Object>> eventsList = new ArrayList<>();

        for (EventDefinition eventDef : events) {
            Map<String, Object> eventData = new HashMap<>();

            eventData.put("id", eventDef.getId());
            eventData.put("displayName", eventDef.getDisplayName());
            eventData.put("description", eventDef.getDescription());
            String icon = eventDef.getMetadata().getOrDefault("icon", "minecraft:paper");
            eventData.put("icon", icon);

            String category = eventDef.getMetadata().getOrDefault("category", "general");
            eventData.put("category", category);

            String difficulty = eventDef.getMetadata().getOrDefault("difficulty", "medium");
            eventData.put("difficulty", difficulty);

            String rewardsJson = eventDef.getMetadata().getOrDefault("rewards_data", "{}");
            eventData.put("rewards", rewardsJson);

            String repeatableStr = eventDef.getMetadata().getOrDefault("repeatable", "false");
            boolean repeatable = Boolean.parseBoolean(repeatableStr);
            eventData.put("repeatable", repeatable);

            List<String> dependencies = new ArrayList<>();
            String depsJson = eventDef.getMetadata().get("dependencies");
            if (depsJson != null && !depsJson.isEmpty()) {
                try {
                    dependencies = new com.google.gson.Gson().fromJson(
                            depsJson,
                            new com.google.gson.reflect.TypeToken<List<String>>(){}.getType()
                    );
                } catch (Exception e) {
                    LOGGER.warning("Failed to parse dependencies for event " + eventDef.getId());
                }
            }

            eventData.put("dependencies", new com.google.gson.Gson().toJson(dependencies));

            boolean isLocked = false;
            if (!dependencies.isEmpty()) {
                for (String depId : dependencies) {
                    var depProgress = plugin.getStorage().getProgress(playerId, depId);
                    if (depProgress.isEmpty() || depProgress.get().getState() != EventState.COMPLETED) {
                        isLocked = true;
                        break;
                    }
                }
            }


            eventData.put("isLocked", isLocked);

            LOGGER.fine("Event '" + eventDef.getId() + "' - locked: " + isLocked + ", deps: " + dependencies.size());


            var progressOpt = plugin.getStorage().getProgress(playerId, eventDef.getId());

            if (progressOpt.isPresent()) {
                EventProgress progress = progressOpt.get();

                eventData.put("state", progress.getState().name());
                eventData.put("overallProgress", String.valueOf(progress.getOverallProgress()));
                eventData.put("startedAt", String.valueOf(progress.getStartedAt()));


                if (progress.getState() == EventState.IN_PROGRESS) {
                    for (ObjectiveDefinition objDef : eventDef.getObjectives()) {
                        ObjectiveProgress objProgress = progress.getObjectivesProgress().stream()
                                .filter(op -> op.getObjectiveId().equals(objDef.getId()))
                                .findFirst()
                                .orElse(null);

                        if (objProgress != null && !objProgress.isCompleted()) {
                            eventData.put("currentObjective", objDef.getDescription());
                            eventData.put("currentProgress", objProgress.getCurrentAmount());
                            eventData.put("targetProgress", objProgress.getTargetAmount());
                            break;
                        }
                    }
                }
            } else {
                eventData.put("state", EventState.AVAILABLE.name());
                eventData.put("overallProgress", "0.0");
                eventData.put("startedAt", "0");
                if (!eventDef.getObjectives().isEmpty()) {
                    ObjectiveDefinition firstObjective = eventDef.getObjectives().get(0);
                    eventData.put("currentObjective", firstObjective.getDescription());
                    eventData.put("currentProgress", 0);
                    eventData.put("targetProgress", firstObjective.getTargetAmount());

                    LOGGER.info("Sending AVAILABLE event '" + eventDef.getId() + "' with objective: " +
                            firstObjective.getDescription() + " (0/" + firstObjective.getTargetAmount() + ")");
                }

            }

            eventsList.add(eventData);
        }

        try {
            String jsonData = new com.google.gson.Gson().toJson(eventsList);

            Map<String, String> payload = Map.of(
                    "events", jsonData,
                    "count", String.valueOf(eventsList.size())
            );

            BridgeMessage response = new PluginBridgeMessage(
                    MessageType.EVENT_DATA_RESPONSE,
                    payload,
                    playerId,
                    message.getMessageId()
            );

            sendMessage(response);

            LOGGER.fine("Sent " + eventsList.size() + " events (with repeatable flags) to player " + player.getName());

        } catch (Exception e) {
            LOGGER.severe("Failed to serialize events: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(message, "Failed to load events", player);
        }
    }

    private void sendErrorResponse(BridgeMessage originalMessage, String errorMessage, Player player) {
        Map<String, String> payload = Map.of(
                "error_code", "PROCESSING_ERROR",
                "message", errorMessage
        );

        BridgeMessage response = new PluginBridgeMessage(
                MessageType.ERROR,
                payload,
                player.getUniqueId(),
                originalMessage.getMessageId()
        );

        sendMessage(response);
    }

        public void notifyProgressUpdate(UUID playerId, String eventId, String objectiveId, int current, int target, String description) {
        Map<String, String> payload = Map.of(
                "event_id", eventId,
                "objective_id", objectiveId,
                "current", String.valueOf(current),
                "target", String.valueOf(target),
                "description", description
        );

        BridgeMessage message = new PluginBridgeMessageMinimal(
                MessageType.PROGRESS_UPDATE,
                payload,
                playerId
        );

        sendMessage(message);
    }

    public PluginNetworkHandler getNetworkHandler() {
        return network;
    }

        private static class PluginBridgeMessageMinimal implements BridgeMessage {
        private final MessageType type;
        private final Map<String, String> payload;
        private final UUID playerId;

        public PluginBridgeMessageMinimal(MessageType type, Map<String, String> payload, UUID playerId) {
            this.type = type;
            this.payload = Collections.unmodifiableMap(payload);
            this.playerId = playerId;
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
            return 0;
        }

        @Override
        public UUID getMessageId() {
            return null;
        }

        @Override
        public UUID getReplyToMessageId() {
            return null;
        }
    }

        private void handleRequestUIConfig(Player player, BridgeMessage message) {
        String uiId = message.getPayload().get("ui_id");

        if (uiId == null || uiId.isEmpty()) {
            uiId = "default_event_list";
        }

        UIConfig uiConfig = plugin.getUIConfigs().get(uiId);

        if (uiConfig == null) {
            LOGGER.warning("UI config not found: " + uiId);
            sendErrorResponse(message, "UI not found: " + uiId, player);
            return;
        }

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();

            Map<String, Object> uiData = new HashMap<>();
            uiData.put("id", uiConfig.getId());
            uiData.put("title", uiConfig.getTitle());
            uiData.put("screen_width", uiConfig.getScreenWidth());
            uiData.put("screen_height", uiConfig.getScreenHeight());
            uiData.put("associated_event_id", uiConfig.getAssociatedEventId());
            uiData.put("screen_properties", uiConfig.getScreenProperties());
            List<Map<String, Object>> elementsData = new ArrayList<>();
            for (com.eventui.api.ui.UIElement element : uiConfig.getRootElements()) {
                elementsData.add(serializeUIElement(element));
            }
            uiData.put("elements", elementsData);

            String jsonData = gson.toJson(uiData);

            Map<String, String> payload = Map.of(
                    "ui_id", uiConfig.getId(),
                    "ui_data", jsonData
            );

            BridgeMessage response = new PluginBridgeMessage(
                    MessageType.UI_CONFIG_RESPONSE,
                    payload,
                    player.getUniqueId(),
                    message.getMessageId()
            );

            sendMessage(response);

            LOGGER.fine("Sent UI config: " + uiId + " to player " + player.getName());

        } catch (Exception e) {
            LOGGER.severe("Failed to serialize UI config: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(message, "Failed to load UI", player);
        }
    }

        private Map<String, Object> serializeUIElement(com.eventui.api.ui.UIElement element) {
        Map<String, Object> data = new HashMap<>();

        data.put("id", element.getId());
        data.put("type", element.getType().name());
        data.put("x", element.getX());
        data.put("y", element.getY());
        data.put("width", element.getWidth());
        data.put("height", element.getHeight());
        data.put("properties", element.getProperties());
        data.put("visible", element.isVisible());
        data.put("z_index", element.getZIndex());

        List<Map<String, Object>> childrenData = new ArrayList<>();
        for (com.eventui.api.ui.UIElement child : element.getChildren()) {
            childrenData.add(serializeUIElement(child));
        }
        data.put("children", childrenData);

        return data;
    }
        private void handleButtonAction(Player player, String action, String buttonId) {
        try {
            LOGGER.fine("Handling action: " + action + " from button: " + buttonId);

            if (action.startsWith("start_event:")) {
                String eventId = action.substring(12);
                handleStartEvent(player, eventId);

            } else if (action.startsWith("abandon_event:")) {
                String eventId = action.substring(14);
                handleAbandonEvent(player, eventId);

            } else if (action.startsWith("view_progress:")) {
                String eventId = action.substring(14);
                handleViewProgress(player, eventId);

            } else if ("close".equals(action) || "back".equals(action)) {
                LOGGER.info("Client-side action: " + action);

            } else {
                LOGGER.warning("Unknown button action: " + action);
                player.sendMessage("§cUnknown action: " + action);
            }

        } catch (Exception e) {
            LOGGER.severe("Failed to handle button action: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cFailed to execute action");
        }
    }
        private void handleStartEvent(Player player, String eventId) {
        LOGGER.info("Starting event: " + eventId + " for player: " + player.getName());
        var eventOpt = plugin.getStorage().getEventDefinition(eventId);
        if (eventOpt.isEmpty()) {
            player.sendMessage("§cEvent not found: " + eventId);
            return;
        }

        var eventDef = eventOpt.get();

        String depsJson = eventDef.getMetadata().get("dependencies");
        if (depsJson != null && !depsJson.isEmpty()) {
            try {
                List<String> dependencies = new com.google.gson.Gson().fromJson(
                        depsJson,
                        new com.google.gson.reflect.TypeToken<List<String>>(){}.getType()
                );

                List<String> missingDeps = new ArrayList<>();
                for (String depId : dependencies) {
                    var depProgress = plugin.getStorage().getProgress(player.getUniqueId(), depId);
                    if (depProgress.isEmpty() || depProgress.get().getState() != EventState.COMPLETED) {
                        plugin.getStorage().getEventDefinition(depId).ifPresent(dep -> {
                            missingDeps.add(dep.getDisplayName());
                        });
                    }
                }

                if (!missingDeps.isEmpty()) {
                    player.sendMessage("§c🔒 This event is locked!");
                    player.sendMessage("§7You must complete these first:");
                    for (String depName : missingDeps) {
                        player.sendMessage("  §e• " + depName);
                    }
                    return;
                }

            } catch (Exception e) {
                LOGGER.warning("Failed to check dependencies for " + eventId);
            }
        }

        String repeatableStr = eventDef.getMetadata().getOrDefault("repeatable", "false");
        boolean repeatable = Boolean.parseBoolean(repeatableStr);

        var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);

        if (progressOpt.isPresent()) {
            var progress = progressOpt.get();

            if (progress.getState() == EventState.COMPLETED) {
                if (!repeatable) {
                    player.sendMessage("§cYou have already completed this event!");
                    return;
                }

                plugin.getStorage().removeProgress(player.getUniqueId(), eventId);
                player.sendMessage("§eRestarting event...");
                notifyStateChange(player.getUniqueId(), eventId, EventState.AVAILABLE);

            } else if (progress.getState() == EventState.IN_PROGRESS) {
                player.sendMessage("§eThis event is already in progress!");
                return;
            }
        }

        var progress = plugin.getStorage().getOrCreateProgress(player.getUniqueId(), eventId);
        progress.start();

        plugin.getObjectiveTracker().registerActiveEvent(player.getUniqueId(), eventId);

        player.sendMessage("§a✓ Started event: " + eventDef.getDisplayName());


        notifyStateChange(player.getUniqueId(), eventId, EventState.IN_PROGRESS);

        if (!eventDef.getObjectives().isEmpty()) {
            var firstObjective = eventDef.getObjectives().get(0);

            this.notifyProgressUpdate(
                    player.getUniqueId(),
                    eventId,
                    firstObjective.getId(),
                    0,
                    firstObjective.getTargetAmount(),
                    firstObjective.getDescription()
            );
        }

        LOGGER.info("✓ Event started successfully: " + eventId);
    }

        private void handleAbandonEvent(Player player, String eventId) {
        var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);
        if (progressOpt.isEmpty()) {
            player.sendMessage("§7You haven't started this event.");
            return;
        }

        var progress = progressOpt.get();

        if (progress.getState() != EventState.IN_PROGRESS) {
            player.sendMessage("§cThis event is not in progress.");
            return;
        }

        var eventOpt = plugin.getStorage().getEventDefinition(eventId);
        if (eventOpt.isEmpty()) {
            player.sendMessage("§cEvent not found: " + eventId);
            return;
        }

        var eventDef = eventOpt.get();
        String repeatableStr = eventDef.getMetadata().getOrDefault("repeatable", "false");
        boolean repeatable = Boolean.parseBoolean(repeatableStr);

        if (repeatable) {
            plugin.getStorage().removeProgress(player.getUniqueId(), eventId);
            plugin.getObjectiveTracker().unregisterActiveEvent(player.getUniqueId(), eventId);
            player.sendMessage("§eEvent abandoned. You can start it again.");
            notifyStateChange(player.getUniqueId(), eventId, EventState.AVAILABLE);
        } else {
            progress.fail();
            plugin.getObjectiveTracker().unregisterActiveEvent(player.getUniqueId(), eventId);
            player.sendMessage("§cEvent failed. It cannot be restarted.");
            notifyStateChange(player.getUniqueId(), eventId, EventState.FAILED);
        }
    }

        private void handleViewProgress(Player player, String eventId) {
        var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);
        if (progressOpt.isEmpty()) {
            player.sendMessage("§7You haven't started this event yet.");
            return;
        }

        var progress = progressOpt.get();
        player.sendMessage("§6=== Progress ===");
        player.sendMessage("§eState: §f" + progress.getState());
        player.sendMessage("§eProgress: §f" + String.format("%.1f%%", progress.getOverallProgress() * 100));
    }

        public void notifyStateChange(UUID playerId, String eventId, EventState newState) {
        Map<String, String> payload = Map.of(
                "event_id", eventId,
                "new_state", newState.name()
        );

        BridgeMessage message = new PluginBridgeMessage(
                MessageType.EVENT_STATE_CHANGED,
                payload,
                playerId
        );

        sendMessage(message);
        plugin.getPlayerDataManager().requestSave(playerId, "event state changed: " + eventId + " -> " + newState);

        LOGGER.info("✓ Notified state change to client: event=" + eventId + ", state=" + newState);
    }
        private void handleUIConfigRequest(BridgeMessage request, Player player, String screenId) {
        try {
            UIConfig uiConfig = plugin.getUIConfigs().get(screenId);
            if (uiConfig == null) {
                sendErrorResponse(request, "UI not found: " + screenId, player);
                return;
            }

            com.google.gson.Gson gson = new com.google.gson.Gson();
            Map<String, Object> uiData = new HashMap<>();

            uiData.put("id", uiConfig.getId());
            uiData.put("title", uiConfig.getTitle());
            uiData.put("screen_width", uiConfig.getScreenWidth());
            uiData.put("screen_height", uiConfig.getScreenHeight());
            uiData.put("associated_event_id", uiConfig.getAssociatedEventId());
            uiData.put("screen_properties", uiConfig.getScreenProperties());

            List<Map<String, Object>> elementsData = new ArrayList<>();
            for (com.eventui.api.ui.UIElement element : uiConfig.getRootElements()) {
                elementsData.add(serializeUIElement(element));
            }
            uiData.put("elements", elementsData);

            String uiConfigJson = gson.toJson(uiData);

            Map<String, String> payload = Map.of(
                    "ui_id", uiConfig.getId(),
                    "ui_config", uiConfigJson
            );

            BridgeMessage response = new PluginBridgeMessage(
                    MessageType.UI_CONFIG_RESPONSE,
                    payload,
                    player.getUniqueId(),
                    request.getMessageId()
            );

            sendMessage(response);
            LOGGER.fine("Sent UI config: " + screenId + " (" + uiConfigJson.length() + " chars)");

        } catch (Exception e) {
            LOGGER.severe("Failed to handle UI config request: " + e.getMessage());
            sendErrorResponse(request, "Internal error", player);
        }
    }

    private void handleRequestSkillData(Player player, BridgeMessage message) {
        try {
            sendSkillDataToPlayer(player, message.getMessageId());
        } catch (Exception e) {
            LOGGER.severe("Failed to handle REQUEST_SKILL_DATA: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(message, "Failed to load skill data", player);
        }
    }

    public void sendSkillDataToPlayer(Player player) {
        sendSkillDataToPlayer(player, null);
    }

    private void sendSkillDataToPlayer(Player player, UUID replyToId) {
        UUID playerId = player.getUniqueId();
        com.google.gson.Gson gson = new com.google.gson.Gson();

        Map<String, Object> treesMap = new HashMap<>();

        // Construir datos de todos los skill trees
        for (var tree : plugin.getSkillTreeStorage().getAllSkillTrees().values()) {
            Map<String, Object> treeData = new HashMap<>();
            treeData.put("id", tree.getId());
            treeData.put("displayName", tree.getDisplayName());
            treeData.put("pointType", tree.getPointType());

            Map<String, Object> nodesMap = new HashMap<>();
            var playerProgress = plugin.getSkillProgressStorage().getProgress(playerId);

            for (var node : tree.getNodes()) {
                Map<String, Object> nodeData = new HashMap<>();
                nodeData.put("id", node.getId());
                nodeData.put("displayName", node.getDisplayName());
                nodeData.put("description", node.getDescription());
                nodeData.put("icon", node.getIcon());
                nodeData.put("maxLevel", node.getMaxLevel());
                nodeData.put("positionX", node.getPositionX());
                nodeData.put("positionY", node.getPositionY());

                int currentLevel = playerProgress.map(p -> p.getNodeLevel(tree.getId(), node.getId())).orElse(0);
                nodeData.put("currentLevel", currentLevel);

                // Calcular costo del siguiente nivel
                int costNextLevel = currentLevel >= node.getMaxLevel() ? -1 : node.getCostForLevel(currentLevel + 1);
                nodeData.put("costNextLevel", costNextLevel);

                // Calcular estado del nodo
                String state = calculateNodeState(node, currentLevel, playerProgress.orElse(null), tree.getId());
                nodeData.put("state", state);

                // Requisitos
                List<Map<String, Object>> requiresList = new ArrayList<>();
                for (var req : node.getRequirements()) {
                    Map<String, Object> reqData = new HashMap<>();
                    reqData.put("nodeId", req.getNodeId());
                    reqData.put("minLevel", req.getMinLevel());
                    requiresList.add(reqData);
                }
                nodeData.put("requires", requiresList);
                nodeData.put("requiresMode", node.getRequiresMode());

                // Texture overrides
                Map<String, String> textureOverrides = node.getTextureOverrides();
                if (textureOverrides != null && !textureOverrides.isEmpty()) {
                    nodeData.put("textureOverrideLocked", textureOverrides.get("locked"));
                    nodeData.put("textureOverrideAvailable", textureOverrides.get("available"));
                    nodeData.put("textureOverridePartial", textureOverrides.get("partial"));
                    nodeData.put("textureOverrideMaxed", textureOverrides.get("maxed"));
                }

                nodesMap.put(node.getId(), nodeData);
            }

            treeData.put("nodes", nodesMap);
            treesMap.put(tree.getId(), treeData);
        }

        // Construir datos de puntos disponibles
        Map<String, Object> pointsMap = new HashMap<>();
        var playerProgress = plugin.getSkillProgressStorage().getProgress(playerId);
        if (playerProgress.isPresent()) {
            for (var tree : plugin.getSkillTreeStorage().getAllSkillTrees().values()) {
                String pointType = tree.getPointType();
                int available = playerProgress.get().getAvailablePoints(pointType);
                int totalEarned = playerProgress.get().getTotalEarnedPoints(pointType);

                Map<String, Object> pointsData = new HashMap<>();
                pointsData.put("available", available);
                pointsData.put("totalEarned", totalEarned);
                pointsMap.put(pointType, pointsData);
            }
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("trees", treesMap);
        responseData.put("points", pointsMap);

        // Add connections config
        var connectionsConfig = plugin.getUIConfigManager().getConnectionsConfig();
        Map<String, Object> connectionsMap = new HashMap<>();
        connectionsMap.put("type", connectionsConfig.getType().toString().toLowerCase());
        connectionsMap.put("thickness", connectionsConfig.getThickness());
        connectionsMap.put("color", connectionsConfig.getColor());
        connectionsMap.put("opacity", connectionsConfig.getOpacity());
        connectionsMap.put("dashed", connectionsConfig.isDashed());
        connectionsMap.put("glow", connectionsConfig.hasGlow());
        connectionsMap.put("animated", connectionsConfig.isAnimated());
        connectionsMap.put("lockedColor", connectionsConfig.getLockedColor());
        connectionsMap.put("availableColor", connectionsConfig.getAvailableColor());
        connectionsMap.put("partialColor", connectionsConfig.getPartialColor());
        connectionsMap.put("maxedColor", connectionsConfig.getMaxedColor());
        connectionsMap.put("showOnHover", connectionsConfig.showOnHover());
        responseData.put("connections", connectionsMap);

        String jsonData = gson.toJson(responseData);

        Map<String, String> payload = Map.of("skill_data", jsonData);

        BridgeMessage response = new PluginBridgeMessage(
                MessageType.SKILL_DATA_RESPONSE,
                payload,
                playerId,
                replyToId
        );

        sendMessage(response);
        LOGGER.fine("Sent skill data to player " + player.getName() + " (" + jsonData.length() + " chars)");
    }

    private void handleRequestSkillSpend(Player player, BridgeMessage message) {
        UUID playerId = player.getUniqueId();
        String treeId = message.getPayload().get("tree_id");
        String nodeId = message.getPayload().get("node_id");

        if (treeId == null || nodeId == null) {
            sendErrorResponse(message, "Invalid skill spend request", player);
            return;
        }

        try {
            // Obtener el nodo para información
            var treeOpt = plugin.getSkillTreeStorage().getSkillTree(treeId);
            if (treeOpt.isEmpty()) {
                sendSkillSpendError(playerId, treeId, nodeId, "SKILL_TREE_NOT_FOUND", 0, 0);
                return;
            }

            var tree = treeOpt.get();
            var nodeOpt = tree.getNodes().stream()
                    .filter(n -> n.getId().equals(nodeId))
                    .findFirst();

            if (nodeOpt.isEmpty()) {
                sendSkillSpendError(playerId, treeId, nodeId, "NODE_NOT_FOUND", 0, 0);
                return;
            }

            var node = nodeOpt.get();

            // Intentar gastar puntos
            var spendResult = plugin.getSkillNodeService().trySpendNode(playerId, treeId, nodeId);

            if (spendResult == com.eventui.core.skill.SpendResult.SUCCESS) {
                // Obtener el nuevo nivel y estado
                var playerProgress = plugin.getSkillProgressStorage().getProgress(playerId).orElse(null);
                if (playerProgress == null) return;

                int newLevel = playerProgress.getNodeLevel(treeId, nodeId);
                String newState = calculateNodeState(node, newLevel, playerProgress, treeId);
                int costNextLevel = newLevel >= node.getMaxLevel() ? -1 : node.getCostForLevel(newLevel + 1);
                int pointsAvailable = playerProgress.getAvailablePoints(tree.getPointType());

                // Enviar actualización
                com.google.gson.Gson gson = new com.google.gson.Gson();
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("tree_id", treeId);
                updateData.put("node_id", nodeId);
                updateData.put("new_level", newLevel);
                updateData.put("new_state", newState);
                updateData.put("cost_next_level", costNextLevel);
                updateData.put("points_available", pointsAvailable);

                String jsonData = gson.toJson(updateData);
                Map<String, String> payload = Map.of("update_data", jsonData);

                BridgeMessage response = new PluginBridgeMessage(
                        MessageType.SKILL_NODE_UPDATE,
                        payload,
                        playerId,
                        message.getMessageId()
                );

                sendMessage(response);
                plugin.getPlayerDataManager().requestSave(playerId, "skill spend: " + nodeId);

                LOGGER.fine("✓ Player " + player.getName() + " spent on " + nodeId + " (level " + newLevel + ")");

            } else {
                // Error en el gasto
                var playerProgress = plugin.getSkillProgressStorage().getProgress(playerId);
                int currentLevel = playerProgress.map(p -> p.getNodeLevel(treeId, nodeId)).orElse(0);
                int cost = currentLevel >= node.getMaxLevel() ? 0 : node.getCostForLevel(currentLevel + 1);
                int available = playerProgress.map(p -> p.getAvailablePoints(tree.getPointType())).orElse(0);

                sendSkillSpendError(playerId, treeId, nodeId, spendResult.name(), cost, available);

                LOGGER.fine("Player " + player.getName() + " failed to spend on " + nodeId + ": " + spendResult.name());
            }

        } catch (Exception e) {
            LOGGER.severe("Failed to handle REQUEST_SKILL_SPEND: " + e.getMessage());
            e.printStackTrace();
            sendSkillSpendError(playerId, treeId, nodeId, "ERROR", 0, 0);
        }
    }

    private String calculateNodeState(
            com.eventui.api.skill.SkillNodeDefinition node,
            int currentLevel,
            com.eventui.api.skill.PlayerSkillProgress playerProgress,
            String treeId
    ) {
        if (currentLevel >= node.getMaxLevel()) return "MAXED";
        if (currentLevel > 0) return "PARTIAL";
        if (playerProgress == null) return "LOCKED";

        var requirements = node.getRequirements();
        if (requirements == null || requirements.isEmpty()) return "AVAILABLE";

        boolean isAny = "any".equalsIgnoreCase(node.getRequiresMode());

        for (var req : requirements) {
            int reqLevel = playerProgress.getNodeLevel(treeId, req.getNodeId());
            boolean met = reqLevel >= req.getMinLevel();
            if (isAny && met) return "AVAILABLE";
            if (!isAny && !met) return "LOCKED";
        }

        // "all" mode: all requirements passed → AVAILABLE
        // "any" mode: no requirement passed → LOCKED
        return isAny ? "LOCKED" : "AVAILABLE";
    }

    private void sendSkillSpendError(UUID playerId, String treeId, String nodeId, String errorType, int cost, int available) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("tree_id", treeId);
            errorData.put("node_id", nodeId);
            errorData.put("error", errorType);
            errorData.put("cost", cost);
            errorData.put("available", available);

            String jsonData = gson.toJson(errorData);
            Map<String, String> payload = Map.of("error_data", jsonData);

            BridgeMessage response = new PluginBridgeMessage(
                    MessageType.SKILL_SPEND_ERROR,
                    payload,
                    playerId
            );

            sendMessage(response);
        } catch (Exception e) {
            LOGGER.severe("Failed to send skill spend error: " + e.getMessage());
        }
    }
}