package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FurnaceStaticRenderer extends StaticRecipeRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnaceStaticRenderer.class);
    private static final int INPUT_X  = 8,  INPUT_Y  = 8;
    private static final int FUEL_X   = 8,  FUEL_Y   = 30;
    private static final int RESULT_X = 56, RESULT_Y = 19;

    public FurnaceStaticRenderer() {
        super(ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/furnace.png"), 82, 54);
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
        LOGGER.info("[FURNACE_DEBUG] render() called x={} y={} data={}", x, y, data);
        renderBackground(graphics, x, y);

        var inputItems = resolveItems(data.get("input"));
        LOGGER.info("[FURNACE_DEBUG] resolveItems('{}') -> {} items", data.get("input"), inputItems.size());
        renderCyclingItem(graphics, font, inputItems, x + INPUT_X, y + INPUT_Y);

        if (data.containsKey("fuel")) {
            var fuelItems = resolveItems(data.get("fuel"));
            LOGGER.info("[FURNACE_DEBUG] resolveItems('{}') -> {} items", data.get("fuel"), fuelItems.size());
            renderCyclingItem(graphics, font, fuelItems, x + FUEL_X, y + FUEL_Y);
        }

        var resultItems = resolveItems(data.get("result"));
        LOGGER.info("[FURNACE_DEBUG] resolveItems('{}') -> {} items", data.get("result"), resultItems.size());
        renderCyclingItem(graphics, font, resultItems, x + RESULT_X, y + RESULT_Y);
    }
}
