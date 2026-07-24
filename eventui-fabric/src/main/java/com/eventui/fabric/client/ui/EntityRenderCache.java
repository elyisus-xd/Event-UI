package com.eventui.fabric.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class EntityRenderCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityRenderCache.class);
    private static final Map<String, LivingEntity> cache = new HashMap<>();

    public static LivingEntity getOrCreate(String entityId) {
        return cache.computeIfAbsent(entityId, id -> {
            try {
                var mc = Minecraft.getInstance();
                if (mc.level == null) return null;

                ResourceLocation loc = ResourceLocation.tryParse(id);
                if (loc == null) {
                    LOGGER.warn("Invalid entity ID: {}", id);
                    return null;
                }

                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(loc)) {
                    LOGGER.warn("Unknown entity type: {}", id);
                    return null;
                }

                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(loc);
                var entity = type.create(mc.level);

                if (entity instanceof LivingEntity living) {
                    LOGGER.info("Created render entity: {}", id);
                    return living;
                }

                LOGGER.warn("Entity {} is not a LivingEntity, cannot render", id);
                return null;

            } catch (Exception e) {
                LOGGER.error("Failed to create render entity: {}", id, e);
                return null;
            }
        });
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (LivingEntity entity : cache.values()) {
            if (entity != null) {
                entity.tickCount++;
            }
        }
    }

    public static void clear() {
        LOGGER.info("Clearing entity render cache ({} entries)", cache.size());
        cache.clear();
    }
}
