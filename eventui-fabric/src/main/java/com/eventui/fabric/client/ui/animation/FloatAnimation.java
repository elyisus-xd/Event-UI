package com.eventui.fabric.client.ui.animation;

import java.util.function.Consumer;

public class FloatAnimation extends Animation {

    private final float from;
    private final float to;
    private final Consumer<Float> onUpdate;
    private float currentValue;

    public FloatAnimation(float from, float to, long durationMs,
                          Easing easing, Consumer<Float> onUpdate) {
        super(durationMs, easing);

        if (onUpdate == null) {
            throw new IllegalArgumentException("onUpdate callback cannot be null");
        }

        this.from = from;
        this.to = to;
        this.onUpdate = onUpdate;
        this.currentValue = from;
    }

    @Override
    protected void update(float progress) {
        currentValue = from + (to - from) * progress;
        onUpdate.accept(currentValue);
    }

    public float getCurrentValue() {
        return currentValue;
    }

    public float getFrom() {
        return from;
    }

    public float getTo() {
        return to;
    }

    public static FloatAnimation create(float from, float to, long durationMs,
                                        Consumer<Float> onUpdate) {
        return new FloatAnimation(from, to, durationMs, Easing.EASE_OUT_CUBIC, onUpdate);
    }

    public static FloatAnimation fadeIn(long durationMs, Consumer<Float> onUpdate) {
        return new FloatAnimation(0f, 1f, durationMs, Easing.EASE_OUT_CUBIC, onUpdate);
    }

    public static FloatAnimation fadeOut(long durationMs, Consumer<Float> onUpdate) {
        return new FloatAnimation(1f, 0f, durationMs, Easing.EASE_OUT_CUBIC, onUpdate);
    }
}
