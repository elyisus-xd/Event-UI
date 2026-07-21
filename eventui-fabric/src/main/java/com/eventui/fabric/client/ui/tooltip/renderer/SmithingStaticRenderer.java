package com.eventui.fabric.client.ui.tooltip.renderer;

import com.eventui.fabric.client.ui.tooltip.frame.RecipeFrameManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class SmithingStaticRenderer extends StaticRecipeRenderer {
    private static final int TEMPLATE_X = 1,  TEMPLATE_Y = 41;
    private static final int BASE_X     = 19, BASE_Y     = 41;
    private static final int ADDITION_X = 37, ADDITION_Y = 41;
    private static final int RESULT_X   = 91, RESULT_Y   = 41;
    private static final int TEX_WIDTH = 108, TEX_HEIGHT = 58;

    @Override
    public int getHeight() {
        return TEX_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return TEX_WIDTH;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, Map<String, ?> data) {
        
        String customFrameStr = (String) data.get("recipe_frame");
        ResourceLocation customFrame = null;
        if (customFrameStr != null && !customFrameStr.isEmpty()) {
            try {
                customFrame = ResourceLocation.parse(customFrameStr);
            } catch (Exception ignored) {}
        }
        
        ResourceLocation frameTexture = RecipeFrameManager.getInstance()
            .resolveFrameTexture("smithing", customFrame);
        
        renderBackground(graphics, x, y, frameTexture, TEX_WIDTH, TEX_HEIGHT);
        renderCyclingItem(graphics, font, resolveItems(data.get("template")), x + TEMPLATE_X, y + TEMPLATE_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("base")), x + BASE_X, y + BASE_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("addition")), x + ADDITION_X, y + ADDITION_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + RESULT_X, y + RESULT_Y);
    }
}
