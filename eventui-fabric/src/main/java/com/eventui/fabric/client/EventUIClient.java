package com.eventui.fabric.client;

import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.bridge.NetworkHandler;
import com.eventui.fabric.client.keybinds.EventUIKeybinds;
import com.eventui.fabric.client.ui.EntityRenderCache;
import com.eventui.fabric.client.ui.HUDElementFactory;
import com.eventui.fabric.client.ui.NotificationSystem;
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
        try {
            NetworkHandler.registerPayloadType();
            ClientEventBridge.getInstance();
            EventUIKeybinds.register();
            HudRenderCallback.EVENT.register((graphics, tickDelta) -> QuestTrackerHUD.render(graphics));

            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                EventUIKeybinds.invalidateCache();
                ClientEventBridge bridge = ClientEventBridge.getInstance();
                bridge.onConnect();
                bridge.requestUIState();
            });

            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                EventUIKeybinds.invalidateCache();
                ClientEventBridge.getInstance().onDisconnect();
                QuestTrackerHUD.reset();
                NotificationSystem.getInstance().clear();
                EntityRenderCache.clear();
                HUDElementFactory.clearStaticCache();
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
                    com.eventui.fabric.client.ui.TextureAlphaCache.clear();
                    EntityRenderCache.clear();
                }
            });

        } catch (Exception e) {
            LOGGER.error("ERROR during initialization!", e);
        }
    }
}
