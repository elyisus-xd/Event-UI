package com.eventui.core.storage;

import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventState;
import com.eventui.api.objective.ObjectiveDefinition;
import com.eventui.core.EventUIPlugin;
import com.eventui.core.event.EventProgressImpl;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PlayerDataManager {

    private static final Logger LOGGER = Logger.getLogger("EventUI PlayerDataManager");
    private static final long REQUEST_SAVE_DELAY_TICKS = 20L;

    private final EventUIPlugin plugin;
    private final Object saveLock = new Object();
    private final Map<UUID, BukkitTask> scheduledSaves = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> saveInProgress = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> saveAgainAfterCurrent = ConcurrentHashMap.newKeySet();

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
        var eventsSection = yaml.getConfigurationSection("events");
        if (eventsSection == null) return;

        for (String eventId : eventsSection.getKeys(false)) {
            var defOpt = plugin.getStorage().getEventDefinition(eventId);
            if (defOpt.isEmpty()) {
                LOGGER.info("Ignoring saved progress for unknown event '" + eventId + "' for player " + playerId);
                continue;
            }

            EventDefinition eventDef = defOpt.get();
            Map<String, ObjectiveDefinition> currentObjectives = new HashMap<>();
            eventDef.getObjectives().forEach(objective -> currentObjectives.put(objective.getId(), objective));

            String stateName = yaml.getString("events." + eventId + ".state", "AVAILABLE");
            EventState state;
            try {
                state = EventState.valueOf(stateName);
            } catch (Exception e) {
                LOGGER.warning("Invalid saved state '" + stateName + "' for event '" + eventId
                        + "' and player " + playerId + "; falling back to AVAILABLE");
                state = EventState.AVAILABLE;
            }

            if (state == EventState.AVAILABLE) continue;
            EventProgressImpl progress = plugin.getStorage()
                    .getOrCreateProgress(playerId, eventId);

            var objectivesSection = yaml.getConfigurationSection("events." + eventId + ".objectives");
            if (objectivesSection != null) {
                for (String objId : objectivesSection.getKeys(false)) {
                    ObjectiveDefinition objectiveDef = currentObjectives.get(objId);
                    if (objectiveDef == null) {
                        LOGGER.info("Ignoring saved progress for removed objective '" + objId
                                + "' in event '" + eventId + "' for player " + playerId);
                        continue;
                    }

                    String currentPath = "events." + eventId + ".objectives." + objId + ".current";
                    int current = readObjectiveCurrent(yaml, currentPath, playerId, eventId, objId);
                    int clampedCurrent = clampObjectiveCurrent(
                            current,
                            objectiveDef.getTargetAmount(),
                            playerId,
                            eventId,
                            objId
                    );

                    var objProgress = progress.getObjectiveProgress(objId);
                    if (objProgress != null) objProgress.setProgress(clampedCurrent);
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

            LOGGER.fine("Restored " + eventId + " -> " + state + " for " + playerId);
        }
    }

    private int readObjectiveCurrent(YamlConfiguration yaml, String path, UUID playerId, String eventId, String objId) {
        Object rawValue = yaml.get(path);
        if (rawValue instanceof Number number) {
            return number.intValue();
        }

        if (rawValue instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                LOGGER.warning("Invalid saved current value '" + value + "' for objective '" + objId
                        + "' in event '" + eventId + "' for player " + playerId + "; using 0");
                return 0;
            }
        }

        if (rawValue != null) {
            LOGGER.warning("Invalid saved current value type '" + rawValue.getClass().getSimpleName()
                    + "' for objective '" + objId + "' in event '" + eventId + "' for player "
                    + playerId + "; using 0");
        }

        return 0;
    }

    private int clampObjectiveCurrent(int current, int targetAmount, UUID playerId, String eventId, String objId) {
        if (current < 0) {
            LOGGER.warning("Saved current value below 0 for objective '" + objId + "' in event '"
                    + eventId + "' for player " + playerId + ": " + current + "; clamping to 0");
            return 0;
        }

        if (current > targetAmount) {
            LOGGER.warning("Saved current value above current target for objective '" + objId + "' in event '"
                    + eventId + "' for player " + playerId + ": " + current + " > "
                    + targetAmount + "; clamping to target");
            return targetAmount;
        }

        return current;
    }

    public void requestSave(UUID playerId, String reason) {
        synchronized (saveLock) {
            if (scheduledSaves.containsKey(playerId)) {
                LOGGER.fine("Save already scheduled for " + playerId + "; coalescing reason: " + reason);
                return;
            }

            if (saveInProgress.contains(playerId)) {
                saveAgainAfterCurrent.add(playerId);
                LOGGER.fine("Save already in progress for " + playerId + "; queued fresh save after: " + reason);
                return;
            }

            BukkitTask task = new BukkitRunnable() {
                @Override public void run() {
                    synchronized (saveLock) {
                        scheduledSaves.remove(playerId);
                    }
                    savePlayerData(playerId);
                }
            }.runTaskLater(plugin, REQUEST_SAVE_DELAY_TICKS);

            scheduledSaves.put(playerId, task);
            LOGGER.fine("Scheduled player data save for " + playerId + " in "
                    + REQUEST_SAVE_DELAY_TICKS + " ticks; reason: " + reason);
        }
    }

    public void savePlayerData(UUID playerId) {
        synchronized (saveLock) {
            BukkitTask scheduled = scheduledSaves.remove(playerId);
            if (scheduled != null) {
                scheduled.cancel();
            }

            if (!saveInProgress.add(playerId)) {
                saveAgainAfterCurrent.add(playerId);
                return;
            }
        }

        Map<String, EventProgressImpl> snapshot = createSnapshot(playerId);

        new BukkitRunnable() {
            @Override public void run() {
                writeSnapshotsUntilClean(playerId, snapshot);
            }
        }.runTaskAsynchronously(plugin);
    }

    private Map<String, EventProgressImpl> createSnapshot(UUID playerId) {
        return Map.copyOf(plugin.getStorage().getPlayerProgressSnapshot(playerId));
    }

    private void writeSnapshotsUntilClean(UUID playerId, Map<String, EventProgressImpl> initialSnapshot) {
        Map<String, EventProgressImpl> snapshot = initialSnapshot;

        while (true) {
            writeSnapshot(playerId, snapshot);

            synchronized (saveLock) {
                if (!saveAgainAfterCurrent.remove(playerId)) {
                    saveInProgress.remove(playerId);
                    return;
                }
            }

            snapshot = createSnapshot(playerId);
        }
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
