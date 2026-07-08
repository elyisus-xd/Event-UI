package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class CustomRecipeStaticRenderer extends StaticRecipeRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomRecipeStaticRenderer.class);

    private int texWidth = 64;
    private int texHeight = 64;

    @Override
    public int getHeight() {
        return texHeight;
    }

    @Override
    public int getWidth(Font font) {
        return texWidth;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, Map<String, ?> data) {
        // 1. Resolver dimensiones
        texWidth = parseIntSafe(data.get("texture_width"), 64);
        texHeight = parseIntSafe(data.get("texture_height"), 64);

        // 2. Resolver textura
        String textureStr = (String) data.get("texture");
        ResourceLocation texture = null;
        if (textureStr != null && !textureStr.isBlank()) {
            try {
                texture = ResourceLocation.parse(textureStr.trim());
            } catch (Exception e) {
                LOGGER.warn("[CUSTOM_RECIPE] Invalid texture '{}': {}", textureStr, e.getMessage());
            }
        }

        // 3. Dibujar fondo
        renderBackground(graphics, x, y, texture, texWidth, texHeight);

        // 4. Parsear y renderizar slots
        String slotsStr = (String) data.get("slots");
        if (slotsStr == null || slotsStr.isBlank()) {
            LOGGER.warn("[CUSTOM_RECIPE] No slots defined");
            return;
        }

        String[] slotDefs = slotsStr.split("\\|");
        for (int i = 0; i < slotDefs.length; i++) {
            String slotDef = slotDefs[i].trim();
            // Format: "role@slotX,slotY"
            String[] parts = slotDef.split("@");
            if (parts.length != 2) {
                LOGGER.warn("[CUSTOM_RECIPE] Invalid slot def: {}", slotDef);
                continue;
            }

            String[] coords = parts[1].split(",");
            if (coords.length != 2) {
                LOGGER.warn("[CUSTOM_RECIPE] Invalid slot coords: {}", parts[1]);
                continue;
            }

            int slotX, slotY;
            try {
                slotX = Integer.parseInt(coords[0].trim());
                slotY = Integer.parseInt(coords[1].trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("[CUSTOM_RECIPE] Non-integer coords in slot {}: {}", i, parts[1]);
                continue;
            }

            // Obtener items para este slot (section_1_slot_0, slot_1, etc.)
            Object itemData = data.get("slot_" + i);
            List<ItemStack> items = resolveItems(itemData);

            if (!items.isEmpty()) {
                renderCyclingItem(graphics, font, items, x + slotX, y + slotY);
            }
        }
    }

    private static int parseIntSafe(Object raw, int def) {
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (Exception e) {
            return def;
        }
    }
}
