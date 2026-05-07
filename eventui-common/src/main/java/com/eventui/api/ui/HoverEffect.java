package com.eventui.api.ui;

/**
 * Representa un efecto de hover individual.
 * Soporte para múltiples efectos simultáneos.
 */
public class HoverEffect {

    private final String type;
    private final float intensity;
    private final int duration;
    private final String easing;

    public HoverEffect(String type, float intensity, int duration, String easing) {
        this.type = type;
        this.intensity = intensity;
        this.duration = duration;
        this.easing = easing != null ? easing : "ease_out";
    }

    public String getType() { return type; }
    public float getIntensity() { return intensity; }
    public int getDuration() { return duration; }
    public String getEasing() { return easing; }
}
