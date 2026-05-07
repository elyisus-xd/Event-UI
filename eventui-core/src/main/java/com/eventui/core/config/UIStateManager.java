package com.eventui.core.config;

import com.eventui.api.bridge.MessageType;
import com.eventui.core.EventUIPlugin;
import com.eventui.core.bridge.PluginBridgeMessage;
import com.google.gson.Gson;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class UIStateManager {

    private static final Logger LOGGER = Logger.getLogger(UIStateManager.class.getName());
    private final EventUIPlugin plugin;
    private final Gson gson = new Gson();

    private final Map<UUID, Map<String, String>> playerState = new ConcurrentHashMap<>();

    public UIStateManager(EventUIPlugin plugin) {
        this.plugin = plugin;
    }


        public void setVariable(UUID playerId, String key, String value) {
        playerState.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(key, value);
        savePlayerState(playerId);
        pushStateToClient(playerId, true);

        LOGGER.info("UIState [" + playerId + "] " + key + " = " + value);
    }

        public void setVariables(UUID playerId, Map<String, String> variables) {
        playerState.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .putAll(variables);
        savePlayerState(playerId);
        pushStateToClient(playerId, true);
    }

    public String getVariable(UUID playerId, String key) {
        return playerState.getOrDefault(playerId, Map.of()).get(key);
    }

    public String getVariable(UUID playerId, String key, String defaultValue) {
        return playerState.getOrDefault(playerId, Map.of())
                .getOrDefault(key, defaultValue);
    }

    public Map<String, String> getAllVariables(UUID playerId) {
        return Collections.unmodifiableMap(
                playerState.getOrDefault(playerId, Map.of())
        );
    }

    public void clearVariables(UUID playerId) {
        playerState.remove(playerId);
        savePlayerState(playerId);
        pushStateToClient(playerId, true);
    }

    public void removeVariable(UUID playerId, String key) {
        Map<String, String> state = playerState.get(playerId);
        if (state != null) {
            state.remove(key);
            savePlayerState(playerId);
            pushStateToClient(playerId, true);
        }
    }


    public void loadPlayerState(UUID playerId) {
        File file = getStateFile(playerId);
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> state = new ConcurrentHashMap<>();

        for (String key : yaml.getKeys(false)) {
            state.put(key, yaml.getString(key, ""));
        }

        playerState.put(playerId, state);
        LOGGER.info("Loaded UI state for " + playerId + " (" + state.size() + " variables)");
    }

    private void savePlayerState(UUID playerId) {
        File file = getStateFile(playerId);
        file.getParentFile().mkdirs();

        YamlConfiguration yaml = new YamlConfiguration();
        Map<String, String> state = playerState.getOrDefault(playerId, Map.of());

        state.forEach(yaml::set);

        try {
            yaml.save(file);
        } catch (Exception e) {
            LOGGER.warning("Failed to save UI state for " + playerId + ": " + e.getMessage());
        }
    }

    private File getStateFile(UUID playerId) {
        return new File(plugin.getDataFolder(), "playerstate/" + playerId + ".yml");
    }




        public void pushStateToClient(UUID playerId, boolean replace) {
        var player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        Map<String, String> state = playerState.getOrDefault(playerId, Map.of());
        String variablesJson = gson.toJson(state);

        Map<String, String> payload = new HashMap<>();
        payload.put("variables", variablesJson);
        if (replace) {
            payload.put("replace", "true");        }

        var message = new PluginBridgeMessage(
                MessageType.UI_STATE_UPDATE,
                payload,
                playerId
        );

        plugin.getEventBridge().sendMessage(message);
    }
}
