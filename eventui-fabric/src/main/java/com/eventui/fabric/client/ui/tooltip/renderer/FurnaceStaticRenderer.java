package com.eventui.fabric.client.ui.tooltip.renderer;

import com.eventui.fabric.client.ui.tooltip.frame.RecipeFrameManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FurnaceStaticRenderer extends StaticRecipeRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnaceStaticRenderer.class);
    private static final int INPUT_X  = 1,  INPUT_Y  = 1;
    private static final int FUEL_X   = 1,  FUEL_Y   = 37;
    private static final int RESULT_X = 57, RESULT_Y = 15;
    private static final int TEX_WIDTH = 82, TEX_HEIGHT = 54;

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
        LOGGER.debug("[FURNACE_DEBUG] render() called x={} y={} data={}", x, y, data);
        
        // Resolver frame texture con override si existe en el map
        String customFrameStr = (String) data.get("recipe_frame");
        ResourceLocation customFrame = null;
        if (customFrameStr != null && !customFrameStr.isEmpty()) {
            try {
                customFrame = ResourceLocation.parse(customFrameStr);
            } catch (Exception e) {
                LOGGER.warn("[FURNACE_DEBUG] Failed to parse custom frame: {}", customFrameStr, e);
            }
        }
        
        ResourceLocation frameTexture = RecipeFrameManager.getInstance()
            .resolveFrameTexture("furnace", customFrame);
        
        renderBackground(graphics, x, y, frameTexture, TEX_WIDTH, TEX_HEIGHT);

        var inputItems = resolveItems(data.get("input"));
        LOGGER.debug("[FURNACE_DEBUG] resolveItems('{}') -> {} items", data.get("input"), inputItems.size());
        renderCyclingItem(graphics, font, inputItems, x + INPUT_X, y + INPUT_Y);

        if (data.containsKey("fuel")) {
            var fuelItems = resolveItems(data.get("fuel"));
            LOGGER.debug("[FURNACE_DEBUG] resolveItems('{}') -> {} items", data.get("fuel"), fuelItems.size());
            renderCyclingItem(graphics, font, fuelItems, x + FUEL_X, y + FUEL_Y);
        }

        var resultItems = resolveItems(data.get("result"));
        LOGGER.debug("[FURNACE_DEBUG] resolveItems('{}') -> {} items", data.get("result"), resultItems.size());
        renderCyclingItem(graphics, font, resultItems, x + RESULT_X, y + RESULT_Y);
    }
}
