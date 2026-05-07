package com.eventui.api.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuración de tooltip avanzado.
 * Extendido para soportar secciones (items, recetas, entidades).
 */
public class TooltipConfig {

    private final RenderType renderType;
    private final String content;
    private final Map<String, String> properties;


    private final List<TooltipSection> sections;

    public TooltipConfig(RenderType renderType, String content, Map<String, String> properties) {
        this(renderType, content, properties, new ArrayList<>());
    }


    public TooltipConfig(RenderType renderType, String content, Map<String, String> properties,
                         List<TooltipSection> sections) {
        this.renderType = renderType;
        this.content = content;
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
        this.sections = sections != null ? sections : new ArrayList<>();
    }

    public RenderType getRenderType() { return renderType; }
    public String getContent() { return content; }
    public Map<String, String> getProperties() { return properties; }


    public List<TooltipSection> getSections() { return sections; }


    public int getOffsetX() {
        return parseInt(properties.get("offset_x"), 5);
    }

    public int getOffsetY() {
        return parseInt(properties.get("offset_y"), -10);
    }

    public String getTextAlign() {
        return properties.getOrDefault("text_align", "left");
    }

    public boolean hasShadow() {
        return "true".equalsIgnoreCase(properties.get("shadow"));
    }


    public String getRecipeId() {
        return properties.get("render_recipe");
    }

    public boolean hasRecipe() {
        return properties.containsKey("render_recipe");
    }

    public String getImagePath() {
        return properties.get("render_image");
    }

    public boolean hasImage() {
        return properties.containsKey("render_image");
    }

    public String getEntityId() {
        return properties.get("render_entity");
    }

    public boolean hasEntity() {
        return properties.containsKey("render_entity");
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public enum RenderType {
        STANDARD,
        TEXT_ONLY,
        ADVANCED
    }

    /**
     * Sección de un tooltip avanzado.
     */
    public static class TooltipSection {
        private final SectionType type;
        private final Map<String, String> data;

        public TooltipSection(SectionType type, Map<String, String> data) {
            this.type = type;
            this.data = data;
        }

        public SectionType getType() { return type; }
        public Map<String, String> getData() { return data; }

        public enum SectionType {
            TEXT,
            ITEM,
            RECIPE,
            ENTITY,
            IMAGE,
            SEPARATOR
        }
    }
}
