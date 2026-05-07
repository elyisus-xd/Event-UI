package com.eventui.fabric.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TextureAlphaCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextureAlphaCache.class);
    private static final Map<ResourceLocation, AlphaData> cache = new ConcurrentHashMap<>();
    public static AlphaData getAlphaData(ResourceLocation textureLocation) {
        return cache.computeIfAbsent(textureLocation, TextureAlphaCache::loadAlphaData);
    }

    private static AlphaData loadAlphaData(ResourceLocation textureLocation) {
        try {
            var resourceManager = Minecraft.getInstance().getResourceManager();
            Resource resource = resourceManager.getResource(textureLocation)
                    .orElseThrow(() -> new IOException("Texture not found: " + textureLocation));

            try (var inputStream = resource.open()) {
                NativeImage image = NativeImage.read(inputStream);

                int width = image.getWidth();
                int height = image.getHeight();
                boolean[][] alphaMap = new boolean[width][height];

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        int color = image.getPixelRGBA(x, y);
                        int alpha = (color >> 24) & 0xFF;
                        alphaMap[x][y] = alpha > 10;
                    }
                }

                image.close();
                LOGGER.debug("Loaded alpha data for texture: {} ({}x{})",
                        textureLocation, width, height);

                return new AlphaData(width, height, alphaMap);
            }

        } catch (IOException e) {
            LOGGER.warn("Failed to load alpha data for texture: {}", textureLocation, e);
            boolean[][] fallback = new boolean[64][64];
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    fallback[x][y] = true;
                }
            }
            return new AlphaData(64, 64, fallback);
        }
    }

    public static void clear() {
        cache.clear();
        LOGGER.info("Texture alpha cache cleared");
    }

    public static boolean isMouseOverOpaque(AlphaData alphaData,
                                            int elementX, int elementY,
                                            int elementWidth, int elementHeight,
                                            int mouseX, int mouseY) {

        if (mouseX < elementX || mouseX >= elementX + elementWidth ||
                mouseY < elementY || mouseY >= elementY + elementHeight) {
            return false;
        }

        float relativeX = (float)(mouseX - elementX) / elementWidth;
        float relativeY = (float)(mouseY - elementY) / elementHeight;
        int textureX = (int)(relativeX * alphaData.width);
        int textureY = (int)(relativeY * alphaData.height);

        textureX = Math.max(0, Math.min(textureX, alphaData.width - 1));
        textureY = Math.max(0, Math.min(textureY, alphaData.height - 1));


        return alphaData.alphaMap[textureX][textureY];
    }

    public record AlphaData(int width, int height, boolean[][] alphaMap) {}
}
