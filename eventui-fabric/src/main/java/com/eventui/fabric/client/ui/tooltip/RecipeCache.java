package com.eventui.fabric.client.ui.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché de recetas para evitar búsquedas O(n) en el RecipeManager cada frame.
 * La búsqueda lineal (fallback) solo se ejecuta una vez por ResourceLocation;
 * las siguientes llamadas devuelven el resultado cacheado.
 *
 * Debe limpiarse cuando el servidor recarga las recetas (EVENT_RELOAD_NOTIFICATION).
 */
public class RecipeCache {

    private static final Logger LOG = LoggerFactory.getLogger(RecipeCache.class);
    private static final Map<ResourceLocation, Optional<Recipe<?>>> cache = new ConcurrentHashMap<>();

    /**
     * Devuelve la receta cacheada para el id dado.
     * Si no está en caché, la busca (una sola vez) usando byKey + fallback lineal.
     */
    public static Optional<Recipe<?>> get(ResourceLocation id) {
        return cache.computeIfAbsent(id, loc -> {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                LOG.debug("RecipeCache: level is null, returning empty for {}", loc);
                return Optional.empty();
            }
            var mgr = level.getRecipeManager();

            // Intento 1: byKey (O(1))
            var holder = mgr.byKey(loc);
            if (holder.isPresent()) {
                LOG.debug("RecipeCache: cached recipe {} via byKey", loc);
                return Optional.of(holder.get().value());
            }

            // Intento 2: búsqueda lineal (solo si byKey falla)
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

    /**
     * Limpia toda la caché. Debe llamarse cuando el servidor recarga
     * (EVENT_RELOAD_NOTIFICATION) para forzar re-búsqueda de recetas.
     */
    public static void clear() {
        int size = cache.size();
        cache.clear();
        LOG.info("RecipeCache cleared ({} entries)", size);
    }

    /**
     * @return Número de entradas actualmente en caché (para debugging).
     */
    public static int size() {
        return cache.size();
    }
}
