package com.eventui.fabric.client.ui.tooltip.renderer;

import com.eventui.fabric.client.ui.tooltip.frame.RecipeFrameManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class AnvilStaticRenderer extends StaticRecipeRenderer {
    private static final int INPUT1_X  = 1,  INPUT1_Y  = 39;
    private static final int INPUT2_X  = 50, INPUT2_Y  = 39;
    private static final int RESULT_X  = 108, RESULT_Y  = 39;
    private static final int TEX_WIDTH = 125, TEX_HEIGHT = 56;

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
        // Resolver frame texture con override si existe
        String customFrameStr = (String) data.get("recipe_frame");
        ResourceLocation customFrame = null;
        if (customFrameStr != null && !customFrameStr.isEmpty()) {
            try {
                customFrame = ResourceLocation.parse(customFrameStr);
            } catch (Exception ignored) {}
        }
        
        ResourceLocation frameTexture = RecipeFrameManager.getInstance()
            .resolveFrameTexture("anvil", customFrame);
        
        renderBackground(graphics, x, y, frameTexture, TEX_WIDTH, TEX_HEIGHT);
        renderCyclingItem(graphics, font, resolveItems(data.get("input1")), x + INPUT1_X, y + INPUT1_Y);
        if (data.containsKey("input2")) {
            renderCyclingItem(graphics, font, resolveItems(data.get("input2")), x + INPUT2_X, y + INPUT2_Y);
        }
        renderCyclingItem(graphics, font, resolveItems(data.get("result")), x + RESULT_X, y + RESULT_Y);
    }
}
