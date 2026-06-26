package com.eventui.fabric.client.ui.animation;

/**
 * Clase base abstracta para animaciones basadas en tiempo con easing.
 * Las subclases implementan update(progress) para aplicar el efecto
 * según el progreso normalizado (0.0 - 1.0) tras aplicar el easing.
 */
public abstract class Animation {

    private final long durationMs;
    private final Easing easing;

    private long startTime;
    private boolean started = false;
    private boolean finished = false;

    protected Animation(long durationMs, Easing easing) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs debe ser mayor que 0");
        }
        if (easing == null) {
            throw new IllegalArgumentException("easing no puede ser null");
        }
        this.durationMs = durationMs;
        this.easing = easing;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.started = true;
        this.finished = false;
    }

    public void tick() {
        if (!started || finished) return;

        long elapsed = System.currentTimeMillis() - startTime;
        float rawProgress = Math.min(1.0f, elapsed / (float) durationMs);
        float easedProgress = easing.apply(rawProgress);

        update(easedProgress);

        if (rawProgress >= 1.0f) {
            finished = true;
        }
    }

    protected abstract void update(float progress);

    public boolean isFinished() {
        return finished;
    }

    public boolean isStarted() {
        return started;
    }

    public long getDurationMs() {
        return durationMs;
    }
}