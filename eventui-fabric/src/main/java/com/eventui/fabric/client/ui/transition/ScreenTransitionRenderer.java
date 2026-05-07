package com.eventui.fabric.client.ui.transition;

import com.eventui.fabric.client.ui.animation.Easing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;


public class ScreenTransitionRenderer {

    public static void applyTransitionIn(ScreenTransition transition, float progress,
                                         GuiGraphics graphics, int screenWidth, int screenHeight,
                                         Easing easing) {
        PoseStack poseStack = graphics.pose();
        float easedProgress = easing.apply(progress);

        switch (transition) {
            case SLIDE_LEFT -> {
                float offset = (1.0f - easedProgress) * screenWidth;
                poseStack.translate(offset, 0, 0);
            }
            case SLIDE_RIGHT -> {
                float offset = (1.0f - easedProgress) * -screenWidth;
                poseStack.translate(offset, 0, 0);
            }
            case SLIDE_UP -> {
                float offset = (1.0f - easedProgress) * screenHeight;
                poseStack.translate(0, offset, 0);
            }
            case SLIDE_DOWN -> {
                float offset = (1.0f - easedProgress) * -screenHeight;
                poseStack.translate(0, offset, 0);
            }
            case SCALE_UP -> {
                float scale = easedProgress;
                poseStack.translate(screenWidth / 2.0f, screenHeight / 2.0f, 0);
                poseStack.scale(scale, scale, 1.0f);
                poseStack.translate(-screenWidth / 2.0f, -screenHeight / 2.0f, 0);
            }
            case SCALE_DOWN -> {
                float scale = 1.0f + (1.0f - easedProgress);
                poseStack.translate(screenWidth / 2.0f, screenHeight / 2.0f, 0);
                poseStack.scale(scale, scale, 1.0f);
                poseStack.translate(-screenWidth / 2.0f, -screenHeight / 2.0f, 0);
            }
            case FADE, NONE -> {}
        }
    }


}
