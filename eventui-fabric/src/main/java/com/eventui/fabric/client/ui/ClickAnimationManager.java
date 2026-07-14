package com.eventui.fabric.client.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages one-shot click animations for UI elements and skill tree nodes.
 * Progress goes from 1.0 (just clicked) to 0.0 (animation complete).
 */
public class ClickAnimationManager {

    private static final ClickAnimationManager INSTANCE = new ClickAnimationManager();

    private final Map<String, Long>    clickTimestamps = new ConcurrentHashMap<>();
    private final Map<String, String>  clickAnimTypes  = new ConcurrentHashMap<>();
    private final Map<String, Integer> clickDurations  = new ConcurrentHashMap<>();

    private ClickAnimationManager() {}

    public static ClickAnimationManager getInstance() { return INSTANCE; }

    /**
     * Triggers a click animation.
     * @param elementId  unique key (button id, or "treeId:nodeId" for nodes)
     * @param animationType  "punch" | "shake" | "bounce" | "flash"
     * @param durationMs how long the animation plays
     */
    public void triggerClick(String elementId, String animationType, int durationMs) {
        clickTimestamps.put(elementId, System.currentTimeMillis());
        clickAnimTypes.put(elementId,  animationType != null ? animationType : "punch");
        clickDurations.put(elementId,  durationMs > 0 ? durationMs : 150);
    }

    /**
     * Returns progress [1.0 → 0.0] while active, 0.0 when done.
     * Cleans up the entry automatically when the animation finishes.
     */
    public float getProgress(String elementId) {
        Long timestamp = clickTimestamps.get(elementId);
        if (timestamp == null) return 0f;
        int duration = clickDurations.getOrDefault(elementId, 150);
        long elapsed = System.currentTimeMillis() - timestamp;
        if (elapsed >= duration) {
            clickTimestamps.remove(elementId);
            clickAnimTypes.remove(elementId);
            clickDurations.remove(elementId);
            return 0f;
        }
        return 1f - (elapsed / (float) duration);
    }

    public String getAnimationType(String elementId) {
        return clickAnimTypes.getOrDefault(elementId, "punch");
    }

    public boolean isActive(String elementId) {
        return clickTimestamps.containsKey(elementId);
    }
}
