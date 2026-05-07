package com.eventui.core.config;

import com.eventui.api.ui.UIConfig;
import com.eventui.api.ui.UIElement;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record UIConfigImpl(
        String id,
        String title,
        int screenWidth,
        int screenHeight,
        List<UIElement> rootElements,
        String associatedEventId,
        Map<String, String> screenProperties
) implements UIConfig {

    public UIConfigImpl {
        rootElements = rootElements != null ? Collections.unmodifiableList(List.copyOf(rootElements)) : List.of();
        screenProperties = screenProperties != null ? Collections.unmodifiableMap(Map.copyOf(screenProperties)) : Map.of();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public int getScreenWidth() {
        return screenWidth;
    }

    @Override
    public int getScreenHeight() {
        return screenHeight;
    }

    @Override
    public List<UIElement> getRootElements() {
        return rootElements;
    }

    @Override
    public String getAssociatedEventId() {
        return associatedEventId;
    }

    @Override
    public Map<String, String> getScreenProperties() {
        return screenProperties;
    }
}
