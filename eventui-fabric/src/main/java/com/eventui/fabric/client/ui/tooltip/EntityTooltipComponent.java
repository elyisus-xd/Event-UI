package com.eventui.fabric.client.ui.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record EntityTooltipComponent(Entity entity) implements ClientTooltipComponent {

    @Override
    public int getHeight() {
        float entityHeight = entity.getBbHeight();
        float entityWidth = entity.getBbWidth();
        float maxDimension = Math.max(entityHeight, entityWidth);

        if (maxDimension > 7.0f) {
            return 120;
        } else if (maxDimension > 4.0f) {
            return 100;
        } else if (maxDimension > 3.0f) {
            return 90;
        } else if (maxDimension > 2.0f) {
            return 70;
        } else {
            return 60;
        }
    }

    @Override
    public int getWidth(Font font) {
        float entityWidth = entity.getBbWidth();

        if (entityWidth > 5.0f) {
            return 100;
        } else if (entityWidth > 3.0f) {
            return 80;
        } else {
            return 70;
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (entity instanceof LivingEntity livingEntity) {
            long gameTime = Minecraft.getInstance().level.getGameTime();
            float rotation = (gameTime % 360) * 2.0f;

            livingEntity.setYRot(rotation);
            livingEntity.yBodyRot = rotation;
            livingEntity.yHeadRot = rotation;
            livingEntity.yRotO = rotation;
            livingEntity.tickCount = (int) (gameTime % 100000);
        }

        float entityHeight = entity.getBbHeight();
        float entityWidth = entity.getBbWidth();
        float maxDimension = Math.max(entityHeight, entityWidth);

        int size;
        if (maxDimension > 7.0f) {
            size = 12;
        } else if (maxDimension > 4.0f) {
            size = 15;
        } else if (maxDimension > 3.0f) {
            size = 18;
        } else if (maxDimension > 2.0f) {
            size = 25;
        } else {
            size = 35;
        }

        try {
            RenderSystem.disableScissor();

            var poseStack = graphics.pose();
            poseStack.pushPose();

            int renderHeight = getHeight();
            int renderWidth = getWidth(font);

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    x - 10,
                    y - 10,
                    x + renderWidth + 10,
                    y + renderHeight + 10,
                    size,
                    0.0625F,
                    (float) x + (renderWidth / 2),
                    (float) y + (renderHeight / 2),
                    (LivingEntity) entity
            );

            poseStack.popPose();

            RenderSystem.enableScissor(0, 0,
                    Minecraft.getInstance().getWindow().getWidth(),
                    Minecraft.getInstance().getWindow().getHeight()
            );

        } catch (Exception e) {
            String name = entity.getType().getDescription().getString();
            graphics.drawString(font, "§7" + name, x + 5, y + 25, 0xFFFFFF, true);
        }
    }

    public record Data(Entity entity) implements TooltipComponent {}
}
