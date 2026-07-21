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

    protected StaticRecipeRenderer() {
        
    }

    protected void renderBackground(GuiGraphics graphics, int x, int y,
                                   ResourceLocation frameTexture,
                                   int frameWidth, int frameHeight) {
        if (frameTexture == null) return;
        graphics.blit(frameTexture, x, y, 0, 0, frameWidth, frameHeight, frameWidth, frameHeight);
    }

    protected void renderCyclingItem(GuiGraphics graphics, Font font,
                                     List<ItemStack> items,
                                     int slotX, int slotY) {
        if (items == null || items.isEmpty()) return;
        long time = System.currentTimeMillis() / 1000;
        int idx = (int) (time % items.size());
        ItemStack stack = items.get(idx);
        graphics.renderItem(stack, slotX, slotY);
        graphics.renderItemDecorations(font, stack, slotX, slotY);
    }

    protected static List<ItemStack> resolveItems(Object raw) {
        switch (raw) {
            case null -> {
                return List.of();
            }
            case String string -> {
                return resolveItemsFromString(string);
            }
            case CharSequence sequence -> {
                return resolveItemsFromString(sequence.toString());
            }
            case Iterable<?> iterable -> {
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
            default -> {
            }
        }

        LOGGER.warn("Unsupported item data type: {}", raw.getClass().getName());
        return List.of();
    }

    private static List<ItemStack> resolveItemsFromString(String value) {
        List<ItemStack> stacks = new ArrayList<>();
        if (value == null || value.isBlank()) return stacks;

        String[] parts = value.split(",");
        for (String rawId : parts) {
            String itemId = rawId.trim();
            if (itemId.isEmpty()) continue;

            try {
                ResourceLocation location = ResourceLocation.parse(itemId);
                Item item = BuiltInRegistries.ITEM.get(location);
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
