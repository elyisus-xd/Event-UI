package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class SmithingStaticRenderer extends StaticRecipeRenderer {
    private static final int TEMPLATE_X = 4,  TEMPLATE_Y = 20;
    private static final int BASE_X     = 26, BASE_Y     = 20;
    private static final int ADDITION_X = 48, ADDITION_Y = 20;
    private static final int RESULT_X   = 82, RESULT_Y   = 20;

    public SmithingStaticRenderer() {
        super(ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/smithing.png"), 108, 58);
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
        renderCyclingItem(graphics, font, resolveItems(data.get("template")), x + TEMPLATE_X, y + TEMPLATE_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("base")), x + BASE_X, y + BASE_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("addition")), x + ADDITION_X, y + ADDITION_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + RESULT_X, y + RESULT_Y);
    }
}
