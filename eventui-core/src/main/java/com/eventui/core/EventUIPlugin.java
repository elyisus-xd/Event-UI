package com.eventui.core;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.bridge.MessageType;
import com.eventui.api.event.EventDefinition;
import com.eventui.api.objective.ObjectiveType;
import com.eventui.api.ui.UIConfig;
import com.eventui.core.bridge.PluginBridgeMessage;
import com.eventui.core.bridge.PluginEventBridge;
import com.eventui.core.commands.EventCommand;
import com.eventui.core.commands.EventCommandTabCompleter;
import com.eventui.core.config.*;
import com.eventui.core.messaging.EventMessenger;
import com.eventui.core.rewards.RewardManager;
import com.eventui.core.storage.EventStorage;
import com.eventui.core.storage.PlayerDataListener;
import com.eventui.core.storage.PlayerDataManager;
import com.eventui.core.tracking.ObjectiveTracker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class EventUIPlugin extends JavaPlugin {

    private static EventUIPlugin instance;
    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private BukkitAudiences adventure;

    private EventConfigLoader configLoader;
    private EventStorage storage;
    private PluginEventBridge eventBridge;
    private UIConfigLoader uiConfigLoader;
    private Map<String, UIConfig> uiConfigs;
    private RewardManager rewardManager;
    private ObjectiveTracker objectiveTracker;
    private UIConfigManager uiConfigManager;
    private UIStateManager uiStateManager;
    private UIFileWatcher fileWatcher;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;

        LOGGER.info("  EventUI Plugin initialization...");
        this.adventure = BukkitAudiences.create(this);
        this.configLoader = new EventConfigLoader(getDataFolder());
        this.uiConfigLoader = new UIConfigLoader(getDataFolder());
        this.uiConfigs = uiConfigLoader.loadAllUIConfigs();
        this.fileWatcher = new UIFileWatcher(this);
        this.fileWatcher.start();
        this.uiConfigManager = new UIConfigManager(this);
        this.uiConfigManager.loadConfig();
        this.uiConfigManager.startConfigWatcher();
        this.uiStateManager = new UIStateManager(this);
        this.storage = new EventStorage(this);
        this.rewardManager = new RewardManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        loadEvents();
        initializeBridge();
        registerTrackers();
        objectiveTracker.buildObjectiveTypeIndex();
        objectiveTracker.initializeActiveEventsIndex();

        registerCommands();

        getServer().getScheduler().runTaskTimer(this, () ->
                getServer().getOnlinePlayers().forEach(player ->
                        objectiveTracker.checkCollectObjectives(player)
                ), 40L, 40L);
        getServer().getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(player -> {
            Set<String> relevantEvents = objectiveTracker.getRelevantActiveEvents(
                    player.getUniqueId(), ObjectiveType.REACH_LOCATION);

            if (!relevantEvents.isEmpty()) {
                objectiveTracker.checkReachLocationObjectives(player);
            }

        }), 40L, 40L);

        getServer().getScheduler().runTaskTimer(this, () ->
                getServer().getOnlinePlayers().forEach(player ->
                        playerDataManager.savePlayerData(player.getUniqueId())
                ), 6000L, 6000L); // delay inicial = 5 min, intervalo = 5 min

        LOGGER.info("✓ Auto-save scheduled every 5 minutes");

        LOGGER.info("✓ EventUI enabled — " + storage.getAllEventDefinitions().size()
                + " events, " + uiConfigs.size() + " UIs loaded");
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    @Override
    public void onDisable() {
        if (eventBridge != null) {
            eventBridge.getNetworkHandler().unregister();
        }

        if (uiConfigManager != null) {
            uiConfigManager.stopConfigWatcher();
        }

        if (fileWatcher != null) fileWatcher.stop();

        if (playerDataManager != null) {
            playerDataManager.saveAll();
            LOGGER.info("✓ Player data saved on disable");
        }

        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }

        LOGGER.info("EventUI disabled");
        instance = null;
    }

    public BukkitAudiences adventure() { return adventure; }

    private void loadEvents() {
        try {
            Map<String, EventDefinition> events = configLoader.loadAllEvents();

            if (events.isEmpty()) {
                LOGGER.warning("No events loaded! Check your events/ directory");
                return;
            }

            storage.registerEvents(events);

            LOGGER.info("Successfully loaded events:");
            events.values().forEach(event ->
                    LOGGER.info("  - " + event.getId() + ": " + event.getDisplayName())
            );

        } catch (IllegalStateException e) {
            LOGGER.severe("╔════════════════════════════════════════════╗");
            LOGGER.severe("║  CRITICAL: Invalid event configuration    ║");
            LOGGER.severe("╚════════════════════════════════════════════╝");
            LOGGER.severe("");
            LOGGER.severe("Error: " + e.getMessage());
            LOGGER.severe("");
            LOGGER.severe("Fix the dependency cycle in your event YAML files");
            LOGGER.severe("and restart the server.");
            LOGGER.severe("");

            getServer().getPluginManager().disablePlugin(this);

        } catch (Exception e) {
            LOGGER.severe("Failed to load events: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeBridge() {
        this.eventBridge = new PluginEventBridge(this);
        LOGGER.info("EventBridge initialized");
    }

    private void registerTrackers() {
        this.objectiveTracker = new ObjectiveTracker(this);
        getServer().getPluginManager().registerEvents(objectiveTracker, this);
        LOGGER.info("Registered objective trackers");
    }

    private void registerCommands() {
        var command = getCommand("eventui");
        if (command != null) {
            EventCommand executor = new EventCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(new EventCommandTabCompleter(this));
            LOGGER.info("Registered commands with tab completion");
        } else {
            LOGGER.warning("Failed to register eventui command - command not found in plugin.yml!");
        }
    }

    public void reloadEvents() {
        LOGGER.info("Reloading events and UI configuration...");
        loadEvents();
        this.uiConfigs = uiConfigLoader.loadAllUIConfigs();
        LOGGER.info("✓ Reloaded " + uiConfigs.size() + " UI config(s)");
        this.uiConfigManager.reload();
        objectiveTracker.buildObjectiveTypeIndex();
        objectiveTracker.initializeActiveEventsIndex();
        notifyUIReload();
    }

    public void notifyUIReload() {
        for (Player player : getServer().getOnlinePlayers()) {
            Map<String, String> payload = Map.of("reason", "config_reload");
            BridgeMessage message = new PluginBridgeMessage(
                    MessageType.EVENT_RELOAD_NOTIFICATION, payload, player.getUniqueId());
            eventBridge.sendMessage(message);
        }
        LOGGER.info("✓ Notified " + getServer().getOnlinePlayers().size() + " players to reload UI");
    }

    public void notifyHotReload(String screenId) {
        for (Player player : getServer().getOnlinePlayers()) {
            Map<String, String> payload = Map.of("reason", "hotreload", "screenId", screenId);
            BridgeMessage message = new PluginBridgeMessage(
                    MessageType.EVENT_RELOAD_NOTIFICATION, payload, player.getUniqueId());
            eventBridge.sendMessage(message);
        }
        LOGGER.info("[EventUI] Notificados " + getServer().getOnlinePlayers().size()
                + " jugadores del hot reload de: " + screenId);
    }

    public EventMessenger getMessenger() {
        return uiConfigManager.getMessenger();
    }


    public Map<String, UIConfig> getUIConfigs() { return uiConfigs; }
    public UIConfigLoader getUIConfigLoader() { return uiConfigLoader; }
    public static EventUIPlugin getInstance() { return instance; }
    public EventStorage getStorage() { return storage; }
    public EventConfigLoader getConfigLoader() { return configLoader; }
    public PluginEventBridge getEventBridge() { return eventBridge; }
    public ObjectiveTracker getObjectiveTracker() { return objectiveTracker; }
    public UIConfigManager getUIConfigManager() { return uiConfigManager; }
    public UIStateManager getUIStateManager() { return uiStateManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
}
