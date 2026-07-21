package com.eventui.core.event;

import com.eventui.api.event.EventProgress;
import com.eventui.api.event.EventState;
import com.eventui.api.objective.ObjectiveProgress;
import com.eventui.core.objective.ObjectiveProgressImpl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventProgressImpl implements EventProgress {

    private final UUID playerId;
    private final String eventId;
    private final Map<String, ObjectiveProgressImpl> objectivesProgress;

    private volatile EventState state;
    private volatile long startedAt;
    private volatile long completedAt;

    public EventProgressImpl(UUID playerId, String eventId, List<String> objectiveIds) {
        this.playerId = playerId;
        this.eventId = eventId;
        this.state = EventState.AVAILABLE;
        this.startedAt = 0;
        this.completedAt = 0;

        this.objectivesProgress = new ConcurrentHashMap<>();
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public EventState getState() {
        return state;
    }

    @Override
    public List<ObjectiveProgress> getObjectivesProgress() {
        return List.copyOf(objectivesProgress.values());
    }

    @Override
    public float getOverallProgress() {
        if (objectivesProgress.isEmpty()) {
            return 0.0f;
        }

        float total = 0.0f;
        for (ObjectiveProgress progress : objectivesProgress.values()) {
            total += progress.getProgressPercentage();
        }

        return total / objectivesProgress.size();
    }

    @Override
    public long getStartedAt() {
        return startedAt;
    }

    @Override
    public long getCompletedAt() {
        return completedAt;
    }

        public synchronized void start() {
        if (state == EventState.AVAILABLE) {
            state = EventState.IN_PROGRESS;
            startedAt = System.currentTimeMillis();
        }
    }

        public synchronized void complete() {
        if (state == EventState.IN_PROGRESS) {
            state = EventState.COMPLETED;
            completedAt = System.currentTimeMillis();
        }
    }

        public synchronized void fail() {
        if (state == EventState.IN_PROGRESS) {
            state = EventState.FAILED;
        }
    }

        public ObjectiveProgressImpl getObjectiveProgress(String objectiveId) {
        return objectivesProgress.get(objectiveId);
    }

        public void registerObjective(String objectiveId, int targetAmount) {
        objectivesProgress.putIfAbsent(objectiveId, new ObjectiveProgressImpl(objectiveId, targetAmount));
    }

        public boolean areAllObjectivesCompleted() {
        return objectivesProgress.values().stream()
                .allMatch(ObjectiveProgress::isCompleted);
    }

}