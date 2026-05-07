package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIBadge;
import com.eventui.api.ui.UIElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BadgeRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeRenderer.class);
    private static final Set<String> dismissedBadges = ConcurrentHashMap.newKeySet();
    public static void renderBadge(UIElement element, UIBadge badge, GuiGraphics graphics,
                                   int elementX, int elementY, Map<String, Object> context) {
        if (badge == null || !badge.isEnabled()) {
            return;
        }

        String badgeKey = generateBadgeKey(element);
        if (dismissedBadges.contains(badgeKey)) {
            return;
        }

        if (badge.getCondition() != null && !badge.getCondition().isEmpty()) {
            String resolvedCondition = DataBinder.resolveBindings(badge.getCondition(), context);
            if ("false".equalsIgnoreCase(resolvedCondition) || resolvedCondition.isEmpty()) {
                return;
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
                                              InteractionType interaction) {
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
                    LOGGER.warn("Timer-based badges not implemented yet: {}", disappearOn);
                    return;
                }
                LOGGER.warn("Unknown disappear_on value: {}", disappearOn);
        }

        if (shouldDismiss) {
            dismissBadge(element);
        }
    }

    public static void dismissBadge(UIElement element) {
        String badgeKey = generateBadgeKey(element);
        dismissedBadges.add(badgeKey);
        LOGGER.debug("Badge dismissed: {}", badgeKey);
    }

    public static void clearDismissedBadges() {
        dismissedBadges.clear();
        LOGGER.info("Cleared all dismissed badges");
    }

    private static String generateBadgeKey(UIElement element) {
        return "unknown:" + element.getId();
    }

    public enum InteractionType {
        CLICK,
        HOVER,
        UI_OPEN
    }
}
