package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class StaticRecipeRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticRecipeRenderer.class);

    protected final ResourceLocation texture;
    protected final int texWidth;
    protected final int texHeight;

    protected StaticRecipeRenderer(ResourceLocation texture, int texWidth, int texHeight) {
        this.texture = texture;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
    }

    protected void renderBackground(GuiGraphics graphics, int x, int y) {
        LOGGER.info("[FURNACE_DEBUG] renderBackground texture={}", texture);
        graphics.blit(texture, x, y, 0, 0, texWidth, texHeight, texWidth, texHeight);
    }

    protected void renderCyclingItem(GuiGraphics graphics, Font font,
                                     List<ItemStack> items,
                                     int slotX, int slotY) {
        if (items == null || items.isEmpty()) return;
        long time = System.currentTimeMillis() / 1000;
        int idx = (int) (time % items.size());
        ItemStack stack = items.get(idx);
        graphics.renderItem(stack, slotX, slotY);
        if (stack.getCount() > 1) {
            graphics.renderItemDecorations(font, stack, slotX, slotY);
        }
    }

    protected static List<ItemStack> resolveItems(Object raw) {
        if (raw == null) return List.of();

        if (raw instanceof String string) {
            return resolveItemsFromString(string);
        }

        if (raw instanceof CharSequence sequence) {
            return resolveItemsFromString(sequence.toString());
        }

        if (raw instanceof Iterable<?> iterable) {
            List<ItemStack> stacks = new ArrayList<>();
            for (Object entry : iterable) {
                if (entry instanceof String string) {
                    stacks.addAll(resolveItemsFromString(string));
                } else if (entry instanceof CharSequence sequence) {
                    stacks.addAll(resolveItemsFromString(sequence.toString()));
                } else {
                    LOGGER.warn("Unsupported item entry type: {}", entry == null ? "null" : entry.getClass().getName());
                }
            }
            return stacks;
        }

        LOGGER.warn("Unsupported item data type: {}", raw.getClass().getName());
        return List.of();
    }

    private static List<ItemStack> resolveItemsFromString(String value) {
        List<ItemStack> stacks = new ArrayList<>();
        if (value == null || value.isBlank()) return stacks;

        // Keep compatibility with the current tooltip parser, which provides section values as strings.
        // Multiple ids can be supplied as a comma-separated list (e.g. "minecraft:iron_ore,minecraft:raw_iron").
        String[] parts = value.split(",");
        for (String rawId : parts) {
            String itemId = rawId.trim();
            if (itemId.isEmpty()) continue;

            try {
                ResourceLocation location = ResourceLocation.parse(itemId);
                Item item = BuiltInRegistries.ITEM.get(location);
                if (item == null) {
                    LOGGER.warn("Item not found for id: {}", itemId);
                    continue;
                }
                stacks.add(new ItemStack(item));
            } catch (Exception e) {
                LOGGER.warn("Failed to resolve item '{}': {}", itemId, e.getMessage());
            }
        }
        return stacks;
    }

    public abstract int getHeight();

    public abstract int getWidth(Font font);

    public abstract void render(GuiGraphics graphics, Font font,
                                int x, int y,
                                Map<String, ?> data);
}
