package com.eventui.fabric.client.ui.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Helper for drawing recipe slots and arrows using simple rectangles.
 * No textures required — slots are rendered as grey-bordered dark squares.
 */
public class RecipeSlotHelper {

    public static final int SLOT_SIZE = 18;  // 16px item + 1px padding each side
    public static final int ARROW_WIDTH = 14;
    public static final int PADDING = 4;

    /** Draws an ingredient slot (dark square with grey border). */
    public static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B); // border
        graphics.fill(x, y, x + 16, y + 16, 0xFF373737);           // background
    }

    /** Draws the arrow character between slots. */
    public static void drawArrow(GuiGraphics graphics, Font font, int x, int y) {
        graphics.drawString(font, "\u2192", x, y, 0xFFFFFFFF, false);
    }
}
