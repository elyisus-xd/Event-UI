package com.eventui.core.storage;

import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventProgress;
import com.eventui.api.event.EventState;
import com.eventui.core.EventUIPlugin;
import com.eventui.core.event.EventProgressImpl;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class EventStorage {

    private static final Logger LOGGER = Logger.getLogger("EventUI EventStorage");

    private final Map<String, EventDefinition> eventDefinitions;

    private final EventUIPlugin plugin;

    private final Map<UUID, Map<String, EventProgressImpl>> playerProgress;

    public EventStorage(EventUIPlugin plugin) {        this.eventDefinitions = new ConcurrentHashMap<>();
        this.playerProgress = new ConcurrentHashMap<>();
        this.plugin = plugin;    }

        public void registerEvent(EventDefinition definition) {
        eventDefinitions.put(definition.getId(), definition);
    }

        public void registerEvents(Map<String, EventDefinition> events) {
        eventDefinitions.putAll(events);

        for (Map.Entry<UUID, Map<String, EventProgressImpl>> playerEntry : playerProgress.entrySet()) {
            for (Map.Entry<String, EventProgressImpl> progressEntry : playerEntry.getValue().entrySet()) {
                String eventId = progressEntry.getKey();
                EventProgressImpl progress = progressEntry.getValue();

                EventDefinition newDefinition = eventDefinitions.get(eventId);
                if (newDefinition != null) {
                    
                    for (var objective : newDefinition.getObjectives()) {
                        progress.registerObjective(objective.getId(), objective.getTargetAmount());
                    }
                }
            }
        }
    }

        public Optional<EventDefinition> getEventDefinition(String eventId) {
        return Optional.ofNullable(eventDefinitions.get(eventId));
    }

        public Map<String, EventDefinition> getAllEventDefinitions() {
        return Map.copyOf(eventDefinitions);
    }

        public EventProgressImpl getOrCreateProgress(UUID playerId, String eventId) {
        EventDefinition definition = eventDefinitions.get(eventId);
        if (definition == null) {
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        Map<String, EventProgressImpl> playerEvents = playerProgress.computeIfAbsent(
                playerId,
                k -> new ConcurrentHashMap<>()
        );

        return playerEvents.computeIfAbsent(eventId, k -> {
            EventProgressImpl progress = new EventProgressImpl(
                    playerId,
                    eventId,
                    definition.getObjectives().stream()
                            .map(obj -> obj.getId())
                            .toList()
            );

            definition.getObjectives().forEach(obj ->
                    progress.registerObjective(obj.getId(), obj.getTargetAmount())
            );
            if (progress.getState() == EventState.IN_PROGRESS) {
                plugin.getObjectiveTracker().registerActiveEvent(playerId, eventId);
            }
            return progress;
        });
    }

        public Optional<EventProgress> getProgress(UUID playerId, String eventId) {
        Map<String, EventProgressImpl> playerEvents = playerProgress.get(playerId);
        if (playerEvents == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(playerEvents.get(eventId));
    }

        public void clearPlayerProgress(UUID playerId) {
        playerProgress.remove(playerId);
        LOGGER.info("Cleared progress for player: " + playerId);
    }

        public void removeProgress(UUID playerId, String eventId) {
        Map<String, EventProgressImpl> playerEvents = playerProgress.get(playerId);
        if (playerEvents != null) {
            playerEvents.remove(eventId);
            LOGGER.info("Removed progress for player " + playerId + ", event: " + eventId);
        }
    }

        public Map<UUID, Map<String, EventProgressImpl>> getAllProgress() {
        return Collections.unmodifiableMap(playerProgress);
    }

        public Map<String, EventProgressImpl> getPlayerProgressSnapshot(UUID playerId) {
        Map<String, EventProgressImpl> map = playerProgress.get(playerId);
        return map != null ? Map.copyOf(map) : Map.of();
    }

}
