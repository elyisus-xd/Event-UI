package com.eventui.api.ui;

/**
 * Badge de notificación sobre un botón.
 */
public class UIBadge {

    private final boolean enabled;
    private final String texture;
    private final int xOffset;
    private final int yOffset;
    private final int width;
    private final int height;
    private final String condition;

    public UIBadge(boolean enabled, String texture, int xOffset, int yOffset,
                   int width, int height, String condition) {
        this.enabled = enabled;
        this.texture = texture;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.width = width;
        this.height = height;
        this.condition = condition;
    }

    public boolean isEnabled() { return enabled; }
    public String getTexture() { return texture; }
    public int getXOffset() { return xOffset; }
    public int getYOffset() { return yOffset; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getCondition() { return condition; }
}
