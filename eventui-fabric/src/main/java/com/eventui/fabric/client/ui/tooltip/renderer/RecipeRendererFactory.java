package com.eventui.fabric.client.ui.tooltip.renderer;

import com.eventui.fabric.client.ui.tooltip.RecipeGridConfig;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public class RecipeRendererFactory {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RecipeRendererFactory.class);

    public static RecipeRenderer create(Recipe<?> recipe, RecipeGridConfig config) {
        if (recipe == null) {
            LOG.warn("Received null recipe to create renderer");
            return new ShapelessRecipeRenderer(config);
        }

        String recipeName = recipe.getClass().getSimpleName();

        if (recipe instanceof ShapedRecipe) {
            LOG.debug("Using ShapedRecipeRenderer for {}", recipeName);
            return new ShapedRecipeRenderer(config);
        } else if (recipe instanceof ShapelessRecipe) {
            LOG.debug("Using ShapelessRecipeRenderer for {}", recipeName);
            return new ShapelessRecipeRenderer(config);
        }

        try {
            if (recipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING) {
                LOG.debug("Fallback to ShapelessRecipeRenderer for {}", recipeName);
                return new ShapelessRecipeRenderer(config);
            }
        } catch (Exception ignored) {}

        LOG.debug("Fallback to ShapelessRecipeRenderer for {}", recipeName);
        return new ShapelessRecipeRenderer(config);
    }
}
