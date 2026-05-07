package com.eventui.core.storage;

import com.eventui.api.event.EventState;
import com.eventui.core.EventUIPlugin;
import com.eventui.core.event.EventProgressImpl;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerDataManager {

    private static final Logger LOGGER = Logger.getLogger(PlayerDataManager.class.getName());
    private final EventUIPlugin plugin;

    public PlayerDataManager(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadPlayerData(UUID playerId) {
        new BukkitRunnable() {
            @Override public void run() {
                File file = getDataFile(playerId);
                if (!file.exists()) return;

                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

                new BukkitRunnable() {
                    @Override public void run() {
                        applyLoadedData(playerId, yaml);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void applyLoadedData(UUID playerId, YamlConfiguration yaml) {
        if (!yaml.contains("events")) return;

        for (String eventId : yaml.getConfigurationSection("events").getKeys(false)) {
            var defOpt = plugin.getStorage().getEventDefinition(eventId);
            if (defOpt.isEmpty()) continue;

            String stateName = yaml.getString("events." + eventId + ".state", "AVAILABLE");
            EventState state;
            try { state = EventState.valueOf(stateName); }
            catch (Exception e) { continue; }

            if (state == EventState.AVAILABLE) continue;
            EventProgressImpl progress = plugin.getStorage()
                    .getOrCreateProgress(playerId, eventId);

            if (yaml.contains("events." + eventId + ".objectives")) {
                for (String objId : yaml.getConfigurationSection(
                        "events." + eventId + ".objectives").getKeys(false)) {
                    int current = yaml.getInt("events." + eventId + ".objectives." + objId + ".current", 0);
                    var objProgress = progress.getObjectiveProgress(objId);
                    if (objProgress != null) objProgress.setProgress(current);
                }
            }

            if (state == EventState.IN_PROGRESS) {
                progress.start();
                plugin.getObjectiveTracker().registerActiveEvent(playerId, eventId);
            } else if (state == EventState.COMPLETED) {
                progress.start();
                progress.complete();
            } else if (state == EventState.FAILED) {
                progress.start();
                progress.fail();
            }

            LOGGER.fine("Restored " + eventId + " → " + state + " for " + playerId);
        }
    }

    public void savePlayerData(UUID playerId) {
        Map<String, EventProgressImpl> snapshot =
                Map.copyOf(plugin.getStorage().getPlayerProgressSnapshot(playerId));

        new BukkitRunnable() {
            @Override public void run() {
                writeSnapshot(playerId, snapshot);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void writeSnapshot(UUID playerId, Map<String, EventProgressImpl> progressMap) {
        YamlConfiguration yaml = new YamlConfiguration();

        for (var entry : progressMap.entrySet()) {
            String eventId = entry.getKey();
            EventProgressImpl p = entry.getValue();
            String path = "events." + eventId;

            yaml.set(path + ".state", p.getState().name());
            yaml.set(path + ".startedAt", p.getStartedAt());
            yaml.set(path + ".completedAt", p.getCompletedAt());

            p.getObjectivesProgress().forEach(obj ->
                    yaml.set(path + ".objectives." + obj.getObjectiveId() + ".current",
                            obj.getCurrentAmount())
            );
        }

        try {
            File targetFile = getDataFile(playerId);
            targetFile.getParentFile().mkdirs();
            File tempFile = new File(targetFile.getParentFile(), playerId + ".tmp");
            yaml.save(tempFile);
            java.nio.file.Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception e) {
            LOGGER.warning("Failed to save data for " + playerId + ": " + e.getMessage());
        }
    }



    public void saveAll() {
        plugin.getServer().getOnlinePlayers().forEach(p -> savePlayerData(p.getUniqueId()));
    }

    private File getDataFile(UUID playerId) {
        return new File(plugin.getDataFolder(), "playerdata/" + playerId + ".yml");
    }
}
