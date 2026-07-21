package com.eventui.fabric.client.ui.tooltip.renderer;

import com.eventui.fabric.client.ui.tooltip.frame.RecipeFrameManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class BrewingStaticRenderer extends StaticRecipeRenderer {
    private static final int INGREDIENT_X = 24, INGREDIENT_Y = 1;
    private static final int BOTTLE1_X    = 1,  BOTTLE1_Y    = 35;
    private static final int BOTTLE2_X    = 24, BOTTLE2_Y    = 35;
    private static final int BOTTLE3_X    = 47, BOTTLE3_Y    = 35;
    private static final int TEX_WIDTH = 64, TEX_HEIGHT = 59;

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
            .resolveFrameTexture("brewing", customFrame);
        
        renderBackground(graphics, x, y, frameTexture, TEX_WIDTH, TEX_HEIGHT);
        renderCyclingItem(graphics, font, resolveItems(data.get("ingredient")), x + INGREDIENT_X, y + INGREDIENT_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + BOTTLE1_X, y + BOTTLE1_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + BOTTLE2_X, y + BOTTLE2_Y);
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + BOTTLE3_X, y + BOTTLE3_Y);
    }
}
