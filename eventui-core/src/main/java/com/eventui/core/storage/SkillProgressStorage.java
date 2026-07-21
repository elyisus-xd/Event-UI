package com.eventui.core.storage;

import com.eventui.api.skill.PlayerSkillProgress;
import com.eventui.core.skill.PlayerSkillProgressImpl;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SkillProgressStorage {

    private static final Logger LOGGER = Logger.getLogger("EventUI SkillProgressStorage");

    private final Map<UUID, PlayerSkillProgressImpl> playerProgress;

    public SkillProgressStorage() {
        this.playerProgress = new ConcurrentHashMap<>();
    }

    public PlayerSkillProgressImpl getOrCreateProgress(UUID playerId) {
        return playerProgress.computeIfAbsent(playerId, k -> new PlayerSkillProgressImpl(playerId));
    }

    public Optional<PlayerSkillProgress> getProgress(UUID playerId) {
        return Optional.ofNullable(playerProgress.get(playerId));
    }

    public Map<UUID, PlayerSkillProgressImpl> getAllProgress() {
        return Collections.unmodifiableMap(playerProgress);
    }

    public PlayerSkillProgressImpl getPlayerProgressSnapshot(UUID playerId) {
        PlayerSkillProgressImpl progress = playerProgress.get(playerId);
        if (progress == null) return new PlayerSkillProgressImpl(playerId);
        return progress;  
    }

    public void clearPlayerProgress(UUID playerId) {
        playerProgress.remove(playerId);
        LOGGER.info("Cleared skill progress for player: " + playerId);
    }
}
