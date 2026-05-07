package com.eventui.api.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper para leer properties de UIElement de forma tipada.
 */
public class UIElementHelper {

    public static String getTexture(UIElement element) {
        return element.getProperties().get("texture");
    }

    public static String getHoverTexture(UIElement element) {
        return element.getProperties().get("hover_texture");
    }

    public static boolean hasHoverTexture(UIElement element) {
        return element.getProperties().containsKey("hover_texture");
    }

    public static String getActionType(UIElement element) {
        return element.getProperties().get("action_type");
    }

    public static String getActionTarget(UIElement element) {
        return element.getProperties().get("action_target");
    }

    public static boolean hasAction(UIElement element) {
        return element.getProperties().containsKey("action_type");
    }

    public static boolean isBadgeEnabled(UIElement element) {
        String enabled = element.getProperties().get("badge_enabled");
        return "true".equalsIgnoreCase(enabled);
    }

    public static String getBadgeTexture(UIElement element) {
        return element.getProperties().get("badge_texture");
    }

    public static int getBadgeXOffset(UIElement element) {
        return parseInt(element.getProperties().get("badge_x_offset"), 0);
    }

    public static int getBadgeYOffset(UIElement element) {
        return parseInt(element.getProperties().get("badge_y_offset"), 0);
    }

    public static int getBadgeWidth(UIElement element) {
        return parseInt(element.getProperties().get("badge_width"), 12);
    }

    public static int getBadgeHeight(UIElement element) {
        return parseInt(element.getProperties().get("badge_height"), 12);
    }

    public static String getBadgeCondition(UIElement element) {
        return element.getProperties().get("badge_condition");
    }


    public static String getTooltipType(UIElement element) {
        return element.getProperties().getOrDefault("tooltip_type", "INLINE");
    }

    /**
     * Parsea las líneas del tooltip desde JSON simple.
     *
     * @return Lista de TooltipLine
     */
    public static List<TooltipLine> getTooltipLines(UIElement element) {
        String tooltipJson = element.getProperties().get("tooltip_lines");

        if (tooltipJson == null || tooltipJson.isEmpty()) {
            return List.of();
        }

        try {
            return parseTooltipLinesManually(tooltipJson);
        } catch (Exception e) {
            System.err.println("Failed to parse tooltip lines: " + e.getMessage());
            return List.of();
        }
    }

    public static boolean hasTooltip(UIElement element) {
        return element.getProperties().containsKey("tooltip_lines");
    }

    /**
     * Parsea JSON simple de tooltips manualmente (sin Gson).
     */
    private static List<TooltipLine> parseTooltipLinesManually(String json) {
        List<TooltipLine> lines = new ArrayList<>();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        String[] objects = json.split("\\},\\{");

        for (String obj : objects) {
            obj = obj.replace("{", "").replace("}", "");
            String text = null;
            int x = 0;
            int y = 0;
            String[] pairs = obj.split(",(?=\\s*\"\\w+\":)");

            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length != 2) continue;

                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim();

                switch (key) {
                    case "text":
                        text = value.replace("\"", "");
                        break;
                    case "x":
                        x = Integer.parseInt(value);
                        break;
                    case "y":
                        y = Integer.parseInt(value);
                        break;
                }
            }

