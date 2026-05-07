package com.eventui.fabric.client.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class UIStateCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIStateCache.class);
    private static UIStateCache instance;

    private final Map<String, String> variables = new ConcurrentHashMap<>();
    private final List<Consumer<Map<String, String>>> listeners = new ArrayList<>();

    public static UIStateCache getInstance() {
        if (instance == null) instance = new UIStateCache();
        return instance;
    }

    public void updateVariables(Map<String, String> newVariables) {
        variables.putAll(newVariables);
        LOGGER.debug("UIState updated: {} total variables", variables.size());
        notifyListeners();
    }

    public void replaceAll(Map<String, String> newVariables) {
        variables.clear();
        variables.putAll(newVariables);
        LOGGER.debug("UIState replaced: {} total variables", variables.size());
        notifyListeners();
    }

    public String get(String key) {
        return variables.get(key);
    }

    public String get(String key, String defaultValue) {
        return variables.getOrDefault(key, defaultValue);
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(variables);
    }

    public void addListener(Consumer<Map<String, String>> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Map<String, String>> listener) {
        listeners.remove(listener);
    }

    public void clear() {
        variables.clear();
    }

    private void notifyListeners() {
        Map<String, String> snapshot = getAll();
        listeners.forEach(l -> l.accept(snapshot));
    }
}
