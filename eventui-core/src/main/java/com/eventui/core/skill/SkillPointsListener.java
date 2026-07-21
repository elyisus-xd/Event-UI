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

    private final Map<UUID, Integer> levelAccumulator = new HashMap<>();

    public SkillPointsListener(EventUIPlugin plugin, SkillSourcesConfig config, PointSourceManager pointSourceManager) {
        this.plugin = plugin;
        this.config = config;
        this.pointSourceManager = pointSourceManager;
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        
        pointSourceManager.handleXpGain(
            event.getPlayer(),
            event.getNewLevel() - event.getOldLevel(),
            event.getNewLevel() > event.getOldLevel()
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        
        levelAccumulator.remove(event.getPlayer().getUniqueId());
        pointSourceManager.cleanup();
    }

    private int calculatePoints(UUID playerId, int levelsGained) {
        int levelsPerPoint = config.getLevelsPerPoint();

        if (levelsPerPoint == 1) {
            return levelsGained;
        }

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
