package com.eventui.fabric.client.ui.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.List;


public record RecipeTooltipComponent(Recipe<?> recipe, String customFrame) implements ClientTooltipComponent {

    private static final ResourceLocation CRAFTING_TABLE_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    public RecipeTooltipComponent(Recipe<?> recipe) {
        this(recipe, null);
    }

    @Override
    public int getHeight() {
        return 76;
    }

    @Override
    public int getWidth(Font font) {
        return 130;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (customFrame != null && !customFrame.isEmpty()) {
            renderCustomFrame(graphics, x, y);
        } else {
            renderVanillaFrame(graphics, x, y);
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            renderShapedRecipe(shapedRecipe, graphics, x, y);
        } else {
            renderShapelessRecipe(graphics, x, y);
        }
        renderArrowAndOutput(graphics, font, x, y);
    }

    private void renderCustomFrame(GuiGraphics graphics, int x, int y) {
        try {
            ResourceLocation frameLoc = ResourceLocation.parse(customFrame);

            int frameWidth = 64;
            int frameHeight = 64;

            int frameX = x - 5;
            int frameY = y - 5;

            graphics.blit(
                    frameLoc,
                    frameX, frameY,
                    0, 0,
                    frameWidth, frameHeight,
                    frameWidth, frameHeight
            );

        } catch (Exception e) {
            renderVanillaFrame(graphics, x, y);
        }
    }

    private void renderVanillaFrame(GuiGraphics graphics, int x, int y) {
        graphics.blit(
                CRAFTING_TABLE_LOCATION,
                x, y,
                29, 16,
                54, 54,
                256, 256
        );
    }

    private void renderArrowAndOutput(GuiGraphics graphics, Font font, int x, int y) {
        int arrowX = x + 61;
        int arrowY = y + 20;
        graphics.drawString(font, "§f→", arrowX, arrowY, 0xFFFFFF, false);

        int outputSlotX = x + 95;
        int outputSlotY = y + 18;

        graphics.fill(outputSlotX - 1, outputSlotY - 1, outputSlotX + 17, outputSlotY + 17, 0xFF8B8B8B);
        graphics.fill(outputSlotX, outputSlotY, outputSlotX + 16, outputSlotY + 16, 0xFF373737);

        ItemStack result = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        graphics.renderItem(result, outputSlotX, outputSlotY);
        graphics.renderItemDecorations(font, result, outputSlotX, outputSlotY);
    }

    private void renderShapedRecipe(ShapedRecipe recipe, GuiGraphics graphics, int x, int y) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe.getWidth();
        int height = recipe.getHeight();

        int index = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (index >= ingredients.size()) break;

                Ingredient ingredient = ingredients.get(index);
                ItemStack[] items = ingredient.getItems();

                if (items.length > 0) {
                    int slotX = x + 1 + col * 18;
                    int slotY = y + 1 + row * 18;

                    long time = System.currentTimeMillis() / 1000;
                    int itemIndex = (int)(time % items.length);

                    graphics.renderItem(items[itemIndex], slotX, slotY);
                }
                index++;
            }
        }
    }

    private void renderShapelessRecipe(GuiGraphics graphics, int x, int y) {
        List<Ingredient> ingredients = recipe.getIngredients();

        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
            Ingredient ingredient = ingredients.get(i);
            ItemStack[] items = ingredient.getItems();

            if (items.length > 0) {
                int col = i % 3;
                int row = i / 3;
                int slotX = x + 1 + col * 18;
                int slotY = y + 1 + row * 18;

                long time = System.currentTimeMillis() / 1000;
                int itemIndex = (int)(time % items.length);

                graphics.renderItem(items[itemIndex], slotX, slotY);
            }
        }
    }

    public record Data(Recipe<?> recipe, String customFrame) implements TooltipComponent {}
}
