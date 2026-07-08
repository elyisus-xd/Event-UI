package com.eventui.fabric.client.ui.tooltip;

import com.eventui.fabric.client.ui.tooltip.frame.RecipeFrameManager;
import com.eventui.fabric.client.ui.tooltip.renderer.RecipeRenderer;
import com.eventui.fabric.client.ui.tooltip.renderer.RecipeRendererFactory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecipeTooltipComponent implements ClientTooltipComponent {

    private final Recipe<?> recipe;
    private final String customFrame;
    private final RecipeGridConfig gridConfig;
    private final RecipeRenderer renderer;

    private static final Logger LOG = LoggerFactory.getLogger(RecipeTooltipComponent.class);

    public RecipeTooltipComponent(Recipe<?> recipe) {
        this(recipe, null, new RecipeGridConfig());
    }

    public RecipeTooltipComponent(Recipe<?> recipe, String customFrame) {
        this(recipe, customFrame, new RecipeGridConfig());
    }

    public RecipeTooltipComponent(Recipe<?> recipe, String customFrame, RecipeGridConfig config) {
        this.recipe = recipe;
        this.customFrame = customFrame;
        this.gridConfig = config;
        this.renderer = RecipeRendererFactory.create(recipe, config);
    }

    @Override
    public int getHeight() {
        return renderer.getHeight();
    }

    @Override
    public int getWidth(Font font) {
        return renderer.getWidth(font);
    }

    private static final ResourceLocation CRAFTING_TABLE_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        LOG.debug("Rendering recipe tooltip for {}", recipe.getClass().getSimpleName());
        if (renderer.usesVanillaCraftingFrame() && gridConfig.isShowGridFrame()) {
            // Determinar el tipo de frame (shaped vs shapeless)
            String frameType = recipe instanceof ShapedRecipe ? "shaped_frame" : "shapeless_frame";
            
            // Resolver frame: primero custom (si está en YAML), luego default
            ResourceLocation frameTexture = null;
            if (customFrame != null && !customFrame.isEmpty()) {
                try {
                    frameTexture = ResourceLocation.parse(customFrame);
                } catch (Exception e) {
                    LOG.warn("Failed to parse custom frame '{}': {}", customFrame, e.getMessage());
                }
            }
            
            // Si no hay custom o falló, usar RecipeFrameManager
            if (frameTexture == null) {
                frameTexture = RecipeFrameManager.getInstance()
                    .resolveFrameTexture(frameType, null);
            }
            
            if (frameTexture == null) {
                // No hay textura custom ni default configurada: usar frame vanilla como antes
                renderVanillaFrame(graphics, x, y);
            } else {
                renderFrame(graphics, x, y, frameTexture);
            }
        }
        renderer.renderRecipe(graphics, font, x, y, recipe);
    }

    private void renderFrame(GuiGraphics graphics, int x, int y, ResourceLocation frameTexture) {
        try {
            graphics.blit(frameTexture, x, y, 0, 0, gridConfig.getGridFrameWidth(), gridConfig.getGridFrameHeight(), gridConfig.getGridFrameWidth(), gridConfig.getGridFrameHeight());
        } catch (Exception e) {
            LOG.warn("Failed to render frame '{}': {}", frameTexture, e.getMessage());
            // Fallback a vanilla frame si falla
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

    // Keep an inner data record for legacy TooltipComponent usage
    public record Data(Recipe<?> recipe, String customFrame) implements TooltipComponent {}
}
