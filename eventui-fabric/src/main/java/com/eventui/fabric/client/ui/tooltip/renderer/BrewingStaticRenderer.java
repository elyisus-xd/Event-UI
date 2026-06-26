package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class BrewingStaticRenderer extends StaticRecipeRenderer {
    private static final int INGREDIENT_X = 23, INGREDIENT_Y = 4;
    private static final int BOTTLE1_X    = 4,  BOTTLE1_Y    = 36;
    private static final int BOTTLE2_X    = 23, BOTTLE2_Y    = 36;
    private static final int BOTTLE3_X    = 42, BOTTLE3_Y    = 36;

    public BrewingStaticRenderer() {
        super(ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/brewing.png"), 64, 59);
    }

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
        renderBackground(graphics, x, y);
        renderCyclingItem(graphics, font, resolveItems(data.get("ingredient")), x + INGREDIENT_X, y + INGREDIENT_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + BOTTLE1_X, y + BOTTLE1_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + BOTTLE2_X, y + BOTTLE2_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + BOTTLE3_X, y + BOTTLE3_Y);
    }
}
