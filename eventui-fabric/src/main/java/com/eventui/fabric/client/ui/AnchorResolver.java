package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIElement;

public class AnchorResolver {

    public record ResolvedPos(int x, int y) {}
    public static ResolvedPos resolve(UIElement element, int designWidth, int designHeight) {
        String anchor = element.getProperties().get("anchor");
        if (anchor == null || anchor.isEmpty() || anchor.equals("none")) {
            return new ResolvedPos(element.getX(), element.getY());
        }

        int offsetX = parseIntProp(element, "anchor_offset_x", 0);
        int offsetY = parseIntProp(element, "anchor_offset_y", 0);

        int anchorPointX = resolveAnchorX(anchor, designWidth);
        int anchorPointY = resolveAnchorY(anchor, designHeight);

        int resolvedX = anchorPointX - element.getWidth()  / 2 + offsetX;
        int resolvedY = anchorPointY - element.getHeight() / 2 + offsetY;

        return new ResolvedPos(resolvedX, resolvedY);
    }

    public static ResolvedPos resolveRelative(UIElement element, int parentWidth, int parentHeight) {
        String anchor = element.getProperties().get("anchor");

        if (anchor == null || anchor.isEmpty() || anchor.equals("none")) {
            return new ResolvedPos(element.getX(), element.getY());
        }

        int offsetX = parseIntProp(element, "anchor_offset_x", 0);
        int offsetY = parseIntProp(element, "anchor_offset_y", 0);

        int anchorPointX = resolveAnchorX(anchor, parentWidth);
        int anchorPointY = resolveAnchorY(anchor, parentHeight);

        int resolvedX = anchorPointX - element.getWidth()  / 2 + offsetX;
        int resolvedY = anchorPointY - element.getHeight() / 2 + offsetY;

        return new ResolvedPos(resolvedX, resolvedY);
    }

    private static int resolveAnchorX(String anchor, int width) {
        return switch (anchor.toLowerCase()) {
            case "top_left", "center_left", "bottom_left"        -> 0;
            case "top_center", "center", "bottom_center"         -> width / 2;
            case "top_right", "center_right", "bottom_right"     -> width;
            default -> {
                org.slf4j.LoggerFactory.getLogger(AnchorResolver.class)
                        .warn("Unknown anchor value '{}', defaulting to top_left", anchor);
                yield 0;
            }
        };
    }

    private static int resolveAnchorY(String anchor, int height) {
        return switch (anchor.toLowerCase()) {
            case "top_left", "top_center", "top_right"           -> 0;
            case "center_left", "center", "center_right"         -> height / 2;
            case "bottom_left", "bottom_center", "bottom_right"  -> height;
            default -> 0;
        };
    }

    private static int parseIntProp(UIElement element, String key, int defaultValue) {
        String val = element.getProperties().get(key);
        if (val == null || val.isEmpty()) return defaultValue;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
