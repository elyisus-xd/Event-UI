package com.eventui.core.config;

import com.eventui.api.ui.UIElement;
import com.eventui.api.ui.UIElementType;

import java.util.List;
import java.util.Map;

public record UIElementImpl(
        String id,
        UIElementType type,
        int x,
        int y,
        int width,
        int height,
        Map<String, String> properties,
        List<UIElement> children,
        boolean visible,
        int zIndex
) implements UIElement {

    public UIElementImpl {
        properties = properties != null ? Map.copyOf(properties) : Map.of();
        children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public UIElementType getType() {
        return type;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public List<UIElement> getChildren() {
        return children;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public int getZIndex() {
        return zIndex;
    }
}
