package com.eventui.api.ui;

/**
 * Define una animación de hover para botones.
 * Sistema de animaciones
 *
 * @param duration  Milisegundos
 * @param intensity Multiplicador del efecto
 * @param easing    linear, ease_in, ease_out, ease_in_out
 */
public record HoverAnimation(AnimationType type, int duration, float intensity, String easing) {

    public HoverAnimation(AnimationType type, int duration, float intensity, String easing) {
        this.type = type;
        this.duration = duration;
        this.intensity = intensity;
        this.easing = easing != null ? easing : "ease_out";
    }

    public enum AnimationType {
        NONE,
        ZOOM_IN,
        ZOOM_OUT,
        GLOW,
        SHAKE,
        BOUNCE,
        PULSE,
        ROTATE,
        SWING,
        FLOAT,
        FLIP_X,
        FLIP_Y,
        WAVE,
        HEARTBEAT,
        JELLY,
        SPIN_3D,
        SLIDE_LEFT,
        SLIDE_RIGHT,
        FADE_IN,
        RIPPLE
    }
}
