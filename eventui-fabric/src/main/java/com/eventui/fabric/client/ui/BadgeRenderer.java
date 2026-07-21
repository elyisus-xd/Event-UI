package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIBadge;
import com.eventui.api.ui.UIElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.eventui.fabric.client.bridge.BridgeMessageImpl;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.api.bridge.MessageType;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BadgeRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeRenderer.class);
    private static final Set<String> dismissedBadges = ConcurrentHashMap.newKeySet();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "eventui-badge-scheduler");
        t.setDaemon(true);
        return t;
    });

    private static final java.util.Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    public static void renderBadge(UIElement element, UIBadge badge, GuiGraphics graphics,
                                   int elementX, int elementY, Map<String, Object> context, String screenId) {
        if (badge == null || !badge.isEnabled()) {
            return;
        }

        String badgeKey = generateBadgeKey(element, screenId);
        if (dismissedBadges.contains(badgeKey)) {
            return;
        }

        if (badge.getCondition() != null && !badge.getCondition().isEmpty()) {
            String resolvedCondition = DataBinder.resolveBindings(badge.getCondition(), context);
            if ("false".equalsIgnoreCase(resolvedCondition) || resolvedCondition.isEmpty()) {
                return;
            }
        }

        String disappearOn = element.getProperties().get("badge_disappear_on");
        if (disappearOn != null && disappearOn.startsWith("timer:")) {
            String badgeKeyForTimer = generateBadgeKey(element, screenId);
            if (!dismissedBadges.contains(badgeKeyForTimer) && !scheduledTasks.containsKey(badgeKeyForTimer)) {
                try {
                    int seconds = Integer.parseInt(disappearOn.substring("timer:".length()).trim());
                    if (seconds > 0) {
                        ScheduledFuture<?> future = SCHEDULER.schedule(() -> {
                            try {
                                Minecraft.getInstance().execute(() -> dismissBadge(element, screenId));
                            } catch (Exception e) {
                                LOGGER.debug("Timer dismissal task failed for {}: {}", badgeKeyForTimer, e.getMessage());
                            }
                        }, seconds, TimeUnit.SECONDS);
                        scheduledTasks.put(badgeKeyForTimer, future);
                        LOGGER.debug("Scheduled timer dismiss for {} in {}s", badgeKeyForTimer, seconds);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid timer value for badge: {}", disappearOn);
                }
            }
        }

        int badgeX = elementX + badge.getXOffset();
        int badgeY = elementY + badge.getYOffset();
        try {
            ResourceLocation badgeTexture = ResourceLocation.parse(badge.getTexture());

            graphics.blit(
                    badgeTexture,
                    badgeX, badgeY,
                    0, 0,
                    badge.getWidth(), badge.getHeight(),
                    badge.getWidth(), badge.getHeight()
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to render badge texture: {}", badge.getTexture(), e);
        }
    }

    public static void handleBadgeInteraction(UIElement element, UIBadge badge,
                                              InteractionType interaction, String screenId) {
        if (badge == null || !badge.isEnabled()) {
            return;
        }
        String disappearOn = element.getProperties().get("badge_disappear_on");
        if (disappearOn == null || disappearOn.isEmpty()) {
            return;
        }

        boolean shouldDismiss = false;

        switch (disappearOn.toLowerCase()) {
            case "click":
                shouldDismiss = (interaction == InteractionType.CLICK);
                break;
            case "mouse_hover":
                shouldDismiss = (interaction == InteractionType.HOVER);
                break;
            case "ui_open":
                shouldDismiss = (interaction == InteractionType.UI_OPEN);
                break;
            default:
                if (disappearOn.startsWith("timer:")) {
                    
                    try {
                        int seconds = Integer.parseInt(disappearOn.substring("timer:".length()).trim());
                        scheduleDismiss(element, screenId, seconds);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Invalid timer value for badge: {}", disappearOn);
                    }
                    return;
                }
                LOGGER.warn("Unknown disappear_on value: {}", disappearOn);
        }

        if (shouldDismiss) {
            dismissBadge(element, screenId);
        }
    }

    public static void dismissBadge(UIElement element, String screenId) {
        String badgeKey = generateBadgeKey(element, screenId);
        dismissedBadges.add(badgeKey);
        LOGGER.debug("Badge dismissed: {}", badgeKey);

        ScheduledFuture<?> f = scheduledTasks.remove(badgeKey);
        if (f != null) {
            f.cancel(false);
            LOGGER.debug("Cancelled scheduled dismissal for {}", badgeKey);
        }

        try {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                java.util.Map<String, String> payload = java.util.Map.of(
                        "screen_id", screenId == null ? "" : screenId,
                        "element_id", element.getId()
                );
                BridgeMessageImpl message = new BridgeMessageImpl(MessageType.BADGE_DISMISS, payload, player.getUUID());
                ClientEventBridge.getInstance().sendMessage(message);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to notify server about dismissed badge: {}", e.getMessage());
        }
    }

    public static void replaceDismissedFromServer(java.util.Set<String> keys) {
        
        for (String k : keys) {
            ScheduledFuture<?> f = scheduledTasks.remove(k);
            if (f != null) {
                f.cancel(false);
                LOGGER.debug("Cancelled scheduled dismissal due to server replacement for {}", k);
            }
        }
        dismissedBadges.clear();
        dismissedBadges.addAll(keys);
        LOGGER.debug("Replaced dismissed badges from server: {}", keys.size());
    }

    public static void clearDismissedBadges() {
        dismissedBadges.clear();
        LOGGER.info("Cleared all dismissed badges");
    }

    private static String generateBadgeKey(UIElement element, String screenId) {
        if (screenId == null || screenId.isEmpty()) screenId = "unknown";
        return screenId + ":" + element.getId();
    }

    private static void scheduleDismiss(UIElement element, String screenId, int seconds) {
        if (seconds <= 0) return;
        String badgeKey = generateBadgeKey(element, screenId);
        if (dismissedBadges.contains(badgeKey) || scheduledTasks.containsKey(badgeKey)) return;
        ScheduledFuture<?> future = SCHEDULER.schedule(() -> {
            try {
                Minecraft.getInstance().execute(() -> dismissBadge(element, screenId));
            } catch (Exception e) {
                LOGGER.debug("Timer dismissal task failed for {}: {}", badgeKey, e.getMessage());
            }
        }, seconds, TimeUnit.SECONDS);
        scheduledTasks.put(badgeKey, future);
        LOGGER.debug("Scheduled timer dismiss for {} in {}s", badgeKey, seconds);
    }

    public enum InteractionType {
        CLICK,
        HOVER,
        UI_OPEN
    }
}
