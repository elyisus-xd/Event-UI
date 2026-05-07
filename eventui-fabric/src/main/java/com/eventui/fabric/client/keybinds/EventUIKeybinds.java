package com.eventui.fabric.client.keybinds;

import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.ui.EventScreen;
import com.eventui.fabric.client.ui.QuestTrackerHUD;
import com.eventui.fabric.client.ui.custom.CustomEventScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EventUIKeybinds {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventUIKeybinds.class);

    private static KeyMapping openEventsKey;
    private static KeyMapping toggleTrackerKey;
    private static String cachedUIMode = null;
    private static String cachedScreenId = null;

    public static void register() {
        LOGGER.info("Registering EventUI keybinds...");
        openEventsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.eventui.openevents",
                GLFW.GLFW_KEY_K,
                "category.eventui"
        ));

        toggleTrackerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.eventui.toggle_tracker",
                GLFW.GLFW_KEY_H,
                "category.eventui"
        ));

        LOGGER.info("Keybind objects created: openEventsKey={}, toggleTrackerKey={}",
                openEventsKey, toggleTrackerKey);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEventsKey.consumeClick()) {
                handleOpenEvents(client);
            }
            while (toggleTrackerKey.consumeClick()) {
                handleToggleTracker(client);
            }
        });

        LOGGER.info("EventUI Keybinds registered successfully!");
        LOGGER.info("  - K: Open Events Screen");
        LOGGER.info("  - H: Toggle Quest Tracker");
    }

    private static void handleOpenEvents(Minecraft client) {
        if (client.player == null) return;
        if (cachedUIMode != null) {
            openScreen(client, cachedUIMode, cachedScreenId);
            return;
        }
        LOGGER.info("Requesting UI mode from server...");
        ClientEventBridge bridge = ClientEventBridge.getInstance();
        bridge.requestUIMode().thenAccept(response -> {
            cachedUIMode = response.get("mode");
            cachedScreenId = response.get("screenId");

            LOGGER.info("Received UI mode: {}", cachedUIMode);
            if (cachedScreenId != null && !cachedScreenId.isEmpty()) {
                LOGGER.info("Custom screen ID: {}", cachedScreenId);
            }

            client.execute(() -> openScreen(client, cachedUIMode, cachedScreenId));

        }).exceptionally(error -> {
            LOGGER.error("Failed to get UI mode from server", error);
            client.execute(() -> {
                LOGGER.warn("Using hardcoded UI as fallback");
                client.setScreen(new EventScreen());
            });

            return null;
        });
    }

    private static void openScreen(Minecraft client, String mode, String screenId) {
        Screen screen;

        if ("custom".equals(mode)) {
            if (screenId == null || screenId.isEmpty()) {
                LOGGER.error("Custom mode selected but no screenId provided!");
                LOGGER.warn("Falling back to hardcoded UI");
                screen = new EventScreen();
            } else {
                LOGGER.info("Opening custom UI: {}", screenId);
                screen = new CustomEventScreen(screenId, new java.util.Stack<>());
            }
        } else {
            LOGGER.info("Opening hardcoded UI");
            screen = new EventScreen();
        }

        client.setScreen(screen);
        QuestTrackerHUD.forceUpdate();
    }


    private static void handleToggleTracker(Minecraft client) {
        if (client.player != null) {
            QuestTrackerHUD.toggle();
        }
    }


    public static void invalidateCache() {
        cachedUIMode = null;
        cachedScreenId = null;
        LOGGER.info("UI mode cache invalidated - next open will request from server");
    }

    public static void forceHardcodedMode() {
        cachedUIMode = "hardcoded";
        cachedScreenId = null;
        LOGGER.warn("UI mode forced to hardcoded");
    }

    public static void forceCustomMode(String screenId) {
        cachedUIMode = "custom";
        cachedScreenId = screenId;
        LOGGER.warn("UI mode forced to custom: {}", screenId);
    }
}
