package com.eventui.fabric.client.ui.animation;

@FunctionalInterface
public interface Easing {

    float apply(float t);

    Easing LINEAR = t -> t;

    Easing EASE_IN_QUAD = t -> t * t;

    Easing EASE_OUT_QUAD = t -> t * (2 - t);

    Easing EASE_IN_OUT_QUAD = t ->
            t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;

    Easing EASE_IN_CUBIC = t -> t * t * t;

    Easing EASE_OUT_CUBIC = t -> {
        float f = t - 1;
        return f * f * f + 1;
    };

    Easing EASE_IN_OUT_CUBIC = t ->
            t < 0.5f ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;

    Easing EASE_OUT_EXPO = t ->
            t == 1.0f ? 1.0f : 1 - (float) Math.pow(2, -10 * t);

    Easing EASE_IN_EXPO = t ->
            t == 0.0f ? 0.0f : (float) Math.pow(2, 10 * (t - 1));

    Easing EASE_OUT_ELASTIC = t -> {
        if (t == 0 || t == 1) return t;

        float p = 0.3f;
        return (float) (Math.pow(2, -10 * t) *
                Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1);
    };

    Easing EASE_OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    };

    Easing EASE_IN_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return c3 * t * t * t - c1 * t * t;
    };

    Easing EASE_OUT_BOUNCE = t -> {
        float n1 = 7.5625f;
        float d1 = 2.75f;

        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5 / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        } else {
            t -= 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    };
}
