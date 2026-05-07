package com.eventui.fabric.client.ui.animation;

import java.util.ArrayList;
import java.util.List;

public class AnimationManager {

    private static final int MAX_CONCURRENT_ANIMATIONS = 100;

    private final List<Animation> activeAnimations;

    public AnimationManager() {
        this.activeAnimations = new ArrayList<>();
    }


    public void play(Animation animation) {
        if (animation == null) {
            throw new IllegalArgumentException("Animation cannot be null");
        }

        if (activeAnimations.size() >= MAX_CONCURRENT_ANIMATIONS) {
            activeAnimations.remove(0);
        }

        animation.start();
        activeAnimations.add(animation);
    }

    public void tick() {
        for (int i = activeAnimations.size() - 1; i >= 0; i--) {
            Animation animation = activeAnimations.get(i);
            animation.tick();

            if (animation.isFinished()) {
                activeAnimations.remove(i);
            }
        }
    }

    public void stopAll() {
        activeAnimations.clear();
    }

    public void stopIf(java.util.function.Predicate<Animation> predicate) {
        activeAnimations.removeIf(predicate);
    }

    public int getActiveCount() {
        return activeAnimations.size();
    }

    public boolean hasActiveAnimations() {
        return !activeAnimations.isEmpty();
    }

    private boolean paused = false;

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public void tickWithPause() {
        if (paused) return;
        tick();
    }
}
