package com.eventui.core.skill;

import com.eventui.core.EventUIPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillPointsListener implements Listener {

    private final EventUIPlugin plugin;
    private final SkillSourcesConfig config;
    private final PointSourceManager pointSourceManager;

    // Accumulator for leftover levels when levels_per_point > 1
    private final Map<UUID, Integer> levelAccumulator = new HashMap<>();

    public SkillPointsListener(EventUIPlugin plugin, SkillSourcesConfig config, PointSourceManager pointSourceManager) {
        this.plugin = plugin;
        this.config = config;
        this.pointSourceManager = pointSourceManager;
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        // Delegate to PointSourceManager for XP conversion
        pointSourceManager.handleXpGain(
            event.getPlayer(),
            event.getNewLevel() - event.getOldLevel(),
            event.getNewLevel() > event.getOldLevel()
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up accumulator when player leaves
        levelAccumulator.remove(event.getPlayer().getUniqueId());
        pointSourceManager.cleanup();
    }

    /**
     * Calculate points to grant based on levels gained and levels_per_point configuration.
     * Handles accumulation when levels_per_point > 1.
     *
     * Example: if levels_per_point = 5:
     *   - Player gains 3 levels: accumulated = 3, points = 0 (store for next time)
     *   - Player gains 2 levels: accumulated = 5, points = 1, reset accumulator
     */
    private int calculatePoints(UUID playerId, int levelsGained) {
        int levelsPerPoint = config.getLevelsPerPoint();

        // Simple case: 1 level = 1 point
        if (levelsPerPoint == 1) {
            return levelsGained;
        }

        // Complex case: need accumulator
        int accumulated = levelAccumulator.getOrDefault(playerId, 0);
        accumulated += levelsGained;

        int pointsToGrant = accumulated / levelsPerPoint;
        int remaining = accumulated % levelsPerPoint;

        if (remaining > 0) {
            levelAccumulator.put(playerId, remaining);
        } else {
            levelAccumulator.remove(playerId);
        }

        return pointsToGrant;
    }
}
