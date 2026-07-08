package com.eventui.core.storage;

import com.eventui.api.skill.PlayerSkillProgress;
import com.eventui.core.skill.PlayerSkillProgressImpl;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Almacenamiento en memoria del progreso de habilidades de cada jugador.
 * Paralelo a EventStorage: Map<UUID, PlayerSkillProgress>.
 */
public class SkillProgressStorage {

    private static final Logger LOGGER = Logger.getLogger("EventUI SkillProgressStorage");

    private final Map<UUID, PlayerSkillProgressImpl> playerProgress;

    public SkillProgressStorage() {
        this.playerProgress = new ConcurrentHashMap<>();
    }

    /**
     * Obtiene o crea el progreso de habilidades de un jugador.
     */
    public PlayerSkillProgressImpl getOrCreateProgress(UUID playerId) {
        return playerProgress.computeIfAbsent(playerId, k -> new PlayerSkillProgressImpl(playerId));
    }

    /**
     * Obtiene el progreso de habilidades de un jugador.
     */
    public Optional<PlayerSkillProgress> getProgress(UUID playerId) {
        return Optional.ofNullable(playerProgress.get(playerId));
    }

    /**
     * Obtiene todo el progreso de todos los jugadores (para snapshot).
     */
    public Map<UUID, PlayerSkillProgressImpl> getAllProgress() {
        return Collections.unmodifiableMap(playerProgress);
    }

    /**
     * Obtiene un snapshot inmutable del progreso de un jugador.
     */
    public PlayerSkillProgressImpl getPlayerProgressSnapshot(UUID playerId) {
        PlayerSkillProgressImpl progress = playerProgress.get(playerId);
        if (progress == null) return new PlayerSkillProgressImpl(playerId);
        return progress;  // Ya tiene métodos que retornan copias inmutables
    }

    /**
     * Limpia el progreso de un jugador.
     */
    public void clearPlayerProgress(UUID playerId) {
        playerProgress.remove(playerId);
        LOGGER.info("Cleared skill progress for player: " + playerId);
    }
}
