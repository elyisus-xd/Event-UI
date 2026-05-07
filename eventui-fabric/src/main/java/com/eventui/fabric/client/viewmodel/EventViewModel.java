package com.eventui.fabric.client.viewmodel;

import com.eventui.api.bridge.MessageType;
import com.eventui.api.event.EventState;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventViewModel.class);

    private final UUID playerId;
    private final ClientEventBridge bridge;
    private final Map<String, EventData> eventsCache;

    private final List<Consumer<List<EventData>>> changeListeners;

    private final Gson gson = new Gson();

    public EventViewModel(UUID playerId) {
        this.playerId = playerId;
        this.bridge = ClientEventBridge.getInstance();
        this.eventsCache = new ConcurrentHashMap<>();
        this.changeListeners = new ArrayList<>();

        subscribeToUpdates();
    }

    private void subscribeToUpdates() {
        LOGGER.info("Subscribing to bridge updates...");
        bridge.registerMessageHandler(MessageType.PROGRESS_UPDATE, message -> {
            String eventId = message.getPayload().get("event_id");
            String objectiveId = message.getPayload().get("objective_id");
            String currentStr = message.getPayload().get("current");
            String targetStr = message.getPayload().get("target");
            String description = message.getPayload().get("description");

            if (currentStr != null && targetStr != null) {
                int current = Integer.parseInt(currentStr);
                int target = Integer.parseInt(targetStr);

                LOGGER.info("Received progress update: event={}, objective={}, progress={}/{}, desc={}",
                        eventId, objectiveId, current, target, description);

                updateProgressInCache(eventId, objectiveId, current, target, description);
                notifyListeners();
            }
        });

        bridge.registerMessageHandler(MessageType.EVENT_STATE_CHANGED, message -> {
            String eventId = message.getPayload().get("event_id");
            String newStateStr = message.getPayload().get("new_state");

            if (eventId != null && newStateStr != null) {
                EventState newState = EventState.valueOf(newStateStr);
                LOGGER.info("Received state change: event={}, newState={}", eventId, newState);
                updateStateInCache(eventId, newState);
                if (newState == EventState.COMPLETED || newState == EventState.AVAILABLE) {
                    LOGGER.info("Requesting fresh data after state change to {}", newState);
                    requestEvents();
                } else {
                    notifyListeners();
                }
            }
        });

        bridge.registerMessageHandler(MessageType.EVENT_DATA_RESPONSE, message -> {
            LOGGER.info("Received EVENT_DATA_RESPONSE");
            handleEventDataResponse(message);
        });

        bridge.registerMessageHandler(MessageType.EVENT_RELOAD_NOTIFICATION, message -> {
            LOGGER.info("⚠️ Server reloaded events, clearing cache and requesting fresh data");
            eventsCache.clear();
            requestEvents();
        });
        LOGGER.info("Bridge subscriptions registered");
    }


    private void updateStateInCache(String eventId, EventState newState) {
        EventData event = eventsCache.get(eventId);

        if (event != null) {
            event.state = newState;
            LOGGER.info("Updated state for event '{}': {}", eventId, newState);
        } else {
            LOGGER.warn("Cannot update state, event not in cache: {}", eventId);
        }
    }

    private void handleEventDataResponse(com.eventui.api.bridge.BridgeMessage message) {
        try {
            String eventsJson = message.getPayload().get("events");
            String countStr = message.getPayload().get("count");

            LOGGER.info("Processing EVENT_DATA_RESPONSE with {} events", countStr);

            if (eventsJson == null || eventsJson.isEmpty()) {
                LOGGER.warn("Empty events JSON in response");
                return;
            }

            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> eventsList = gson.fromJson(eventsJson, listType);
            LOGGER.info("Deserialized {} events from JSON", eventsList.size());
            eventsCache.clear();

            for (Map<String, Object> eventMap : eventsList) {
                String id = (String) eventMap.get("id");
                String displayName = (String) eventMap.get("displayName");
                String description = (String) eventMap.get("description");
                String stateStr = (String) eventMap.get("state");
                String icon = (String) eventMap.getOrDefault("icon", "minecraft:paper");
                String category = (String) eventMap.getOrDefault("category", "general");
                String difficulty = (String) eventMap.getOrDefault("difficulty", "easy");
                String rewardsJson = (String) eventMap.getOrDefault("rewards", "{}");

                EventState state = EventState.valueOf(stateStr);

                int currentProgress = 0;
                int targetProgress = 0;
                String currentObjective = null;

                if (eventMap.containsKey("currentProgress")) {
                    currentProgress = ((Number) eventMap.get("currentProgress")).intValue();
                    targetProgress = ((Number) eventMap.get("targetProgress")).intValue();
                    currentObjective = (String) eventMap.get("currentObjective");
                }

                boolean repeatable = false;
                Object repeatableObj = eventMap.get("repeatable");
                if (repeatableObj instanceof Boolean) {
                    repeatable = (Boolean) repeatableObj;
                } else if (repeatableObj instanceof String) {
                    repeatable = Boolean.parseBoolean((String) repeatableObj);
                }

                long startedAt = 0;
                if (eventMap.containsKey("startedAt")) {
                    startedAt = Long.parseLong(eventMap.get("startedAt").toString());
                }

                List<String> dependencies = new ArrayList<>();
                Object depsObj = eventMap.get("dependencies");
                if (depsObj instanceof String) {
                    String depsJson = (String) depsObj;
                    if (!depsJson.isEmpty() && !depsJson.equals("[]")) {
                        try {
                            Type listStringType = new TypeToken<List<String>>(){}.getType();
                            dependencies = gson.fromJson(depsJson, listStringType);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse dependencies for event {}", id);
                        }
                    }
                } else if (depsObj instanceof List) {
                    for (Object dep : (List<?>) depsObj) {
                        if (dep instanceof String) {
                            dependencies.add((String) dep);
                        }
                    }
                }

                boolean isLocked = false;
                Object isLockedObj = eventMap.get("isLocked");
                if (isLockedObj instanceof Boolean) {
                    isLocked = (Boolean) isLockedObj;
                } else if (isLockedObj instanceof String) {
                    isLocked = Boolean.parseBoolean((String) isLockedObj);
                }
                EventData eventData = new EventData(
                        id,
                        displayName,
                        description,
                        state,
                        currentProgress,
                        targetProgress,
                        currentObjective,
                        repeatable,
                        icon,
                        category,
                        difficulty,
                        rewardsJson,
                        startedAt,
                        dependencies,
                        isLocked
                );

                eventsCache.put(id, eventData);

                LOGGER.info("  Loaded event: {} - {} (state={}, locked={}, deps={})",
                        id, displayName, state, isLocked, dependencies.size());
            }
            LOGGER.info("✓ Successfully loaded {} events into cache", eventsCache.size());
            long lockedCount = eventsCache.values().stream().filter(e -> e.isLocked).count();
            LOGGER.info(">>> LOCKED EVENTS COUNT: {}", lockedCount);
            notifyListeners();

        } catch (Exception e) {
            LOGGER.error("Failed to process EVENT_DATA_RESPONSE", e);
            e.printStackTrace();
        }
    }

    private void updateProgressInCache(String eventId, String objectiveId, int current, int target, String objectiveDescription) {
        EventData event = eventsCache.get(eventId);

        if (event != null) {
            event.state = EventState.IN_PROGRESS;
            event.currentProgress = current;
            event.targetProgress = target;
            event.currentObjectiveDescription = objectiveDescription != null ? objectiveDescription : "In progress...";

            LOGGER.info("Updated cache for event '{}': {}/{}, desc={}", eventId, current, target, objectiveDescription);
        } else {
            LOGGER.info("Event not in cache, creating new entry: {}", eventId);
            EventData newEvent = new EventData(
                    eventId,
                    "Event " + eventId,
                    "Loading...",
                    EventState.IN_PROGRESS,
                    current,
                    target,
                    objectiveDescription != null ? objectiveDescription : "Loading...",
                    false,
                    "minecraft:paper",
                    "general",
                    "easy",
                    "{}",
                    0L,
                    new ArrayList<>(),
                    false
            );

            eventsCache.put(eventId, newEvent);
        }
    }


    public void requestEvents() {
        LOGGER.info("Requesting events from server via bridge...");

        try {
            com.eventui.fabric.client.bridge.BridgeMessageImpl request =
                    new com.eventui.fabric.client.bridge.BridgeMessageImpl(
                            MessageType.REQUEST_EVENT_DATA,
                            Map.of("player_id", playerId.toString()),
                            playerId
                    );
            bridge.sendMessage(request);
            LOGGER.info("✓ Event request sent to server");
        } catch (Exception e) {
            LOGGER.error("Failed to request events", e);
        }
    }

    public List<EventData> getAllEvents() {
        return new ArrayList<>(eventsCache.values());
    }

    public void addChangeListener(Consumer<List<EventData>> listener) {
        changeListeners.add(listener);
        LOGGER.debug("Change listener added, total listeners: {}", changeListeners.size());
    }
    public void removeChangeListener(Consumer<List<EventData>> listener) {
        changeListeners.remove(listener);
        LOGGER.debug("Change listener removed, total listeners: {}", changeListeners.size());
    }

    private void notifyListeners() {
        List<EventData> events = getAllEvents();
        LOGGER.info("Notifying {} listeners with {} events", changeListeners.size(), events.size());

        for (Consumer<List<EventData>> listener : changeListeners) {
            try {
                listener.accept(events);
            } catch (Exception e) {
                LOGGER.error("Error notifying listener", e);
            }
        }
    }

    public void updateEvents(List<EventData> newEvents) {
        eventsCache.clear();
        for (EventData event : newEvents) {
            eventsCache.put(event.id, event);
        }
        notifyListeners();
    }
    public void dispose() {
        changeListeners.clear();
        eventsCache.clear();
        LOGGER.info("ViewModel disposed");
    }

    public static class EventData {
        public final String id;
        public final String displayName;
        public final String description;
        public final String rewardsJson;
        public EventState state;
        public int currentProgress;
        public int targetProgress;
        public String currentObjectiveDescription;
        public boolean repeatable;
        public final String icon;
        public final String category;
        public final String difficulty;
        public long startedAt;
        public final List<String> dependencies;
        public final boolean isLocked;
        public boolean hasRewards;

        public EventData(String id, String displayName, String description, EventState state,
                         int currentProgress, int targetProgress, String currentObjectiveDescription,
                         boolean repeatable, String icon, String category, String difficulty,
                         String rewardsJson, long startedAt, List<String> dependencies,
                         boolean isLocked) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.state = state;
            this.currentProgress = currentProgress;
            this.targetProgress = targetProgress;
            this.currentObjectiveDescription = currentObjectiveDescription;
            this.repeatable = repeatable;
            this.icon = icon;
            this.category = category;
            this.difficulty = difficulty;
            this.rewardsJson = rewardsJson;
            this.startedAt = startedAt;
            this.dependencies = dependencies;
            this.isLocked = isLocked;
        }

        public boolean hasRewards() {
            return rewardsJson != null && !rewardsJson.equals("{}");
        }

        public float getProgressPercentage() {
            if (targetProgress == 0) return 0f;
            return (float) currentProgress / targetProgress;
        }
    }
}
