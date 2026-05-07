package com.eventui.fabric.client.ui.custom;

import com.eventui.api.ui.UIConfig;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.ui.ConfigurableUIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Stack;

public class CustomEventScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomEventScreen.class);
    private Stack<String> navigationStack;
    private final String screenId;
    private UIConfig uiConfig;
    private boolean loading = true;
    private String errorMessage = null;

    public CustomEventScreen(String screenId, Stack<String> navigationStack) {
        super(Component.literal("Loading..."));
        this.screenId = screenId;
        this.navigationStack = navigationStack != null ? navigationStack : new Stack<>();

        LOGGER.info("CustomEventScreen created for: {}", screenId);
    }

    public Stack<String> getNavigationStack() {
        return navigationStack;
    }

    @Override
    protected void init() {
        super.init();
        LOGGER.info("CustomEventScreen initialized, requesting UI config from server...");

        ClientEventBridge.getInstance()
                .requestUIConfig(screenId)
                .thenAccept(config -> {
                    Minecraft.getInstance().execute(() -> {
                        this.uiConfig = config;
                        this.loading = false;

                        LOGGER.info("✓ UIConfig received, opening ConfigurableUIScreen");

                        Minecraft.getInstance().setScreen(new ConfigurableUIScreen(config));
                    });
                })
                .exceptionally(error -> {
                    LOGGER.error("Failed to load UI config for: " + screenId, error);

                    Minecraft.getInstance().execute(() -> {
                        this.loading = false;
                        this.errorMessage = error.getMessage();
                    });

                    return null;
                });
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (loading) {
            graphics.drawCenteredString(this.font,
                    Component.literal("§eLoading UI: §f" + screenId),
                    centerX, centerY - 10, 0xFFFFFF);

            graphics.drawCenteredString(this.font,
                    Component.literal("§7Please wait..."),
                    centerX, centerY + 10, 0xAAAAAA);

        } else if (errorMessage != null) {
            graphics.drawCenteredString(this.font,
                    Component.literal("§cFailed to load UI"),
                    centerX, centerY - 20, 0xFF5555);

            graphics.drawCenteredString(this.font,
                    Component.literal("§7" + errorMessage),
                    centerX, centerY, 0x888888);

            graphics.drawCenteredString(this.font,
                    Component.literal("§8Press ESC to close"),
                    centerX, centerY + 30, 0x666666);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
