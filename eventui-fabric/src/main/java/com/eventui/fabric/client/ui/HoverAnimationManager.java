package com.eventui.fabric.client.ui;

import com.eventui.api.ui.HoverAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class HoverAnimationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(HoverAnimationManager.class);
    private final Map<String, AnimationState> activeAnimations = new ConcurrentHashMap<>();

    public void startAnimation(String elementId, HoverAnimation animation) {
        if (animation == null) return;

        AnimationState state = activeAnimations.get(elementId);

        if (state == null) {
            state = new AnimationState(animation, System.currentTimeMillis());
            activeAnimations.put(elementId, state);
        } else if (state.isExiting) {
            float currentProgress = getProgress(elementId);
            long virtualElapsed = (long)(currentProgress * animation.duration());
            state.isExiting = false;
            state.exitStartTime = 0L;
            activeAnimations.put(elementId,
                    new AnimationState(animation, System.currentTimeMillis() - virtualElapsed));
        } else if (!state.animation.equals(animation)) {
            state = new AnimationState(animation, System.currentTimeMillis());
            activeAnimations.put(elementId, state);
        }
    }

    public void stopAnimation(String elementId) {
        AnimationState state = activeAnimations.get(elementId);
        if (state == null) return;
        if (!state.isExiting) {
            state.isExiting = true;
            state.exitStartTime = System.currentTimeMillis();
            state.exitStartProgress = getProgress(elementId);
        }
    }

    public float getProgress(String elementId) {
        AnimationState state = activeAnimations.get(elementId);
        if (state == null) return 0.0f;

        if (state.isExiting) {
            long exitDuration = (long)(state.animation.duration() * 0.6f);
            long elapsed = System.currentTimeMillis() - state.exitStartTime;
            float exitT = Math.min(1.0f, elapsed / (float) exitDuration);
            float progress = state.exitStartProgress * (1.0f - exitT);
            if (progress <= 0.001f) {
                activeAnimations.remove(elementId);
                return 0.0f;
            }

            return progress;
        }

        long elapsed = System.currentTimeMillis() - state.startTime;
        float progress = Math.min(1.0f, elapsed / (float) state.animation.duration());
        return applyEasing(progress, state.animation.easing());
    }

    public void applyTransform(String elementId, PoseStack poseStack, int x, int y, int width, int height) {
        AnimationState state = activeAnimations.get(elementId);
        if (state == null) {
            return;
        }

        float progress = getProgress(elementId);
        HoverAnimation anim = state.animation;

        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        switch (anim.type()) {
            case ZOOM_IN:
                applyZoom(poseStack, centerX, centerY, progress, anim.intensity());
                break;
            case ZOOM_OUT:
                applyZoom(poseStack, centerX, centerY, progress, 2.0f - anim.intensity());
                break;
            case SHAKE:
                applyShake(poseStack, progress, anim.intensity());
                break;
            case BOUNCE:
                applyBounce(poseStack, y, progress, anim.intensity());
                break;
            case ROTATE:
                applyRotate(poseStack, centerX, centerY, progress, anim.intensity());
                break;
            case SWING:
                applySwing(poseStack, centerX, centerY, progress, anim.intensity());
                break;
            case FLOAT:
                applyFloat(poseStack, progress, anim.intensity());
                break;
            case WAVE:
                applyWave(poseStack, progress, anim.intensity());
                break;
            case HEARTBEAT:
                applyHeartbeat(poseStack, centerX, centerY, progress, anim.intensity());
                break;
            case JELLY:
                applyJelly(poseStack, centerX, centerY, progress, anim.intensity());
                break;
            case SPIN_3D:
                applySpin3D(poseStack, centerX, centerY, progress, anim.intensity());
                break;
        }
    }

    public int getGlowColor(String elementId) {
        AnimationState state = activeAnimations.get(elementId);
        if (state == null || state.animation.type() != HoverAnimation.AnimationType.GLOW) {
            return 0;
        }

        float progress = getProgress(elementId);
        int alpha = (int) (progress * 80);

        return (alpha << 24) | 0xFFD700;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        activeAnimations.entrySet().removeIf(entry -> {
            long elapsed = now - entry.getValue().startTime;
            return elapsed > entry.getValue().animation.duration() + 1000;
        });
    }

    private void applyZoom(PoseStack poseStack, float centerX, float centerY,
                           float progress, float targetScale) {
        float scale = 1.0f + (targetScale - 1.0f) * progress;

        poseStack.translate(centerX, centerY, 0);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-centerX, -centerY, 0);
    }

    private void applyShake(PoseStack poseStack, float progress, float intensity) {
        float shake = (float) Math.sin(progress * Math.PI * 6) * intensity * (1.0f - progress);
        poseStack.translate(shake, 0, 0);
    }

    private void applyBounce(PoseStack poseStack, float y, float progress, float intensity) {
        float bounce = (float) Math.abs(Math.sin(progress * Math.PI)) * intensity * 3;
        poseStack.translate(0, -bounce, 0);
    }

    private void applyRotate(PoseStack poseStack, float centerX, float centerY,
                             float progress, float maxDegrees) {
        float angle = progress * maxDegrees;

        poseStack.translate(centerX, centerY, 0);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
        poseStack.translate(-centerX, -centerY, 0);
    }

    private void applySwing(PoseStack poseStack, float centerX, float centerY,
                            float progress, float intensity) {
        long time = System.currentTimeMillis();
        float swingAngle = (float) Math.sin(time * 0.003) * intensity * 15.0f;

        poseStack.translate(centerX, centerY, 0);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(swingAngle));
        poseStack.translate(-centerX, -centerY, 0);
    }

    private void applyFloat(PoseStack poseStack, float progress, float intensity) {
        long time = System.currentTimeMillis();
        float floatOffset = (float) Math.sin(time * 0.002) * intensity * 5.0f;
        poseStack.translate(0, floatOffset, 0);
    }

    private void applyWave(PoseStack poseStack, float progress, float intensity) {
        long time = System.currentTimeMillis();
        float waveX = (float) Math.sin(time * 0.002) * intensity * 3.0f;
        float waveY = (float) Math.cos(time * 0.003) * intensity * 3.0f;
        poseStack.translate(waveX, waveY, 0);
    }

    private void applyHeartbeat(PoseStack poseStack, float centerX, float centerY,
                                float progress, float intensity) {
        long time = System.currentTimeMillis();
        float beat = (float) Math.abs(Math.sin(time * 0.004)) * intensity * 0.1f;
        float scale = 1.0f + beat;

        poseStack.translate(centerX, centerY, 0);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-centerX, -centerY, 0);
    }

    private void applyJelly(PoseStack poseStack, float centerX, float centerY,
                            float progress, float intensity) {
        long time = System.currentTimeMillis();
        float jellyX = 1.0f + (float) Math.sin(time * 0.004) * intensity * 0.1f;
        float jellyY = 1.0f - (float) Math.sin(time * 0.004) * intensity * 0.1f;

        poseStack.translate(centerX, centerY, 0);
        poseStack.scale(jellyX, jellyY, 1.0f);
        poseStack.translate(-centerX, -centerY, 0);
    }

    private void applySpin3D(PoseStack poseStack, float centerX, float centerY,
                             float progress, float intensity) {
        long time = System.currentTimeMillis();
        float angle3D = (time * intensity * 0.1f) % 360.0f;

        poseStack.translate(centerX, centerY, 0);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle3D));
        poseStack.translate(-centerX, -centerY, 0);
    }

    private float applyEasing(float t, String easing) {
        return switch (easing.toLowerCase()) {
            case "linear" -> t;
            case "ease_in" -> t * t;
            case "ease_out" -> t * (2.0f - t);
            case "ease_in_out" -> t < 0.5f ? 2.0f * t * t : -1.0f + (4.0f - 2.0f * t) * t;
            default -> t * (2.0f - t);
        };
    }

    private static class AnimationState {
        final HoverAnimation animation;
        final long startTime;

        boolean isExiting = false;
        long exitStartTime = 0L;
        float exitStartProgress = 1.0f;

        AnimationState(HoverAnimation animation, long startTime) {
            this.animation = animation;
            this.startTime = startTime;
        }
    }
}
