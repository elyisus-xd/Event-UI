package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import com.eventui.fabric.client.ui.tooltip.InventoryHelper;
import com.eventui.fabric.client.ui.tooltip.RecipeGridConfig;
import com.eventui.fabric.client.ui.tooltip.RecipeSlotHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.eventui.fabric.client.ui.tooltip.RecipeSlotHelper.*;

public class ShapedRecipeRenderer implements RecipeRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShapedRecipeRenderer.class);

    private final RecipeGridConfig config;

    public ShapedRecipeRenderer(RecipeGridConfig config) {
        this.config = config;
    }

    @Override
    public int getHeight() { return 3 * SLOT_SIZE; }  // 54

    @Override
    public int getWidth(Font font) {
        // grid(54) + padding(4) + arrow(14) + padding(4) + slot(18) + padding(4) = 98
        return 3 * SLOT_SIZE + PADDING + ARROW_WIDTH + PADDING + SLOT_SIZE + PADDING;
    }

    @Override
    public boolean usesVanillaCraftingFrame() { return true; }

    @Override
    public void renderRecipe(GuiGraphics graphics, Font font, int x, int y, Recipe<?> recipe) {
        if (!(recipe instanceof ShapedRecipe shaped)) return;
        List<Ingredient> ingredients = shaped.getIngredients();
        int width = shaped.getWidth();
        int height = shaped.getHeight();
        long time = System.currentTimeMillis() / 1000;

        // Grid 3x3: draw items only (vanilla frame will be drawn by RecipeTooltipComponent)
        int index = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (index >= ingredients.size()) break;
                Ingredient ingredient = ingredients.get(index);
                int slotX = x + col * SLOT_SIZE;
                int slotY = y + row * SLOT_SIZE;
                ItemStack[] items = ingredient.getItems();
                if (items.length > 0) {
                    graphics.renderItem(items[(int)(time % items.length)], slotX, slotY);
                    if (config.isShowInventoryPreview() && !InventoryHelper.playerHasIngredient(ingredient)) {
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xAA000000);
                    }
                }
                index++;
            }
        }

        // Arrow (centred vertically in the grid)
        int arrowX = x + 3 * SLOT_SIZE + PADDING;
        int arrowY = y + (3 * SLOT_SIZE / 2) - 4;
        RecipeSlotHelper.drawArrow(graphics, font, arrowX, arrowY);

        // Output
        int outputX = arrowX + ARROW_WIDTH + PADDING;
        int outputY = y + (3 * SLOT_SIZE / 2) - 8;
        try {
            var level = Minecraft.getInstance().level;
            var registry = level != null ? level.registryAccess() : null;
            ItemStack result = recipe.getResultItem(registry);
            graphics.renderItem(result, outputX, outputY);
            graphics.renderItemDecorations(font, result, outputX, outputY);
        } catch (Exception ignored) {}
    }
}
