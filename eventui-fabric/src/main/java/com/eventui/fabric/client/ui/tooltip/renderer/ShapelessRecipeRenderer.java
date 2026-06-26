package com.eventui.fabric.client.ui.tooltip.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.eventui.fabric.client.ui.tooltip.InventoryHelper;
import com.eventui.fabric.client.ui.tooltip.RecipeGridConfig;
import com.eventui.fabric.client.ui.tooltip.RecipeSlotHelper;

import java.util.List;

import static com.eventui.fabric.client.ui.tooltip.RecipeSlotHelper.*;

public class ShapelessRecipeRenderer implements RecipeRenderer {

    private final RecipeGridConfig config;

    public ShapelessRecipeRenderer(RecipeGridConfig config) {
        this.config = config;
    }

    @Override
    public boolean usesVanillaCraftingFrame() { return true; }

    @Override
    public int getHeight() { return 3 * SLOT_SIZE; }  // 54

    @Override
    public int getWidth(Font font) {
        return 3 * SLOT_SIZE + PADDING + ARROW_WIDTH + PADDING + SLOT_SIZE + PADDING;
    }

    @Override
    public void renderRecipe(GuiGraphics graphics, Font font, int x, int y, Recipe<?> recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        long time = System.currentTimeMillis() / 1000;

        // Fill grid linearly (col = i%3, row = i/3) - draw items only (frame handled by tooltip)
        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
            Ingredient ingredient = ingredients.get(i);
            int col = i % 3;
            int row = i / 3;
            int slotX = x + col * SLOT_SIZE;
            int slotY = y + row * SLOT_SIZE;
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0) {
                graphics.renderItem(items[(int)(time % items.length)], slotX, slotY);
                if (config.isShowInventoryPreview() && !InventoryHelper.playerHasIngredient(ingredient)) {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xAA000000);
                }
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
