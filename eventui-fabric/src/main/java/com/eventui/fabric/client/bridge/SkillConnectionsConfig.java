package com.eventui.fabric.client.bridge;

public class SkillConnectionsConfig {

    public enum ConnectionType {
        STRAIGHT,
        CURVED,
        ORTHOGONAL
    }

    private final ConnectionType type;
    private final int thickness;
    private final String color;
    private final float opacity;
    private final boolean dashed;
    private final boolean glow;
    private final boolean animated;
    private final String lockedColor;
    private final String availableColor;
    private final String partialColor;
    private final String maxedColor;
    private final boolean showOnHover;

    public SkillConnectionsConfig(
            String type,
            int thickness,
            String color,
            float opacity,
            boolean dashed,
            boolean glow,
            boolean animated,
            String lockedColor,
            String availableColor,
            String partialColor,
            String maxedColor,
            boolean showOnHover) {

        this.type = parseType(type);
        this.thickness = Math.max(1, Math.min(5, thickness));
        this.color = color != null ? color : "#FFFFFF";
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        this.dashed = dashed;
        this.glow = glow;
        this.animated = animated;
        this.lockedColor = lockedColor != null ? lockedColor : "#555555";
        this.availableColor = availableColor != null ? availableColor : "#00FF00";
        this.partialColor = partialColor != null ? partialColor : "#FFFF00";
        this.maxedColor = maxedColor != null ? maxedColor : "#00FFFF";
        this.showOnHover = showOnHover;
    }

    private ConnectionType parseType(String type) {
        if (type == null) return ConnectionType.CURVED;
        try {
            return ConnectionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ConnectionType.CURVED;
        }
    }

    public static SkillConnectionsConfig defaults() {
        return new SkillConnectionsConfig(
                "curved",
                2,
                "#FFFFFF",
                0.7f,
                false,
                true,
                false,
                "#555555",
                "#00FF00",
                "#FFFF00",
                "#00FFFF",
                true
        );
    }

    public ConnectionType getType() { return type; }
    public int getThickness() { return thickness; }
    public String getColor() { return color; }
    public float getOpacity() { return opacity; }
    public boolean isDashed() { return dashed; }
    public boolean hasGlow() { return glow; }
    public boolean isAnimated() { return animated; }
    public String getLockedColor() { return lockedColor; }
    public String getAvailableColor() { return availableColor; }
    public String getPartialColor() { return partialColor; }
    public String getMaxedColor() { return maxedColor; }
    public boolean showOnHover() { return showOnHover; }
}
