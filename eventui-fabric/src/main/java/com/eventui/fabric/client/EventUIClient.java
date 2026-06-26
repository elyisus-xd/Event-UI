package com.eventui.fabric.client;

import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.bridge.NetworkHandler;
import com.eventui.fabric.client.keybinds.EventUIKeybinds;
import com.eventui.fabric.client.ui.EntityRenderCache;
import com.eventui.fabric.client.ui.QuestTrackerHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventUIClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("EventUI-Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing EventUI client...");

        try {
            NetworkHandler.registerPayloadType();
            ClientEventBridge.getInstance();
            LOGGER.info("Registering keybinds...");
            EventUIKeybinds.register();
            LOGGER.info("Registering Quest Tracker HUD...");
            HudRenderCallback.EVENT.register((graphics, tickDelta) -> QuestTrackerHUD.render(graphics));

            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                LOGGER.info("[JOIN_DEBUG] === JOIN EVENT FIRED === Thread: {}", Thread.currentThread().getName());
                EventUIKeybinds.invalidateCache();
                LOGGER.info("[JOIN_DEBUG] invalidateCache() done, calling onConnect/requestUIState");
                ClientEventBridge bridge = ClientEventBridge.getInstance();
                bridge.onConnect();
                bridge.requestUIState();
            });

            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                LOGGER.info("[DISCONNECT_DEBUG] === DISCONNECT EVENT FIRED === Thread: {}", Thread.currentThread().getName());
                EventUIKeybinds.invalidateCache();
                LOGGER.info("[DISCONNECT_DEBUG] invalidateCache() done, calling onDisconnect");
                ClientEventBridge.getInstance().onDisconnect();
            });

            net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(
                    net.minecraft.server.packs.PackType.CLIENT_RESOURCES
            ).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {

                @Override
                public ResourceLocation getFabricId() {
                    return ResourceLocation.fromNamespaceAndPath("eventui", "texture_alpha_cache");
                }

                @Override
                public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
                    LOGGER.info("Clearing texture alpha cache...");
                    com.eventui.fabric.client.ui.TextureAlphaCache.clear();
                    EntityRenderCache.clear();
                }
            });

        } catch (Exception e) {
            LOGGER.error("ERROR during initialization!", e);
        }

        LOGGER.info("EventUI client initialized!");
    }
}
