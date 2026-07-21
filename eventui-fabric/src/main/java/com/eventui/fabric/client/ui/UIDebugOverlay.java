package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIElement;
import com.eventui.api.ui.UIConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class UIDebugOverlay {

    private static final int COLOR_IMAGE        = 0x8800AAFF;
    private static final int COLOR_IMAGEBUTTON  = 0x88FF6600;
    private static final int COLOR_BUTTON       = 0x88FF0000;
    private static final int COLOR_TEXT         = 0x8800FF88;
    private static final int COLOR_PANEL        = 0x88FF00FF;
    private static final int COLOR_PROGRESSBAR  = 0x88FFFF00;
    private static final int COLOR_OTHER        = 0x88AAAAAA;

    private static final int BORDER_IMAGE       = 0xFF00AAFF;
    private static final int BORDER_IMAGEBUTTON = 0xFFFF6600;
    private static final int BORDER_BUTTON      = 0xFFFF0000;
    private static final int BORDER_TEXT        = 0xFF00FF88;
    private static final int BORDER_PANEL       = 0xFFFF00FF;
    private static final int BORDER_PROGRESSBAR = 0xFFFFFF00;
    private static final int BORDER_OTHER       = 0xFFAAAAAA;

    public static void render(GuiGraphics graphics, Font font, UIConfig config,
                              int mouseX, int mouseY,
                              int offsetX, int offsetY, float uiScale) {

        int localMouseX = (int)((mouseX - offsetX) / uiScale);
        int localMouseY = (int)((mouseY - offsetY) / uiScale);

        renderElements(graphics, font, config.getRootElements(),
                mouseX, mouseY, localMouseX, localMouseY,
                offsetX, offsetY, uiScale,
                config.getScreenWidth(), config.getScreenHeight(),
                false);

        int designCenterX = offsetX + (int)(config.getScreenWidth()  * uiScale / 2);
        int designCenterY = offsetY + (int)(config.getScreenHeight() * uiScale / 2);
        int panelRight    = offsetX + (int)(config.getScreenWidth()  * uiScale);
        int panelBottom   = offsetY + (int)(config.getScreenHeight() * uiScale);

        graphics.fill(designCenterX, offsetY, designCenterX + 1, panelBottom, 0x55FFFFFF);

        graphics.fill(offsetX, designCenterY, panelRight, designCenterY + 1, 0x55FFFFFF);

        graphics.fill(offsetX,          offsetY,           panelRight,      offsetY + 1,      0xAAFFFFFF);
        graphics.fill(offsetX,          panelBottom - 1,   panelRight,      panelBottom,      0xAAFFFFFF);
        graphics.fill(offsetX,          offsetY,           offsetX + 1,     panelBottom,      0xAAFFFFFF);
        graphics.fill(panelRight - 1,   offsetY,           panelRight,      panelBottom,      0xAAFFFFFF);

        String mouseInfo = String.format("§eMouse: §f%d, %d  §7(screen: %d, %d)",
                localMouseX, localMouseY, mouseX, mouseY);
        graphics.fill(4, 4, font.width(mouseInfo) + 8, 14, 0xAA000000);
        graphics.drawString(font, mouseInfo, 6, 6, 0xFFFFFF, false);

        String scaleInfo = String.format("§7Design: §f%dx%d  §7Scale: §f%.3f  §7Offset: §f%d,%d",
                config.getScreenWidth(), config.getScreenHeight(), uiScale, offsetX, offsetY);
        graphics.fill(4, 16, font.width(scaleInfo) + 8, 26, 0xAA000000);
        graphics.drawString(font, scaleInfo, 6, 18, 0xAAAAAA, false);
    }

    private static void renderElements(GuiGraphics graphics, Font font,
                                       List<UIElement> elements,
                                       int mouseX, int mouseY,
                                       int localMouseX, int localMouseY,
                                       int offsetX, int offsetY, float uiScale,
                                       int designWidth, int designHeight,
                                       boolean isChild) {
        for (UIElement element : elements) {
            if (element.getType() == com.eventui.api.ui.UIElementType.TOOLTIP) continue;

            int resolvedX = element.getX();
            int resolvedY = element.getY();
            if (designWidth > 0 && element.getProperties().containsKey("anchor")) {
                AnchorResolver.ResolvedPos resolved = isChild
                        ? AnchorResolver.resolveRelative(element, designWidth, designHeight)
                        : AnchorResolver.resolve(element, designWidth, designHeight);
                resolvedX = resolved.x();
                resolvedY = resolved.y();
            }

            int screenX = offsetX + (int)(resolvedX * uiScale);
            int screenY = offsetY + (int)(resolvedY * uiScale);
            int screenW = (int)(element.getWidth()  * uiScale);
            int screenH = (int)(element.getHeight() * uiScale);

            boolean hovered = localMouseX >= resolvedX
                    && localMouseX <= resolvedX + element.getWidth()
                    && localMouseY >= resolvedY
                    && localMouseY <= resolvedY + element.getHeight();

            if (!element.getChildren().isEmpty()) {
                renderElements(graphics, font, element.getChildren(),
                        mouseX, mouseY, localMouseX - resolvedX, localMouseY - resolvedY,
                        offsetX + (int)(resolvedX * uiScale),
                        offsetY + (int)(resolvedY * uiScale),
                        uiScale,
                        element.getWidth(), element.getHeight(),
                        true);
            }
            int fillColor   = getFillColor(element);
            int borderColor = getBorderColor(element);

            graphics.fill(screenX, screenY, screenX + screenW, screenY + screenH, fillColor);

            graphics.fill(screenX,              screenY,              screenX + screenW, screenY + 1,          borderColor);
            graphics.fill(screenX,              screenY + screenH - 1, screenX + screenW, screenY + screenH,   borderColor);
            graphics.fill(screenX,              screenY,              screenX + 1,        screenY + screenH,   borderColor);
            graphics.fill(screenX + screenW - 1, screenY,             screenX + screenW, screenY + screenH,   borderColor);

            if (hovered) {
                renderHoverInfo(graphics, font, element, mouseX, mouseY);
            }
        }
    }

    private static void renderHoverInfo(GuiGraphics graphics, Font font,
                                        UIElement element, int mouseX, int mouseY) {
        String[] lines = {
                "§e" + element.getId(),
                "§7Type: §f"    + element.getType().name(),
                "§7Pos: §f"     + element.getX() + ", " + element.getY(),
                "§7Size: §f"    + element.getWidth() + " x " + element.getHeight(),
                "§7zIndex: §f"  + element.getZIndex(),
                "§7Children: §f" + element.getChildren().size()
        };

        int padding  = 4;
        int lineH    = 10;
        int boxW     = 0;
        for (String line : lines) boxW = Math.max(boxW, font.width(line));
        boxW += padding * 2;
        int boxH = lines.length * lineH + padding * 2;

        int boxX = mouseX + 12;
        int boxY = mouseY - boxH - 4;
        if (boxX + boxW > 1920) boxX = mouseX - boxW - 4;
        if (boxY < 0) boxY = mouseY + 16;

        graphics.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF333333);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE111111);

        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], boxX + padding, boxY + padding + i * lineH, 0xFFFFFF, false);
        }
    }

    private static int getFillColor(UIElement element) {
        return switch (element.getType()) {
            case IMAGE        -> COLOR_IMAGE;
            case IMAGE_BUTTON -> COLOR_IMAGEBUTTON;
            case BUTTON       -> COLOR_BUTTON;
            case TEXT         -> COLOR_TEXT;
            case PANEL        -> COLOR_PANEL;
            case PROGRESS_BAR -> COLOR_PROGRESSBAR;
            default           -> COLOR_OTHER;
        };
    }

    private static int getBorderColor(UIElement element) {
        return switch (element.getType()) {
            case IMAGE        -> BORDER_IMAGE;
            case IMAGE_BUTTON -> BORDER_IMAGEBUTTON;
            case BUTTON       -> BORDER_BUTTON;
            case TEXT         -> BORDER_TEXT;
            case PANEL        -> BORDER_PANEL;
            case PROGRESS_BAR -> BORDER_PROGRESSBAR;
            default           -> BORDER_OTHER;
        };
    }
}
