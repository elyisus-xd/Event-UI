package com.eventui.fabric.client.ui.tooltip.frame;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class RecipeFrameManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeFrameManager.class);
    private static RecipeFrameManager instance;

    private final ResourceManager resourceManager;
    private final Map<String, ResourceLocation> textureCache = new HashMap<>();
    private final Map<String, int[]> frameSizeMap;
    private final RecipeFrameConfig config;

    private RecipeFrameManager(ResourceManager resourceManager, RecipeFrameConfig config) {
        this.resourceManager = resourceManager;
        this.config = config;
        this.frameSizeMap = initializeFrameSizeMap();
    }

    public static synchronized void init(ResourceManager resourceManager, RecipeFrameConfig config) {
        if (instance == null) {
            instance = new RecipeFrameManager(resourceManager, config);
            LOGGER.debug("[RecipeFrameManager] Singleton inicializado");
        }
    }

    public static RecipeFrameManager getInstance() {
        if (instance == null) {
            
            LOGGER.warn("[RecipeFrameManager] getInstance() llamado sin init(). Usando fallback.");
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            instance = new RecipeFrameManager(mc.getResourceManager(), new RecipeFrameConfig());
        }
        return instance;
    }

    private Map<String, int[]> initializeFrameSizeMap() {
        Map<String, int[]> map = new HashMap<>();
        map.put("furnace", new int[]{82, 54});
        map.put("smithing", new int[]{108, 58});
        map.put("anvil", new int[]{125, 56});
        map.put("brewing", new int[]{64, 59});
        map.put("shaped_frame", new int[]{128, 128});
        map.put("shapeless_frame", new int[]{128, 128});
        return map;
    }

    public ResourceLocation resolveFrameTexture(String recipeType, ResourceLocation customOverride) {
        
        if (customOverride != null) {
            ResourceLocation resolved = tryLoadTexture(recipeType, customOverride);
            if (resolved != null) {
                LOGGER.debug("[RecipeFrameManager] Textura custom encontrada para {}: {}", recipeType, customOverride);
                return resolved;
            } else {
                LOGGER.warn("[RecipeFrameManager] Textura custom NO válida para {}: {}. Usando default.", recipeType, customOverride);
            }
        }

        ResourceLocation defaultTexture = config.getDefaultFrameTexture(recipeType);
        if (defaultTexture != null) {
            LOGGER.debug("[RecipeFrameManager] Usando textura default para {}: {}", recipeType, defaultTexture);
            return defaultTexture;
        }
        LOGGER.debug("[RecipeFrameManager] No hay textura default para {}, devolviendo null para usar frame vanilla.", recipeType);
        return null;
    }

    private ResourceLocation tryLoadTexture(String recipeType, ResourceLocation location) {
        
        String cacheKey = recipeType + ":" + location;
        if (textureCache.containsKey(cacheKey)) {
            return textureCache.get(cacheKey);
        }

        try {
            
            ResourceLocation textureLocation = location.getPath().startsWith("textures/") 
                ? location 
                : ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "textures/" + location.getPath());

            if (!textureExists(textureLocation)) {
                LOGGER.warn("[RecipeFrameManager] Textura no encontrada en recursos: {}", textureLocation);
                return null;
            }

            if (!validateFrameTexture(recipeType, textureLocation)) {
                LOGGER.warn("[RecipeFrameManager] Textura tiene tamaño inválido: {} para tipo {}", textureLocation, recipeType);
                return null;
            }

            textureCache.put(cacheKey, textureLocation);
            LOGGER.debug("[RecipeFrameManager] Textura cacheada: {} -> {}", cacheKey, textureLocation);
            return textureLocation;

        } catch (Exception e) {
            LOGGER.warn("[RecipeFrameManager] Error al cargar textura {}: {}", location, e.getMessage());
            return null;
        }
    }

    private boolean textureExists(ResourceLocation location) {
        try {
            
            var resource = resourceManager.getResource(location);
            return resource.isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateFrameTexture(String recipeType, ResourceLocation textureLocation) {
        int[] expectedSize = frameSizeMap.get(recipeType);
        if (expectedSize == null) {
            LOGGER.warn("[RecipeFrameManager] Tipo de receta desconocido para validación: {}", recipeType);
            return true; 
        }

        LOGGER.debug("[RecipeFrameManager] Validación de tamaño para {} (esperado: {}x{})", 
            recipeType, expectedSize[0], expectedSize[1]);
        return true;
    }

    public int[] getFrameSize(String recipeType) {
        return frameSizeMap.getOrDefault(recipeType, new int[]{128, 128});
    }

    public void clearCache() {
        textureCache.clear();
        LOGGER.debug("[RecipeFrameManager] Caché limpiado");
    }

    public int getCacheSize() {
        return textureCache.size();
    }
}
