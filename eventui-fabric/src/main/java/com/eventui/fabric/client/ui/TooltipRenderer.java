package com.eventui.fabric.client.ui;

import com.eventui.api.ui.TooltipConfig;
import com.eventui.fabric.client.ui.tooltip.EntityTooltipComponent;
import com.eventui.fabric.client.ui.tooltip.RecipeCache;
import com.eventui.fabric.client.ui.tooltip.RecipeTooltipComponent;
import com.eventui.fabric.client.ui.tooltip.RecipeGridConfig;
import com.eventui.fabric.client.ui.tooltip.renderer.AnvilStaticRenderer;
import com.eventui.fabric.client.ui.tooltip.renderer.BrewingStaticRenderer;
import com.eventui.fabric.client.ui.tooltip.renderer.CustomRecipeStaticRenderer;
import com.eventui.fabric.client.ui.tooltip.renderer.FurnaceStaticRenderer;
import com.eventui.fabric.client.ui.tooltip.renderer.SmithingStaticRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TooltipRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TooltipRenderer.class);

    public static void renderTooltip(TooltipConfig config, GuiGraphics graphics, Font font,
                                     int mouseX, int mouseY) {
        if (config == null) return;
        switch (config.getRenderType()) {
            case ADVANCED  -> renderAdvancedTooltipNative(config, graphics, font, mouseX, mouseY);
            case STANDARD  -> renderStandardTooltip(config, graphics, font, mouseX, mouseY);
            case TEXT_ONLY -> renderTextOnlyTooltip(config, graphics, font, mouseX, mouseY);
        }
    }

    private static void renderStandardTooltip(TooltipConfig config, GuiGraphics graphics,
                                              Font font, int mouseX, int mouseY) {
        String content = config.getContent();
        if (content == null || content.isEmpty()) return;

        String[] lines = content.split("\\n");
        List<net.minecraft.network.chat.Component> components = new ArrayList<>();
        for (String line : lines) components.add(MiniMessageHelper.parse(line));

        graphics.renderTooltip(font, components, java.util.Optional.empty(), mouseX, mouseY);
    }




    private static void renderTextOnlyTooltip(TooltipConfig config, GuiGraphics graphics,
                                              Font font, int mouseX, int mouseY) {
        String content = config.getContent().replace("\\n", "\n");
        String[] lines = content.split("\n|<br>|<newline>");

        int offsetX = config.getOffsetX();
        int offsetY = config.getOffsetY();
        boolean shadow = config.hasShadow();
        String align = config.getTextAlign();

        int currentY = mouseY + offsetY;
        for (String line : lines) {
            var lineComp = MiniMessageHelper.parse(line);
            int textWidth = font.width(lineComp);
            int textX = mouseX + offsetX;
            if ("center".equals(align)) {
                textX = mouseX - textWidth / 2;
            } else if ("right".equals(align)) {
                textX = mouseX - textWidth - offsetX;
            }
            graphics.drawString(font, lineComp, textX, currentY, 0xFFFFFF, shadow);
            currentY += font.lineHeight + 1;
        }
    }




    private static void renderAdvancedTooltipNative(TooltipConfig config, GuiGraphics graphics,
                                                    Font font, int mouseX, int mouseY) {
        TooltipDimensions dims = calculateTooltipDimensions(config, font);

        int screenWidth  = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + dims.width  > screenWidth  - 4) x = mouseX - dims.width  - 12;
        if (y + dims.height > screenHeight - 4) y = screenHeight - dims.height - 4;
        x = Math.max(4, x);
        y = Math.max(4, y);

        if (config.isShowTooltipBackground()) {
            renderTooltipBackground(graphics, x, y, dims.width, dims.height);
        }

        int currentY = y + 6;
        int contentX = x + 8;
        for (TooltipConfig.TooltipSection section : config.getSections()) {
            int sectionHeight = renderSectionInline(section, graphics, font,
                    contentX, currentY, dims.width - 16);
            currentY += sectionHeight + 2;
        }
    }



    private static int renderSectionInline(TooltipConfig.TooltipSection section, GuiGraphics graphics,
                                           Font font, int x, int y, int availableWidth) {
        return switch (section.getType()) {

            case TEXT -> {
                String content = section.getData().getOrDefault("content", "");
                String[] lines = content.split("\\n|<br>|<newline>");
                int h = 0;
                for (String line : lines) {
                    var comp = MiniMessageHelper.parse(line);
                    graphics.drawString(font, comp, x, y + h, 0xFFFFFF, false);
                    h += 10;
                }
                yield h;
            }


            case ITEM -> {
                String itemId   = section.getData().get("item");
                String countStr = section.getData().get("count");
                String label    = section.getData().get("label");
                int count = parseIntSafe(countStr, 1);
                try {
                    ResourceLocation itemLoc = ResourceLocation.parse(itemId);
                    net.minecraft.world.item.Item item =
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemLoc);
                    if (item != null) {
                        net.minecraft.world.item.ItemStack stack =
                                new net.minecraft.world.item.ItemStack(item, count);
                        graphics.renderItem(stack, x, y);
                        graphics.renderItemDecorations(font, stack, x, y);
                        if (label != null && !label.isEmpty()) {
                            var labelComp = MiniMessageHelper.parse(label);
                            graphics.drawString(font, labelComp, x + 20, y + 4, 0xFFFFFF, false);
                        }

                        yield 18;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to render item: {}", itemId, e);
                }
                yield 0;
            }

            case RECIPE -> {
                String recipeId    = section.getData().get("recipe");
                String customFrame = section.getData().get("frame");
                if (recipeId == null || recipeId.isBlank()) yield 0;

                // parse grid config from section properties
                RecipeGridConfig gridConfig = RecipeGridConfig.fromMap(section.getData());
                LOGGER.debug("Recipe section '{}' gridConfig={}", recipeId, gridConfig);

                try {
                    ResourceLocation recipeLoc = ResourceLocation.parse(recipeId.trim());
                    java.util.Optional<net.minecraft.world.item.crafting.Recipe<?>> recipeOpt =
                            RecipeCache.get(recipeLoc);
                    if (recipeOpt.isPresent()) {
                        RecipeTooltipComponent comp =
                                new RecipeTooltipComponent(recipeOpt.get(), customFrame, gridConfig);
                        comp.renderImage(font, x, y, graphics);
                        yield comp.getHeight();
                    } else {
                        graphics.drawString(font, "§cRecipe not found:", x, y, 0xFF5555, false);
                        graphics.drawString(font, "§7" + recipeId, x, y + 10, 0xAAAAAA, false);
                        LOGGER.debug("Recipe not found: '{}'", recipeId);
                        yield 20;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to load recipe '{}': {}", recipeId, e.getMessage());
                    yield 0;
                }
            }

            case FURNACE_RECIPE -> {
                FurnaceStaticRenderer renderer = new FurnaceStaticRenderer();
                renderer.render(graphics, font, x, y, section.getData());
                yield renderer.getHeight();
            }
            case SMITHING_RECIPE -> {
                SmithingStaticRenderer renderer = new SmithingStaticRenderer();
                renderer.render(graphics, font, x, y, section.getData());
                yield renderer.getHeight();
            }
            case ANVIL_RECIPE -> {
                AnvilStaticRenderer renderer = new AnvilStaticRenderer();
                renderer.render(graphics, font, x, y, section.getData());
                yield renderer.getHeight();
            }
            case BREWING_RECIPE -> {
                BrewingStaticRenderer renderer = new BrewingStaticRenderer();
                renderer.render(graphics, font, x, y, section.getData());
                yield renderer.getHeight();
            }

            case CUSTOM_RECIPE -> {
                CustomRecipeStaticRenderer renderer = new CustomRecipeStaticRenderer();
                renderer.render(graphics, font, x, y, section.getData());
                yield renderer.getHeight();
            }

            case ENTITY -> {
                String entityId = section.getData().get("entity");
                try {
                    ResourceLocation entityLoc = ResourceLocation.parse(entityId);
                    var entityType = net.minecraft.core.registries.BuiltInRegistries
                            .ENTITY_TYPE.get(entityLoc);
                    if (entityType != null) {
                        var entity = entityType.create(Minecraft.getInstance().level);
                        if (entity != null) {
                            EntityTooltipComponent comp = new EntityTooltipComponent(entity);
                            comp.renderImage(font, x, y, graphics);
                            yield comp.getHeight();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to create entity: {}", entityId, e);
                }
                yield 0;
            }

            case IMAGE -> {
                String texture = section.getData().get("texture");
                int width  = parseIntSafe(section.getData().get("width"),  64);
                int height = parseIntSafe(section.getData().get("height"), 64);
                if (texture != null && !texture.isEmpty()) {
                    try {
                        ResourceLocation texLoc = ResourceLocation.parse(texture);
                        graphics.blit(texLoc, x, y, 0, 0, width, height, width, height);
                        yield height;
                    } catch (Exception e) {
                        LOGGER.warn("Failed to render image: {}", texture, e);
                    }
                }
                yield 0;
            }

            case SEPARATOR -> {
                graphics.fill(x, y + 2, x + availableWidth, y + 3, 0xFF555555);
                yield 6;
            }

            default -> {
                LOGGER.warn("Unknown section type: {}", section.getType());
                yield 0;
            }
        };
    }



    private static TooltipDimensions calculateTooltipDimensions(TooltipConfig config, Font font) {
        int maxWidth    = 0;
        int totalHeight = 12;

        for (TooltipConfig.TooltipSection section : config.getSections()) {
            int sectionW = 0;
            int sectionH = 0;

            switch (section.getType()) {
                case TEXT -> {
                    String content = section.getData().getOrDefault("content", "");
                    String[] lines = content.split("\\n");
                    for (String line : lines) sectionW = Math.max(sectionW, font.width(MiniMessageHelper.parse(line)));
                    sectionH = lines.length * 10;
                }
                case ITEM -> {
                    String label = section.getData().get("label");
                    sectionW = 18;
                    if (label != null && !label.isEmpty()) sectionW += font.width(MiniMessageHelper.parse(label)) + 4;
                    sectionH = 18;
                }

                case RECIPE -> {
                    String recipeId = section.getData().get("recipe");
                    sectionW = 100; sectionH = 76;
                    if (recipeId != null && !recipeId.isBlank()) {
                        try {
                            ResourceLocation loc = ResourceLocation.parse(recipeId.trim());
                            java.util.Optional<net.minecraft.world.item.crafting.Recipe<?>> recipeOpt =
                                    RecipeCache.get(loc);
                            if (recipeOpt.isPresent()) {
                                RecipeGridConfig cfg = RecipeGridConfig.fromMap(section.getData());
                                LOGGER.debug("Calculating dimensions: recipe {} cfg={}", recipeId, cfg);
                                RecipeTooltipComponent comp =
                                        new RecipeTooltipComponent(recipeOpt.get(), null, cfg);
                                sectionW = comp.getWidth(font);
                                sectionH = comp.getHeight();
                            }
                        } catch (Exception ignored) {}
                    }
                }
                case FURNACE_RECIPE -> { sectionW = 82; sectionH = 54; }
                case SMITHING_RECIPE -> { sectionW = 108; sectionH = 58; }
                case ANVIL_RECIPE -> { sectionW = 125; sectionH = 56; }
                case BREWING_RECIPE -> { sectionW = 64; sectionH = 59; }
                case CUSTOM_RECIPE -> {
                    sectionW = parseIntSafe(section.getData().get("texture_width"),  64);
                    sectionH = parseIntSafe(section.getData().get("texture_height"), 64);
                }
                case ENTITY -> {
                    sectionW = 70; sectionH = 60;
                    String entityId = section.getData().get("entity");
                    try {
                        var entityLoc  = ResourceLocation.parse(entityId);
                        var entityType = net.minecraft.core.registries.BuiltInRegistries
                                .ENTITY_TYPE.get(entityLoc);
                        if (entityType != null && Minecraft.getInstance().level != null) {
                            var entity = entityType.create(Minecraft.getInstance().level);
                            if (entity != null) {
                                float max = Math.max(entity.getBbHeight(), entity.getBbWidth());
                                if      (max > 7f) { sectionH = 120; sectionW = 100; }
                                else if (max > 4f) { sectionH = 100; sectionW = 80;  }
                                else if (max > 3f) { sectionH = 90;  sectionW = 80;  }
                                else if (max > 2f) { sectionH = 70;  sectionW = 70;  }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                case IMAGE -> {
                    sectionW = parseIntSafe(section.getData().get("width"),  64);
                    sectionH = parseIntSafe(section.getData().get("height"), 64);
                }
                case SEPARATOR -> { sectionH = 6; }
                default -> {}
            }

            maxWidth    = Math.max(maxWidth, sectionW);
            totalHeight += sectionH + 2;
        }

        maxWidth += 16;
        return new TooltipDimensions(maxWidth, totalHeight);
    }



    private static void renderTooltipBackground(GuiGraphics graphics,
                                                int x, int y, int width, int height) {
        int bgColor    = 0xF0100010;
        int borderTop  = 0x505000FF;
        int borderBot  = 0x5028007F;
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.fillGradient(x - 3, y - 4,        x + width + 3, y - 3,          borderTop, borderTop);
        graphics.fillGradient(x - 3, y + height + 3, x + width + 3, y + height + 4, borderBot, borderBot);
        graphics.fillGradient(x - 3, y - 3,        x + width + 3, y + height + 3, bgColor,   bgColor);
        graphics.fillGradient(x - 4, y - 3,        x - 3,         y + height + 3, borderTop, borderBot);
        graphics.fillGradient(x + width + 3, y - 3, x + width + 4, y + height + 3, borderTop, borderBot);
    }

    private record TooltipDimensions(int width, int height) {}


    private record CustomTooltipComponent(Type type, Object component) {
        enum Type { ITEM, RECIPE, ENTITY }
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid integer value: '{}', using default: {}", value, defaultValue);
            return defaultValue;
        }
    }
}
