package com.eventui.fabric.client.ui.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UISoundHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UISoundHandler.class);

    public static void playSound(String soundId, float volume, float pitch) {
        if (soundId == null || soundId.isEmpty()) return;
        try {
            ResourceLocation soundLocation = ResourceLocation.parse(soundId);
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(soundEvent, pitch, volume)
            );
        } catch (Exception e) {
            LOGGER.error("Failed to play sound: {}", soundId, e);
        }
    }

    public static void playSound(String soundId) {
        playSound(soundId, 1.0f, 1.0f);
    }

    public static class Sounds {
        public static final String BUTTON_HOVER = "minecraft:ui.button.click";
        public static final String BUTTON_CLICK = "minecraft:ui.button.click";
        public static final String QUEST_COMPLETE = "minecraft:entity.player.levelup";
        public static final String QUEST_START = "minecraft:block.note_block.pling";
        public static final String UI_OPEN = "minecraft:block.chest.open";
        public static final String UI_CLOSE = "minecraft:block.chest.close";
        public static final String ERROR = "minecraft:block.note_block.bass";
        public static final String SUCCESS = "minecraft:entity.experience_orb.pickup";
    }
}
