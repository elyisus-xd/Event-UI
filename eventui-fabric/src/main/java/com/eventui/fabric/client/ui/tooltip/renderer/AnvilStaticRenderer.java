package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class AnvilStaticRenderer extends StaticRecipeRenderer {
    private static final int INPUT1_X  = 8,  INPUT1_Y  = 20;
    private static final int INPUT2_X  = 44, INPUT2_Y  = 20;
    private static final int RESULT_X  = 98, RESULT_Y  = 20;

    public AnvilStaticRenderer() {
        super(ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/anvil.png"), 125, 56);
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
        renderCyclingItem(graphics, font, resolveItems(data.get("input1")), x + INPUT1_X, y + INPUT1_Y);
        if (data.containsKey("input2")) {
            renderCyclingItem(graphics, font, resolveItems(data.get("input2")), x + INPUT2_X, y + INPUT2_Y);
        }
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + RESULT_X, y + RESULT_Y);
    }
}
