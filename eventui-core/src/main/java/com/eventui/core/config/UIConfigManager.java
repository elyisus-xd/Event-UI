package com.eventui.core.config;

import com.eventui.api.ui.UIConfig;
import com.eventui.core.EventUIPlugin;
import com.eventui.core.messaging.EventMessageConfig;
import com.eventui.core.messaging.EventMessenger;
import com.eventui.core.skill.SkillConnectionsConfig;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class UIConfigManager {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final EventUIPlugin plugin;

    private UIMode mode;
    private String customScreenId;
    private boolean fallbackEnabled;
    private boolean eventsAlwaysActive;

    private EventMessageConfig messageConfig;
    private SkillConnectionsConfig connectionsConfig;
    private EventMessenger messenger;

    private ScheduledExecutorService watcherExecutor;
    private ScheduledFuture<?> watcherTask;
    private long lastConfigModified = 0L;

    public UIConfigManager(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.reloadConfig();
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        String modeStr = config.getString("ui.mode", "hardcoded");
        this.mode = UIMode.fromString(modeStr);
        this.customScreenId = config.getString("ui.custom.screenId", "");
        this.fallbackEnabled = config.getBoolean("ui.custom.fallback", true);
        this.eventsAlwaysActive = config.getBoolean("events.always_active", false);

        if (mode == UIMode.CUSTOM) {
            validateCustomMode();
        }

        // ── Messages ──
        this.messageConfig = new EventMessageConfig(
                config.getBoolean("messages.enabled", true),
                config.getBoolean("messages.progress.enabled",            true),
                config.getString ("messages.progress.format",             null),
                config.getBoolean("messages.objective_completed.enabled", true),
                config.getString ("messages.objective_completed.format",  null),
                config.getBoolean("messages.event_started.enabled",       true),
                config.getString ("messages.event_started.format",        null),
                config.getBoolean("messages.event_completed.enabled",     true),
                config.getString ("messages.event_completed.format",      null),
                config.getBoolean("messages.event_failed.enabled",        true),
                config.getString ("messages.event_failed.format",         null),
                config.getBoolean("messages.event_locked.enabled",        true),
                config.getString ("messages.event_locked.format",         null),
                config.getBoolean("messages.skills.node_leveled_up.enabled", true),
                config.getString ("messages.skills.node_leveled_up.format", null),
                config.getBoolean("messages.skills.node_maxed.enabled", true),
                config.getString ("messages.skills.node_maxed.format", null),
                config.getBoolean("messages.skills.points_granted.enabled", true),
                config.getString ("messages.skills.points_granted.format", null),
                config.getBoolean("messages.skills.insufficient_points.enabled", true),
                config.getString ("messages.skills.insufficient_points.format", null),
                config.getBoolean("messages.skills.requirements_not_met.enabled", true),
                config.getString ("messages.skills.requirements_not_met.format", null),
                config.getBoolean("messages.skills.already_maxed.enabled", true),
                config.getString ("messages.skills.already_maxed.format", null),
                config.getBoolean("messages.skills.exclusive_branch_blocked.enabled", true),
                config.getString ("messages.skills.exclusive_branch_blocked.format", null),
                config.getBoolean("messages.skills.point_source_cooldown.enabled", true),
                config.getString ("messages.skills.point_source_cooldown.format", null)
        );

        if (this.messenger == null) {
            this.messenger = new EventMessenger(plugin, messageConfig);
        } else {
            this.messenger.reload(messageConfig);
        }

        // ── Skill Connections ──
        this.connectionsConfig = new SkillConnectionsConfig(
                config.getString("skills.connections.style.type", "curved"),
                config.getInt("skills.connections.style.thickness", 2),
                config.getString("skills.connections.style.color", "#FFFFFF"),
                (float) config.getDouble("skills.connections.style.opacity", 0.7),
                config.getBoolean("skills.connections.style.dashed", false),
                config.getBoolean("skills.connections.effects.glow", true),
                config.getBoolean("skills.connections.effects.animated", false),
                config.getString("skills.connections.state_colors.locked", "#555555"),
                config.getString("skills.connections.state_colors.available", "#00FF00"),
                config.getString("skills.connections.state_colors.partial", "#FFFF00"),
                config.getString("skills.connections.state_colors.maxed", "#00FFFF"),
                config.getBoolean("skills.connections.show_on_hover", true)
        );
    }

    public EventMessenger getMessenger() { return messenger; }
    public SkillConnectionsConfig getConnectionsConfig() { return connectionsConfig; }


    public boolean isEventsAlwaysActive() {
        return eventsAlwaysActive;
    }

    private void validateCustomMode() {
        if (customScreenId == null || customScreenId.isBlank()) {
            LOGGER.severe("ui.mode is 'custom' but ui.custom.screenId is not set!");
            if (fallbackEnabled) {
                LOGGER.warning("Falling back to hardcoded UI");
                this.mode = UIMode.HARDCODED;
            } else {
                throw new IllegalStateException(
                        "Custom UI mode requires screenId in config.yml"
                );
            }
            return;
        }

        UIConfig uiConfig = plugin.getUIConfigs().get(customScreenId);

        if (uiConfig == null) {
            LOGGER.severe("Custom UI not found: '" + customScreenId + "'. Available: " +
                    (plugin.getUIConfigs().isEmpty() ? "(none)"
                            : String.join(", ", plugin.getUIConfigs().keySet())));
            if (fallbackEnabled) {
                LOGGER.warning("Falling back to hardcoded UI");
                this.mode = UIMode.HARDCODED;
            } else {
                throw new IllegalStateException(
                        "Custom UI '" + customScreenId + "' not found and fallback is disabled"
                );
            }
        }
    }


    public Map<String, String> getUIModeResponse() {
        Map<String, String> response = new HashMap<>();
        response.put("mode", mode.toString().toLowerCase());

        if (mode == UIMode.CUSTOM) {
            response.put("screenId", customScreenId);
        }

        return response;
    }

        public void reload() {
        plugin.reloadConfig();
        this.mode = null;
        this.customScreenId = null;
        this.eventsAlwaysActive = false;        loadConfig();
        LOGGER.fine("✓ UI configuration reloaded");
    }


    public UIMode getMode() {
        return mode;
    }

    public String getCustomScreenId() {
        return customScreenId;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

        public enum UIMode {
        HARDCODED,
        CUSTOM;

        public static UIMode fromString(String str) {
            if (str == null) return HARDCODED;

            return switch (str.toLowerCase()) {
                case "custom" -> CUSTOM;
                case "hardcoded" -> HARDCODED;
                default -> {
                    LOGGER.warning("Unknown UI mode: '" + str + "', defaulting to hardcoded");
                    yield HARDCODED;
                }
            };
        }
    }

        public void invalidateCache() {
        LOGGER.info("Invalidating UI mode cache (forcing re-evaluation on next request)");
    }

        public void startConfigWatcher() {
        stopConfigWatcher();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        this.lastConfigModified = configFile.lastModified();
        this.watcherExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EventUI-ConfigWatcher");
            t.setDaemon(true);
            return t;
        });

        this.watcherTask = watcherExecutor.scheduleAtFixedRate(() -> {
            try {
                long currentModified = configFile.lastModified();
                if (currentModified != lastConfigModified) {
                    lastConfigModified = currentModified;
                    LOGGER.info("  config.yml changed — reloading UI mode");

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String previousScreenId = this.customScreenId;
                        UIMode previousMode = this.mode;

                        reload();

                        // Also reload skill sources config
                        plugin.setSkillSourcesConfig(new com.eventui.core.skill.SkillSourcesConfig(plugin.getConfig()));
                        plugin.setPointSourceManager(new com.eventui.core.skill.PointSourceManager(plugin.getSkillProgressStorage(), plugin.getSkillSourcesConfig(), plugin));

                        boolean modeChanged   = previousMode != this.mode;
                        boolean screenChanged = !String.valueOf(previousScreenId)
                                .equals(String.valueOf(this.customScreenId));

                        if (modeChanged || screenChanged) {
                            LOGGER.info("UI mode changed: " + previousMode + " → " + this.mode
                                    + (this.customScreenId != null
                                    ? " (screen: " + this.customScreenId + ")"
                                    : ""));
                            plugin.notifyUIReload();
                        } else {
                            LOGGER.fine("config.yml reloaded (including skill sources) — no UI notification sent");
                        }
                    });
                }
            } catch (Exception e) {
                LOGGER.severe("Error in config.yml watcher: " + e.getMessage());
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

        public void stopConfigWatcher() {
        if (watcherTask != null) {
            watcherTask.cancel(false);
            watcherTask = null;
        }
        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
            watcherExecutor = null;
        }
    }


}
