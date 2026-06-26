package com.eventui.fabric.client.ui.tooltip;

import java.util.Map;

public class RecipeGridConfig {

    private boolean showGridFrame = true;
    private int slotSpacing = 18;       // 16px item + 2px gap
    private String gridFrameTexture = "eventui:textures/ui/widgets/crafting_frame.png";
    private int gridFrameWidth = 54;
    private int gridFrameHeight = 54;
    private int outputOffsetX = 76;
    private int outputOffsetY = 19;
    private boolean showInventoryPreview = false;

    public RecipeGridConfig() {}

    public static RecipeGridConfig fromMap(Map<String, String> data) {
        RecipeGridConfig cfg = new RecipeGridConfig();
        java.util.Map<String, String> merged = new java.util.HashMap<>();
        try {
            merged.putAll(com.eventui.fabric.client.keybinds.EventUIKeybinds.getCachedTooltipDefaults());
        } catch (Exception ignored) {}
        if (data != null) merged.putAll(data);

        if (merged.containsKey("show_grid_frame"))
            cfg.showGridFrame = !"false".equalsIgnoreCase(merged.get("show_grid_frame"));
        if (merged.containsKey("slot_spacing"))
            cfg.slotSpacing = parseIntSafe(merged.get("slot_spacing"), cfg.slotSpacing);
        if (merged.containsKey("grid_frame_texture"))
            cfg.gridFrameTexture = merged.get("grid_frame_texture");
        if (merged.containsKey("grid_frame_width"))
            cfg.gridFrameWidth = parseIntSafe(merged.get("grid_frame_width"), cfg.gridFrameWidth);
        if (merged.containsKey("grid_frame_height"))
            cfg.gridFrameHeight = parseIntSafe(merged.get("grid_frame_height"), cfg.gridFrameHeight);
        if (merged.containsKey("output_offset_x"))
            cfg.outputOffsetX = parseIntSafe(merged.get("output_offset_x"), cfg.outputOffsetX);
        if (merged.containsKey("output_offset_y"))
            cfg.outputOffsetY = parseIntSafe(merged.get("output_offset_y"), cfg.outputOffsetY);
        if (merged.containsKey("inventory_preview"))
            cfg.showInventoryPreview = !"false".equalsIgnoreCase(merged.get("inventory_preview"));

        return cfg;
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    // --- Getters ---
    public boolean isShowGridFrame() { return showGridFrame; }
    public int getSlotSpacing() { return slotSpacing; }
    public String getGridFrameTexture() { return gridFrameTexture; }
    public int getGridFrameWidth() { return gridFrameWidth; }
    public int getGridFrameHeight() { return gridFrameHeight; }
    public int getOutputOffsetX() { return outputOffsetX; }
    public int getOutputOffsetY() { return outputOffsetY; }
    public boolean isShowInventoryPreview() { return showInventoryPreview; }

    @Override
    public String toString() {
        return "RecipeGridConfig{" +
                "showGridFrame=" + showGridFrame +
                ", slotSpacing=" + slotSpacing +
                ", gridFrameTexture='" + gridFrameTexture + '\'' +
                ", gridFrameWidth=" + gridFrameWidth +
                ", gridFrameHeight=" + gridFrameHeight +
                ", outputOffsetX=" + outputOffsetX +
                ", outputOffsetY=" + outputOffsetY +
                ", showInventoryPreview=" + showInventoryPreview +
                '}';
    }
}
