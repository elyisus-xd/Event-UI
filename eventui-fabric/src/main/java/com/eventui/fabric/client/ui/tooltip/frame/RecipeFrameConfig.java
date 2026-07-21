package com.eventui.fabric.client.ui.tooltip.frame;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RecipeFrameConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeFrameConfig.class);

    private boolean enabled = true;
    private FallbackMode fallbackMode = FallbackMode.AUTOMATIC;
    private String globalNamespace = "eventui";
    
    private final Map<String, ResourceLocation> defaultFrameTextures = new HashMap<>();

    public enum FallbackMode {
        AUTOMATIC,  
        ERROR       
    }

    public RecipeFrameConfig() {
        initializeDefaultTextures();
    }

    private void initializeDefaultTextures() {
        
        defaultFrameTextures.put("furnace", 
            ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/furnace.png"));
        defaultFrameTextures.put("smithing", 
            ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/smithing.png"));
        defaultFrameTextures.put("anvil", 
            ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/anvil.png"));
        defaultFrameTextures.put("brewing", 
            ResourceLocation.fromNamespaceAndPath("eventui", "textures/gui/recipes/brewing.png"));

    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FallbackMode getFallbackMode() {
        return fallbackMode;
    }

    public void setFallbackMode(FallbackMode fallbackMode) {
        this.fallbackMode = fallbackMode;
    }

    public void setFallbackMode(String modeName) {
        try {
            this.fallbackMode = FallbackMode.valueOf(modeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown fallback mode: {}. Using AUTOMATIC.", modeName);
            this.fallbackMode = FallbackMode.AUTOMATIC;
        }
    }

    public String getGlobalNamespace() {
        return globalNamespace;
    }

    public void setGlobalNamespace(String globalNamespace) {
        this.globalNamespace = globalNamespace;
    }

    public ResourceLocation getDefaultFrameTexture(String recipeType) {
        return defaultFrameTextures.get(recipeType);
    }

    public boolean hasDefaultFrameTexture(String recipeType) {
        return defaultFrameTextures.containsKey(recipeType);
    }

    public void setDefaultFrameTexture(String recipeType, ResourceLocation texture) {
        defaultFrameTextures.put(recipeType, texture);
        LOGGER.info("Set default texture for {}: {}", recipeType, texture);
    }

    public void logConfig() {
        LOGGER.info("===== RecipeFrameConfig =====");
        LOGGER.info("Enabled: {}", enabled);
        LOGGER.info("Fallback Mode: {}", fallbackMode);
        LOGGER.info("Global Namespace: {}", globalNamespace);
        LOGGER.info("Default Textures:");
        defaultFrameTextures.forEach((type, texture) -> 
            LOGGER.info("  {}: {}", type, texture)
        );
        LOGGER.info("=============================");
    }
}
