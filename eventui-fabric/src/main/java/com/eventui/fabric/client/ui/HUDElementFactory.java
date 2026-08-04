package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIElement;
import com.eventui.api.ui.UIElementType;
import com.eventui.core.config.UIElementImpl;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HUDElementFactory {
    
    private static final Pattern SCREEN_WIDTH_PATTERN = Pattern.compile("screen_width\\s*([+-])\\s*(\\d+)");
    private static final Pattern SCREEN_HEIGHT_PATTERN = Pattern.compile("screen_height\\s*([+-])\\s*(\\d+)");
    private static final Pattern WIDTH_PATTERN = Pattern.compile("width\\s*([+-])\\s*(\\d+)");
    private static final Pattern HEIGHT_PATTERN = Pattern.compile("height\\s*([+-])\\s*(\\d+)");
    private static final Pattern BINDING_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}");
    
    
    private static final Map<String, UIElement> staticElementCache = new HashMap<>();
    
    public static UIElement createElement(QuestTrackerConfig.ElementConfig config, Map<String, Object> context) {
        return createElement(config, context, 0, 0, 0, 0);
    }
    
    public static UIElement createElement(QuestTrackerConfig.ElementConfig config, Map<String, Object> context,
                                         int parentX, int parentY, int parentWidth, int parentHeight) {
        
        String cacheKey = generateCacheKey(config, parentX, parentY, parentWidth, parentHeight);
        
        
        if (isStaticElement(config) && staticElementCache.containsKey(cacheKey)) {
            return staticElementCache.get(cacheKey);
        }
        
        int x = resolvePosition(config.getX(), parentX, parentWidth, context);
        int y = resolvePosition(config.getY(), parentY, parentHeight, context);
        int width = resolveDimension(config.getWidth(), parentWidth, context);
        int height = resolveDimension(config.getHeight(), parentHeight, context);

        UIElementType type = parseElementType(config.getType());

        
        Map<String, String> resolvedProperties = new HashMap<>();
        if (config.getProperties() != null) {
            for (Map.Entry<String, String> entry : config.getProperties().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                
                if (value != null && context != null && !key.equals("entity") && !key.equals("block") && !key.equals("item")) {
                    value = DataBinder.resolveBindings(value, context);
                }
                resolvedProperties.put(key, value);
            }
        }

        List<UIElement> children = new ArrayList<>();
        for (QuestTrackerConfig.ElementConfig childConfig : config.getChildren()) {
            children.add(createElement(childConfig, context, x, y, width, height));
        }

        UIElement element = new UIElementImpl(
            config.getId(),
            type,
            x,
            y,
            width,
            height,
            resolvedProperties,
            children,
            true,
            0
        );
        
        
        if (isStaticElement(config)) {
            staticElementCache.put(cacheKey, element);
        }
        
        return element;
    }
    
    private static String generateCacheKey(QuestTrackerConfig.ElementConfig config, 
                                          int parentX, int parentY, int parentWidth, int parentHeight) {
        StringBuilder key = new StringBuilder();
        key.append(config.getType()).append("|");
        key.append(config.getId()).append("|");
        key.append(config.getX()).append("|");
        key.append(config.getY()).append("|");
        key.append(config.getWidth()).append("|");
        key.append(config.getHeight()).append("|");
        
        if (config.getProperties() != null) {
            for (Map.Entry<String, String> entry : config.getProperties().entrySet()) {
                key.append(entry.getKey()).append("=").append(entry.getValue()).append("|");
            }
        }
        
        return key.toString();
    }
    
    private static boolean isStaticElement(QuestTrackerConfig.ElementConfig config) {
        
        if (hasExpression(config.getX()) || hasExpression(config.getY()) ||
            hasExpression(config.getWidth()) || hasExpression(config.getHeight())) {
            return false;
        }
        
        
        if (config.getProperties() != null) {
            for (String value : config.getProperties().values()) {
                if (value != null && BINDING_PATTERN.matcher(value).find()) {
                    return false;
                }
            }
        }
        
        
        for (QuestTrackerConfig.ElementConfig child : config.getChildren()) {
            if (!isStaticElement(child)) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean hasExpression(String value) {
        if (value == null) return false;
        return SCREEN_WIDTH_PATTERN.matcher(value).matches() ||
               SCREEN_HEIGHT_PATTERN.matcher(value).matches() ||
               WIDTH_PATTERN.matcher(value).matches() ||
               HEIGHT_PATTERN.matcher(value).matches();
    }
    
    public static void clearStaticCache() {
        staticElementCache.clear();
    }
    
    private static int resolvePosition(String expression, int parentPos, int parentDim, Map<String, Object> context) {
        if (expression == null || expression.isEmpty()) return 0;
        
        
        try {
            return Integer.parseInt(expression);
        } catch (NumberFormatException e) {
            
        }
        
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        
        Matcher swMatcher = SCREEN_WIDTH_PATTERN.matcher(expression);
        if (swMatcher.matches()) {
            String op = swMatcher.group(1);
            int value = Integer.parseInt(swMatcher.group(2));
            return op.equals("+") ? screenWidth + value : screenWidth - value;
        }
        
        
        Matcher shMatcher = SCREEN_HEIGHT_PATTERN.matcher(expression);
        if (shMatcher.matches()) {
            String op = shMatcher.group(1);
            int value = Integer.parseInt(shMatcher.group(2));
            return op.equals("+") ? screenHeight + value : screenHeight - value;
        }
        
        
        Matcher wMatcher = WIDTH_PATTERN.matcher(expression);
        if (wMatcher.matches()) {
            String op = wMatcher.group(1);
            int value = Integer.parseInt(wMatcher.group(2));
            return op.equals("+") ? parentDim + value : parentDim - value;
        }
        
        
        Matcher hMatcher = HEIGHT_PATTERN.matcher(expression);
        if (hMatcher.matches()) {
            String op = hMatcher.group(1);
            int value = Integer.parseInt(hMatcher.group(2));
            return op.equals("+") ? parentDim + value : parentDim - value;
        }
        
        
        if (context != null) {
            Object value = context.get(expression);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        
        
        return 0;
    }
    
    private static int resolveDimension(String expression, int parentDim, Map<String, Object> context) {
        if (expression == null || expression.isEmpty()) return 0;
        
        
        try {
            return Integer.parseInt(expression);
        } catch (NumberFormatException e) {
            
        }
        
        
        Matcher wMatcher = WIDTH_PATTERN.matcher(expression);
        if (wMatcher.matches()) {
            String op = wMatcher.group(1);
            int value = Integer.parseInt(wMatcher.group(2));
            return op.equals("+") ? parentDim + value : parentDim - value;
        }
        
        Matcher hMatcher = HEIGHT_PATTERN.matcher(expression);
        if (hMatcher.matches()) {
            String op = hMatcher.group(1);
            int value = Integer.parseInt(hMatcher.group(2));
            return op.equals("+") ? parentDim + value : parentDim - value;
        }
        
        
        if (context != null) {
            Object value = context.get(expression);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        
        
        return 0;
    }
    
    private static UIElementType parseElementType(String type) {
        if (type == null) return UIElementType.PANEL;
        
        return switch (type.toLowerCase()) {
            case "image" -> UIElementType.IMAGE;
            case "button" -> UIElementType.BUTTON;
            case "image_button" -> UIElementType.IMAGE_BUTTON;
            case "text" -> UIElementType.TEXT;
            case "progress_bar" -> UIElementType.PROGRESS_BAR;
            case "list" -> UIElementType.LIST;
            case "panel" -> UIElementType.PANEL;
            case "icon" -> UIElementType.ICON;
            case "entity_render" -> UIElementType.ENTITY_RENDER;
            case "item_render" -> UIElementType.ITEM_RENDER;
            case "block_render" -> UIElementType.BLOCK_RENDER;
            case "tooltip" -> UIElementType.TOOLTIP;
            case "skill_tree" -> UIElementType.SKILL_TREE;
            default -> UIElementType.PANEL;
        };
    }
}
