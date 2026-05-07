package com.eventui.fabric.client.ui;

import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.viewmodel.EventViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QuestTrackerHUD {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestTrackerHUD.class);

    private static boolean enabled = true;
    private static EventViewModel.EventData activeQuest = null;
    private static long lastUpdate = 0;
    private static final long UPDATE_INTERVAL = 500;


    private static float alpha = 0.0f;
    private static final float FADE_SPEED = 0.05f;


    public static void render(GuiGraphics graphics) {
        if (!enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        if (now - lastUpdate > UPDATE_INTERVAL) {
            updateActiveQuest();
            lastUpdate = now;
        }

        if (activeQuest != null) {
            if (alpha < 1.0f) {
                alpha = Math.min(1.0f, alpha + FADE_SPEED);
            }
        } else {
            if (alpha > 0.0f) {
                alpha = Math.max(0.0f, alpha - FADE_SPEED);
            } else {
                return;
            }
        }
        if (activeQuest == null && alpha <= 0.0f) {
            return;
        }

        renderQuestTracker(graphics);
    }

    private static void updateActiveQuest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            activeQuest = null;
            return;
        }

        try {
            EventViewModel viewModel = ClientEventBridge.getInstance()
                    .getOrCreateViewModel(mc.player.getUUID());

            List<EventViewModel.EventData> events = viewModel.getAllEvents();
            EventViewModel.EventData newQuest = events.stream()
                    .filter(e -> e.state == com.eventui.api.event.EventState.IN_PROGRESS)
                    .findFirst()
                    .orElse(null);

            if (activeQuest != newQuest) {
                if (newQuest == null) {
                    LOGGER.info("Quest completed/failed - Starting fade out");
                } else {
                    LOGGER.info("New active quest: {} - {}", newQuest.id, newQuest.displayName);
                }
            }

            activeQuest = newQuest;

        } catch (Exception e) {
            LOGGER.error("Failed to update active quest", e);
            activeQuest = null;
        }
    }

    private static void renderQuestTracker(GuiGraphics graphics) {
        if (activeQuest == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int width = 200;
        int height = 45;
        int x = screenWidth - width - 10;
        int y = 10;
        int alphaHex = (int)(alpha * 255) << 24;
        int topColor = (alphaHex & 0xFF000000) | 0x1A1A1A;
        int bottomColor = (alphaHex & 0xFF000000) | 0x0A0A0A;
        renderGradientBox(graphics, x, y, width, height,
                topColor | (int)(0xEE * alpha) << 24,
                bottomColor | (int)(0xEE * alpha) << 24);

        int borderColor = (alphaHex & 0xFF000000) | 0xFFAA00;
        graphics.fill(x, y, x + width, y + 2, borderColor);


        int shadowColor = (int)(0x66 * alpha) << 24;
        graphics.fill(x + 2, y + height, x + width, y + height + 2, shadowColor);
        graphics.pose().pushPose();


        try {
            if (activeQuest.icon != null) {
                net.minecraft.world.item.ItemStack icon = parseItemStack(activeQuest.icon);
                if (!icon.isEmpty()) {
                    int iconBgColor = (int)(0x88 * alpha) << 24;
                    graphics.fill(x + 4, y + 4, x + 20, y + 20, iconBgColor);
                    graphics.renderItem(icon, x + 5, y + 5);
                }
            }

            String title = truncateText(mc.font, activeQuest.displayName, width - 30);
            int titleColor = 0xFFFFFF | alphaHex;
            graphics.drawString(mc.font, "§e" + title, x + 26, y + 6, titleColor, true);

            float progress = activeQuest.getProgressPercentage();
            String progressShort = String.format("§7[§f%d§7/§f%d§7]",
                    activeQuest.currentProgress,
                    activeQuest.targetProgress);

            int progressWidth = mc.font.width(progressShort);
            int textColor = 0xFFFFFF | alphaHex;
            graphics.drawString(mc.font, progressShort, x + width - progressWidth - 8, y + 6, textColor, true);

            String objective = truncateText(mc.font, activeQuest.currentObjectiveDescription, width - 15);
            graphics.drawString(mc.font, "§7" + objective, x + 6, y + 22, textColor, false);

            String bottomText = String.format("§e%.0f%% §7§o• Press §eK", progress * 100);
            graphics.drawString(mc.font, bottomText, x + 6, y + height - 12, textColor, false);

        } catch (Exception e) {
            LOGGER.error("Error rendering quest tracker", e);
            activeQuest = null;
            alpha = 0.0f;
        }

        graphics.pose().popPose();
    }

    private static void renderGradientBox(GuiGraphics graphics, int x, int y, int width, int height, int colorTop, int colorBottom) {
        int strips = Math.min(height, 20);
        float stripHeight = (float) height / strips;

        for (int i = 0; i < strips; i++) {
            float ratio = (float) i / strips;
            int color = blendColors(colorTop, colorBottom, ratio);

            int stripY = y + (int)(i * stripHeight);
            int nextStripY = y + (int)((i + 1) * stripHeight);

            graphics.fill(x, stripY, x + width, nextStripY, color);
        }
    }

    private static int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int)(a1 + (a2 - a1) * ratio);
        int r = (int)(r1 + (r2 - r1) * ratio);
        int g = (int)(g1 + (g2 - g1) * ratio);
        int b = (int)(b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String truncateText(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;

        String truncated = text;
        while (font.width(truncated + "...") > maxWidth && !truncated.isEmpty()) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "...";
    }

    private static net.minecraft.world.item.ItemStack parseItemStack(String itemId) {
        try {
            if (itemId == null || !itemId.contains(":")) {
                return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
            }

            net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.tryParse(itemId);
            if (location == null) {
                return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
            }

            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location);

            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new net.minecraft.world.item.ItemStack(item);
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to parse item: {}", itemId);
        }

        return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
    }

    public static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            alpha = 0.0f;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            enabled ? "§aQuest Tracker: §eON" : "§cQuest Tracker: §eOFF"
                    ),
                    true
            );
        }
    }

    public static void forceUpdate() {
        lastUpdate = 0;
    }

    public static void resetFade() {
        alpha = 0.0f;
    }
}
