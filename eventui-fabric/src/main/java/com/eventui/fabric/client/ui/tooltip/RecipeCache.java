package com.eventui.fabric.client.ui.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeCache {

    private static final Logger LOG = LoggerFactory.getLogger(RecipeCache.class);
    private static final Map<ResourceLocation, Optional<Recipe<?>>> cache = new ConcurrentHashMap<>();

    public static Optional<Recipe<?>> get(ResourceLocation id) {
        return cache.computeIfAbsent(id, loc -> {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                LOG.debug("RecipeCache: level is null, returning empty for {}", loc);
                return Optional.empty();
            }
            var mgr = level.getRecipeManager();

            var holder = mgr.byKey(loc);
            if (holder.isPresent()) {
                LOG.debug("RecipeCache: cached recipe {} via byKey", loc);
                return Optional.of(holder.get().value());
            }

            for (var recipeHolder : mgr.getRecipes()) {
                if (recipeHolder.id().equals(loc)) {
                    LOG.debug("RecipeCache: cached recipe {} via linear fallback", loc);
                    return Optional.of(recipeHolder.value());
                }
            }
            LOG.debug("RecipeCache: recipe {} not found, caching empty", loc);
            return Optional.empty();
        });
    }

    public static void clear() {
        int size = cache.size();
        cache.clear();
        LOG.info("RecipeCache cleared ({} entries)", size);
    }

    public static int size() {
        return cache.size();
    }
}
