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
    
    // Accumulator for leftover levels when levels_per_point > 1
    private final Map<UUID, Integer> levelAccumulator = new HashMap<>();

    public SkillPointsListener(EventUIPlugin plugin, SkillSourcesConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        // 1. Verify XP conversion is enabled
        if (!config.isXpConversionEnabled()) {
            return;
        }

        // 2. If only_on_level_up, ignore when level DECREASES
        if (config.isOnlyOnLevelUp() && event.getNewLevel() <= event.getOldLevel()) {
            return;
        }

        // 3. Calculate how many levels were gained in this event
        int levelsGained = event.getNewLevel() - event.getOldLevel();
        if (levelsGained <= 0) {
            return;
        }

        // 4. Calculate points to grant using levels_per_point
        int pointsToGrant = calculatePoints(event.getPlayer().getUniqueId(), levelsGained);
        if (pointsToGrant <= 0) {
            return;
        }

        // 5. Grant the points
        String pointType = config.getXpPointType();
        var skillProgress = plugin.getSkillProgressStorage()
            .getOrCreateProgress(event.getPlayer().getUniqueId());
        skillProgress.addEarnedPoints(pointType, pointsToGrant);

        // 6. Send message to player (using EventMessenger.sendPointsGranted)
        plugin.getMessenger().sendPointsGranted(event.getPlayer(), pointsToGrant, pointType);

        // 7. Persist data
        plugin.getPlayerDataManager().requestSave(
            event.getPlayer().getUniqueId(), "xp conversion: +" + pointsToGrant);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up accumulator when player leaves
        levelAccumulator.remove(event.getPlayer().getUniqueId());
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