            if (text != null) {
                lines.add(new TooltipLine(text, x, y));
            }
        }

        return lines;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    /**
     * Parsea configuración de tooltip (soporte avanzado).
     */
    public static TooltipConfig parseTooltipConfig(UIElement tooltipElement) {
        if (tooltipElement.getType() != UIElementType.TOOLTIP) {
            return null;
        }

        String renderTypeStr = tooltipElement.getProperties().getOrDefault("render_type", "text_only");
        String content = tooltipElement.getProperties().getOrDefault("content", "");


        TooltipConfig.RenderType renderType;
        try {

            renderType = TooltipConfig.RenderType.valueOf(renderTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {

            renderType = switch (renderTypeStr.toLowerCase()) {
                case "text", "text_only" -> TooltipConfig.RenderType.TEXT_ONLY;
                case "standard" -> TooltipConfig.RenderType.STANDARD;
                case "advanced" -> TooltipConfig.RenderType.ADVANCED;
                default -> TooltipConfig.RenderType.TEXT_ONLY;
            };
        }

        java.util.List<TooltipConfig.TooltipSection> sections = new java.util.ArrayList<>();

        if (renderType == TooltipConfig.RenderType.ADVANCED) {
            sections = parseTooltipSections(tooltipElement);
        }


        if (renderType == TooltipConfig.RenderType.ADVANCED) {
            String advancedContent = tooltipElement.getProperties().get("content");

            if (advancedContent != null && !advancedContent.isEmpty()) {

                sections = parseTooltipSectionsInline(advancedContent);
            } else {

                sections = parseTooltipSections(tooltipElement);
            }
        }
        return new TooltipConfig(renderType, content, tooltipElement.getProperties(), sections);
    }

    /**
     * Parsea secciones de tooltip avanzado desde properties.
     */
    private static java.util.List<TooltipConfig.TooltipSection> parseTooltipSections(UIElement tooltipElement) {
        java.util.List<TooltipConfig.TooltipSection> sections = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String typeKey = "section_" + i + "_type";
            String type = tooltipElement.getProperties().get(typeKey);

            if (type == null || type.isEmpty()) {
                break;
            }
            java.util.Map<String, String> sectionData = new java.util.HashMap<>();
            String prefix = "section_" + i + "_";

            for (java.util.Map.Entry<String, String> entry : tooltipElement.getProperties().entrySet()) {
                if (entry.getKey().startsWith(prefix) && !entry.getKey().equals(typeKey)) {
                    String key = entry.getKey().substring(prefix.length());
                    sectionData.put(key, entry.getValue());
                }
            }


            try {
                TooltipConfig.TooltipSection.SectionType sectionType =
                        TooltipConfig.TooltipSection.SectionType.valueOf(type.toUpperCase());

                sections.add(new TooltipConfig.TooltipSection(sectionType, sectionData));
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid tooltip section type: " + type);
            }
        }

        return sections;
    }

    /**
     * Parsea UIBadge desde properties con objeto anidado.
     * Fallback a formato flat si no hay objeto.
     */
    public static UIBadge parseBadge(UIElement element) {
        if (element.getProperties().containsKey("badge_enabled")) {
            return parseBadgeFlat(element);
        }
        boolean enabled = isBadgeEnabled(element);
        if (!enabled) {
            return null;
        }

        String texture = getBadgeTexture(element);
        int xOffset = getBadgeXOffset(element);
        int yOffset = getBadgeYOffset(element);
        int width = getBadgeWidth(element);
        int height = getBadgeHeight(element);
        String condition = getBadgeCondition(element);

        return new UIBadge(enabled, texture, xOffset, yOffset, width, height, condition);
    }

    private static UIBadge parseBadgeFlat(UIElement element) {
        boolean enabled = isBadgeEnabled(element);
        if (!enabled) return null;

        String texture = getBadgeTexture(element);
        int xOffset = getBadgeXOffset(element);
        int yOffset = getBadgeYOffset(element);
        int width = getBadgeWidth(element);
        int height = getBadgeHeight(element);
        String condition = getBadgeCondition(element);

        return new UIBadge(enabled, texture, xOffset, yOffset, width, height, condition);
    }

    public record TooltipLine(String text, int x, int y) {}

    public static String getIconItem(UIElement element) {
        return element.getProperties().get("icon");
    }

    public static boolean hasIcon(UIElement element) {
        return element.getProperties().containsKey("icon");
    }

    public static float getIconScale(UIElement element) {
        String scale = element.getProperties().get("icon_scale");
        if (scale == null || scale.isEmpty()) {
            return 1.0f;
        }
        try {
            return Float.parseFloat(scale);
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }

    public static int getIconOffsetX(UIElement element) {
        return parseInt(element.getProperties().get("icon_offset_x"), 0);
    }

    public static int getIconOffsetY(UIElement element) {
        return parseInt(element.getProperties().get("icon_offset_y"), 0);
    }

    public static String getInProgressTexture(UIElement element) {
        return element.getProperties().get("in_progress_texture");
    }

    public static String getCompletedTexture(UIElement element) {
        return element.getProperties().get("completed_texture");
    }

    public static String getFailedTexture(UIElement element) {
        return element.getProperties().get("failed_texture");
    }

    public static String getLockedTexture(UIElement element) {
        return element.getProperties().get("locked_texture");
    }

    public static String getCompletedOverlay(UIElement element) {
        return element.getProperties().get("completed_overlay");
    }

    public static String getFailedOverlay(UIElement element) {
        return element.getProperties().get("failed_overlay");
    }

    public static String getLockedOverlay(UIElement element) {
        return element.getProperties().get("locked_overlay");
    }

    public static boolean hasOverlay(UIElement element, String state) {
        return element.getProperties().containsKey(state + "_overlay");
    }

    /**
     * Parsea configuración de animaciones de hover (soporte multi-efecto).
     * Formato simple:
     *   hover_animation: "zoom_in"
     *   hover_animation_intensity: "1.15"
     * Formato extendido:
     *   hover_animation: "zoom_in,glow,shake"
     *   hover_animation_intensity: "1.15"
     * Retorna lista de efectos a aplicar.
     */
    public static java.util.List<HoverEffect> parseHoverEffects(UIElement element) {
        java.util.List<HoverEffect> effects = new java.util.ArrayList<>();

        String animProp = element.getProperties().get("hover_animation");

        if (animProp == null || animProp.isEmpty() || "none".equalsIgnoreCase(animProp)) {
            return effects;
        }

        String[] animTypes = animProp.split(",");

        for (String animType : animTypes) {
            animType = animType.trim();

            if (animType.isEmpty() || "none".equalsIgnoreCase(animType)) {
                continue;
            }
            float intensity = parseFloat(element.getProperties().get("hover_animation_intensity"), getDefaultIntensity(animType));
            int duration = parseInt(element.getProperties().get("hover_animation_duration"), 150);
            String easing = element.getProperties().getOrDefault("hover_animation_easing", "ease_out");

            effects.add(new HoverEffect(animType, intensity, duration, easing));
        }
        return effects;
    }

    /**
     * Obtiene la intensidad por defecto según el tipo de animación.
     */
    private static float getDefaultIntensity(String animType) {
        return switch (animType.toLowerCase()) {
            case "zoom_in" -> 1.15f;
            case "shake" -> 3.0f;
            case "bounce" -> 2.0f;
            case "rotate" -> 2.0f;
            case "pulse" -> 0.15f;
            default -> 1.0f;
        };
    }

    /**
     * Verifica si un elemento tiene animaciones de hover.
     */
    public static boolean hasHoverAnimation(UIElement element) {
        String animType = element.getProperties().get("hover_animation");
        return animType != null && !animType.isEmpty() && !"none".equalsIgnoreCase(animType);
    }
    public static float parseFloat(String value, float defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Obtiene el tipo de hitbox para detección de hover.
     * Valores:
     *   - "bounds" (default)
     *   - "texture_alpha": Solo píxeles no transparentes
     */
    public static String getHoverHitbox(UIElement element) {
        return element.getProperties().getOrDefault("hover_hitbox", "bounds");
    }

    /**
     * Verifica si se debe usar detección de hover basada en alpha.
     */
    public static boolean useTextureAlphaHitbox(UIElement element) {
        return "texture_alpha".equalsIgnoreCase(getHoverHitbox(element));
    }
    /**
     * Parsea secciones desde formato inline compacto.
     * Formato:
     *   text: §6Título
     *   ---
     *   item: minecraft:diamond x5 [§bLabel opcional]
     *   recipe: minecraft:diamond_pickaxe
     *   entity: minecraft:zombie
     */
    private static java.util.List<TooltipConfig.TooltipSection> parseTooltipSectionsInline(String content) {
        java.util.List<TooltipConfig.TooltipSection> sections = new java.util.ArrayList<>();

        String[] lines = content.split("\\n");

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("---") || line.equals("===")) {
                sections.add(new TooltipConfig.TooltipSection(
                        TooltipConfig.TooltipSection.SectionType.SEPARATOR,
                        java.util.Map.of()
                ));
                continue;
            }
            if (!line.contains(":")) {
                continue;
            }

            String[] parts = line.split(":", 2);
            String type = parts[0].trim().toLowerCase();
            String value = parts[1].trim();

            java.util.Map<String, String> data = new java.util.HashMap<>();
            switch (type) {
                case "text" -> {
                    data.put("content", value);
                    sections.add(new TooltipConfig.TooltipSection(
                            TooltipConfig.TooltipSection.SectionType.TEXT,
                            data
                    ));
                }

                case "item" -> {
                    String[] itemParts = value.split("\\s+");
                    String itemId = itemParts[0];
                    int count = 1;
                    String label = null;

                    for (int i = 1; i < itemParts.length; i++) {
                        if (itemParts[i].startsWith("x")) {
                            count = Integer.parseInt(itemParts[i].substring(1));
                        } else if (itemParts[i].startsWith("[")) {
                            label = value.substring(value.indexOf("[") + 1, value.indexOf("]"));
                        }
                    }

                    data.put("item", itemId);
                    data.put("count", String.valueOf(count));
                    if (label != null) {
                        data.put("label", label);
                    }

                    sections.add(new TooltipConfig.TooltipSection(
                            TooltipConfig.TooltipSection.SectionType.ITEM,
                            data
                    ));
                }

                case "recipe" -> {
                    String recipeId = value;
                    String frame = null;

                    if (value.contains("[") && value.contains("]")) {
                        int bracketStart = value.indexOf("[");
                        int bracketEnd = value.indexOf("]");
                        recipeId = value.substring(0, bracketStart).trim();

                        String frameStr = value.substring(bracketStart + 1, bracketEnd);
                        if (frameStr.startsWith("frame:")) {
                            frame = frameStr.substring(6).trim();
                        }
                    }

                    data.put("recipe", recipeId);
                    if (frame != null) {
                        data.put("frame", frame);
                    }

                    sections.add(new TooltipConfig.TooltipSection(
                            TooltipConfig.TooltipSection.SectionType.RECIPE,
                            data
                    ));
                }


                case "entity" -> {
                    data.put("entity", value);
                    sections.add(new TooltipConfig.TooltipSection(
                            TooltipConfig.TooltipSection.SectionType.ENTITY,
                            data
                    ));
                }

                case "image" -> {
                    String[] imageParts = value.split("\\s+");
                    data.put("texture", imageParts[0]);

                    if (imageParts.length > 1 && imageParts[1].contains("x")) {
                        String[] dimensions = imageParts[1].split("x");
                        data.put("width", dimensions[0]);
                        data.put("height", dimensions[1]);
                    }

                    sections.add(new TooltipConfig.TooltipSection(
                            TooltipConfig.TooltipSection.SectionType.IMAGE,
                            data
                    ));
                }
            }
        }
        return sections;
    }
}
