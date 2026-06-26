package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.Recipe;

public interface RecipeRenderer {

    int getHeight();
    int getWidth(Font font);
    void renderRecipe(GuiGraphics graphics, Font font, int x, int y, Recipe<?> recipe);

    default boolean usesVanillaCraftingFrame() { return false; }
}
