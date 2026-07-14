package com.eventui.core.storage;

import com.eventui.core.EventUIPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerDataListener implements Listener {

    private final EventUIPlugin plugin;

    public PlayerDataListener(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().loadPlayerData(event.getPlayer().getUniqueId());

        // Enviar datos de skills al cliente
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            plugin.getEventBridge().sendSkillDataToPlayer(event.getPlayer());
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().savePlayerData(event.getPlayer().getUniqueId());
    }
}
