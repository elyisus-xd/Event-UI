package com.eventui.fabric.client.ui.animation;

import java.util.function.Consumer;

public class ColorAnimation extends Animation {

    private final int fromColor;
    private final int toColor;
    private final Consumer<Integer> onUpdate;
    private int currentColor;

    public ColorAnimation(int fromColor, int toColor, long durationMs,
                          Easing easing, Consumer<Integer> onUpdate) {
        super(durationMs, easing);

        if (onUpdate == null) {
            throw new IllegalArgumentException("onUpdate callback cannot be null");
        }

        this.fromColor = fromColor;
        this.toColor = toColor;
        this.onUpdate = onUpdate;
        this.currentColor = fromColor;
    }

    @Override
    protected void update(float progress) {
        int fromA = (fromColor >> 24) & 0xFF;
        int fromR = (fromColor >> 16) & 0xFF;
        int fromG = (fromColor >> 8) & 0xFF;
        int fromB = fromColor & 0xFF;
        int toA = (toColor >> 24) & 0xFF;
        int toR = (toColor >> 16) & 0xFF;
        int toG = (toColor >> 8) & 0xFF;
        int toB = toColor & 0xFF;

        int currentA = (int) (fromA + (toA - fromA) * progress);
        int currentR = (int) (fromR + (toR - fromR) * progress);
        int currentG = (int) (fromG + (toG - fromG) * progress);
        int currentB = (int) (fromB + (toB - fromB) * progress);

        currentColor = (currentA << 24) | (currentR << 16) | (currentG << 8) | currentB;

        onUpdate.accept(currentColor);
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public int getFromColor() {
        return fromColor;
    }

    public int getToColor() {
        return toColor;
    }

    public static ColorAnimation create(int fromColor, int toColor, long durationMs,
                                        Consumer<Integer> onUpdate) {
        return new ColorAnimation(fromColor, toColor, durationMs,
                Easing.EASE_OUT_CUBIC, onUpdate);
    }

    public static ColorAnimation fadeInColor(int color, long durationMs,
                                             Consumer<Integer> onUpdate) {
        int rgb = color & 0x00FFFFFF;
        int transparentColor = rgb;
        int opaqueColor = 0xFF000000 | rgb;

        return new ColorAnimation(transparentColor, opaqueColor, durationMs,
                Easing.EASE_OUT_CUBIC, onUpdate);
    }

    public static ColorAnimation fadeOutColor(int color, long durationMs,
                                              Consumer<Integer> onUpdate) {
        int rgb = color & 0x00FFFFFF;
        int opaqueColor = 0xFF000000 | rgb;
        int transparentColor = rgb;

        return new ColorAnimation(opaqueColor, transparentColor, durationMs,
                Easing.EASE_OUT_CUBIC, onUpdate);
    }
}
