package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIElement;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.bridge.SkillNodeData;
import com.eventui.fabric.client.bridge.SkillRequirementData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items; // Assuming this is how to get ItemStacks
import net.minecraft.core.registries.BuiltInRegistries; // For getting Item from ResourceLocation
import net.minecraft.world.item.Item;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class SkillTreeRenderer {

    // Hover animation manager shared across all nodes
    private static final HoverAnimationManager nodeHoverAnimManager =
            new HoverAnimationManager();

    // Tracks which nodes were hovered last frame (for hover sound trigger)
    private static final java.util.Set<String> previouslyHoveredNodes =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());


    public static void render(GuiGraphics graphics, Font font,
                              UIElement element, int elementX, int elementY,
                              int elementWidth, int elementHeight,
                              Map<String, Object> context,
                              int mouseX, int mouseY,
                              int screenMouseX, int screenMouseY) {


        // Paso 1: Verificar si skillDataDirty y resetear
        if (ClientEventBridge.skillDataDirty) {
            ClientEventBridge.skillDataDirty = false;
        }

        // Paso 2: Obtener propiedades del YAML
        Map<String, String> properties = element.getProperties();
        String treeId = properties.get("tree_id");
        if (treeId == null) {
            // Esto debería ser un error, pero por ahora, no renderizamos nada
            graphics.drawString(font, "§cError: tree_id no especificado para SKILL_TREE",
                    elementX + elementWidth / 2 - font.width("§cError: tree_id no especificado para SKILL_TREE") / 2,
                    elementY + elementHeight / 2, 0xFF0000, false);
            return;
        }

        int nodeSize = Integer.parseInt(properties.getOrDefault("node_size", "48"));
        int nodeSpacingX = Integer.parseInt(properties.getOrDefault("node_spacing_x", "100"));
        int nodeSpacingY = Integer.parseInt(properties.getOrDefault("node_spacing_y", "80"));
        boolean showLevelText = Boolean.parseBoolean(properties.getOrDefault("show_level_text", "true"));

        int connectionColorLocked = parseHexColor(properties.getOrDefault("connection_color_locked", "555555"));
        int connectionColorAvailable = parseHexColor(properties.getOrDefault("connection_color_available", "aaaaaa"));
        int connectionColorUnlocked = parseHexColor(properties.getOrDefault("connection_color_unlocked", "5cb85c"));

        ResourceLocation nodeTextureLocked = parseResourceLocation(properties.get("node_texture_locked"));
        ResourceLocation nodeTextureAvailable = parseResourceLocation(properties.get("node_texture_available"));
        ResourceLocation nodeTexturePartial = parseResourceLocation(properties.get("node_texture_partial"));
        ResourceLocation nodeTextureMaxed = parseResourceLocation(properties.get("node_texture_maxed"));

        // Parse hover animation properties
        String hoverAnimStr = properties.getOrDefault("hover_animation", "none");
        String hoverAnimEasing = properties.getOrDefault("hover_animation_easing", "ease_out");
        float hoverAnimIntensity = Float.parseFloat(
                properties.getOrDefault("hover_animation_intensity", "1.1"));
        int hoverAnimDuration = Integer.parseInt(
                properties.getOrDefault("hover_animation_duration", "200"));
        String hoverSoundId = properties.get("hover_sound");
        float hoverSoundVolume = Float.parseFloat(
                properties.getOrDefault("hover_sound_volume", "0.5"));
        float hoverSoundPitch = Float.parseFloat(
                properties.getOrDefault("hover_sound_pitch", "1.0"));

        // Parse click animation properties
        String clickAnimStr = properties.getOrDefault("click_animation", "none");
        int clickAnimDuration = Integer.parseInt(
                properties.getOrDefault("click_animation_duration", "150"));
        String clickSoundId = properties.get("click_sound");
        float clickSoundVolume = Float.parseFloat(
                properties.getOrDefault("click_sound_volume", "1.0"));
        float clickSoundPitch = Float.parseFloat(
                properties.getOrDefault("click_sound_pitch", "1.0"));

        com.eventui.api.ui.HoverAnimation.AnimationType hoverAnimType =
            switch (hoverAnimStr.toLowerCase()) {
                case "zoom_in"    -> com.eventui.api.ui.HoverAnimation.AnimationType.ZOOM_IN;
                case "zoom_out"   -> com.eventui.api.ui.HoverAnimation.AnimationType.ZOOM_OUT;
                case "shake"      -> com.eventui.api.ui.HoverAnimation.AnimationType.SHAKE;
                case "bounce"     -> com.eventui.api.ui.HoverAnimation.AnimationType.BOUNCE;
                case "rotate"     -> com.eventui.api.ui.HoverAnimation.AnimationType.ROTATE;
                case "swing"      -> com.eventui.api.ui.HoverAnimation.AnimationType.SWING;
                case "float"      -> com.eventui.api.ui.HoverAnimation.AnimationType.FLOAT;
                case "wave"       -> com.eventui.api.ui.HoverAnimation.AnimationType.WAVE;
                case "heartbeat"  -> com.eventui.api.ui.HoverAnimation.AnimationType.HEARTBEAT;
                case "jelly"      -> com.eventui.api.ui.HoverAnimation.AnimationType.JELLY;
                case "spin_3d"    -> com.eventui.api.ui.HoverAnimation.AnimationType.SPIN_3D;
                default           -> null; // null = no hover animation
            };

        com.eventui.api.ui.HoverAnimation hoverAnim = (hoverAnimType != null)
            ? new com.eventui.api.ui.HoverAnimation(
                    hoverAnimType, hoverAnimDuration, hoverAnimIntensity, hoverAnimEasing)
            : null;

        // Paso 2 (continuación): Obtener el árbol del caché
        var cache = ClientEventBridge.getInstance().getCache();
        var trees = cache.getCachedSkillTrees();
        var tree = trees.get(treeId);

        if (tree == null) {
            graphics.drawString(font, "§7Cargando árbol...",
                    elementX + elementWidth / 2 - font.width("§7Cargando árbol...") / 2,
                    elementY + elementHeight / 2, 0xAAAAAA, false);
            return;
        }

        // Paso 3: Calcular posición en píxeles de cada nodo
        Map<String, int[]> nodePosMap = new HashMap<>();
        for (Map.Entry<String, SkillNodeData> entry : tree.nodes().entrySet()) {
            String nodeId = entry.getKey();
            SkillNodeData node = entry.getValue();
            int nodePixelX = elementX + (node.positionX() * nodeSpacingX);
            int nodePixelY = elementY + (node.positionY() * nodeSpacingY);
            nodePosMap.put(nodeId, new int[]{nodePixelX, nodePixelY});
        }

        // Detect hovered node with texture alpha check
        String hoveredNodeId = null;
        for (Map.Entry<String, int[]> posEntry : nodePosMap.entrySet()) {
            int[] pos = posEntry.getValue();
            if (mouseX >= pos[0] && mouseX <= pos[0] + nodeSize &&
                    mouseY >= pos[1] && mouseY <= pos[1] + nodeSize) {
                String candidateId = posEntry.getKey();
                SkillNodeData candidateNode = tree.nodes().get(candidateId);
                // Texture alpha check (always on for nodes with textures)
                ResourceLocation candidateTexture = getEffectiveNodeTexture(
                        candidateNode, nodeTextureLocked, nodeTextureAvailable,
                        nodeTexturePartial, nodeTextureMaxed);
                if (candidateTexture != null) {
                    TextureAlphaCache.AlphaData alphaData =
                            TextureAlphaCache.getAlphaData(candidateTexture);
                    if (!TextureAlphaCache.isMouseOverOpaque(
                            alphaData, pos[0], pos[1], nodeSize, nodeSize, mouseX, mouseY)) {
                        continue; // transparent pixel — not hovered
                    }
                }
                hoveredNodeId = candidateId;
                break;
            }
        }


        // Paso 4: Dibujar líneas de conexión en L (ANTES que los nodos)
        for (Map.Entry<String, SkillNodeData> entry : tree.nodes().entrySet()) {
            String childNodeId = entry.getKey();
            SkillNodeData node = entry.getValue();
            if (node.requires() != null && !node.requires().isEmpty()) {
                int[] childPos = nodePosMap.get(childNodeId);
                if (childPos == null) continue; // Should not happen

                int childCenterX = childPos[0] + nodeSize / 2;
                int childCenterY = childPos[1] + nodeSize / 2;

                int connectionColor = switch (node.state()) {
                    case "LOCKED" -> connectionColorLocked;
                    case "AVAILABLE" -> connectionColorAvailable;
                    case "PARTIAL", "MAXED" -> connectionColorUnlocked;
                    default -> 0xFF555555; // Default to locked color
                };

                for (SkillRequirementData requirement : node.requires()) {
                    String requiredNodeId = requirement.nodeId(); // Corrected from requirement.id() to requirement.nodeId()
                    int[] parentPos = nodePosMap.get(requiredNodeId);
                    if (parentPos == null) continue; // Should not happen

                    int parentCenterX = parentPos[0] + nodeSize / 2;
                    int parentCenterY = parentPos[1] + nodeSize / 2;

                    // Horizontal line: from parentCenterX to childCenterX, at childCenterY
                    graphics.fill(
                            Math.min(parentCenterX, childCenterX), childCenterY - 1,
                            Math.max(parentCenterX, childCenterX), childCenterY + 1,
                            connectionColor
                    );

                    // Vertical line: from parentCenterY to childCenterY, at parentCenterX
                    graphics.fill(
                            parentCenterX - 1, Math.min(parentCenterY, childCenterY),
                            parentCenterX + 1, Math.max(parentCenterY, childCenterY),
                            connectionColor
                    );
                }
            }
        }

        // Paso 5: Dibujar nodos encima de las líneas
        Set<String> currentlyHovered = new java.util.HashSet<>();

        for (Map.Entry<String, SkillNodeData> entry : tree.nodes().entrySet()) {
            String nodeId = entry.getKey();
            SkillNodeData node = entry.getValue();
            int[] nodePos = nodePosMap.get(nodeId);
            if (nodePos == null) continue;

            int nodePixelX = nodePos[0];
            int nodePixelY = nodePos[1];
            boolean isHovered = nodeId.equals(hoveredNodeId);
            String animKey = treeId + ":" + nodeId;
            String clickKey = "click:" + animKey;

            // --- Hover animation state ---
            if (isHovered) {
                currentlyHovered.add(nodeId);
                if (hoverAnim != null) {
                    nodeHoverAnimManager.startAnimation(animKey, hoverAnim);
                }
                // Hover sound: only on first frame of hover
                if (!previouslyHoveredNodes.contains(nodeId)
                        && hoverSoundId != null && !hoverSoundId.isEmpty()) {
                    com.eventui.fabric.client.ui.sound.UISoundHandler.playSound(
                            hoverSoundId, hoverSoundVolume, hoverSoundPitch);
                }
            } else {
                nodeHoverAnimManager.stopAnimation(animKey);
            }

            float hoverProgress = nodeHoverAnimManager.getProgress(animKey);
            float clickProgress = ClickAnimationManager.getInstance().getProgress(clickKey);
            String activeClickAnim = ClickAnimationManager.getInstance().getAnimationType(clickKey);

            boolean hasTransform = hoverProgress > 0f || (clickProgress > 0f
                    && !"flash".equals(activeClickAnim) && !"none".equals(activeClickAnim));

            if (hasTransform) {
                graphics.pose().pushPose();
                if (hoverProgress > 0f && hoverAnim != null) {
                    nodeHoverAnimManager.applyTransform(animKey, graphics.pose(),
                            nodePixelX, nodePixelY, nodeSize, nodeSize);
                }
                if (clickProgress > 0f) {
                    applyClickTransform(graphics.pose(), activeClickAnim, clickProgress,
                            nodePixelX, nodePixelY, nodeSize);
                }
            }

            // a) Determine texture and fallback colors
            ResourceLocation nodeTexture = getEffectiveNodeTexture(node,
                    nodeTextureLocked, nodeTextureAvailable, nodeTexturePartial, nodeTextureMaxed);
            int fallbackColor;
            int borderColor;
            switch (node.state()) {
                case "LOCKED"    -> { fallbackColor = 0xFF555555; borderColor = 0xFF333333; }
                case "AVAILABLE" -> { fallbackColor = 0xFF888888; borderColor = 0xFF666666; }
                case "PARTIAL"   -> { fallbackColor = 0xFF5c8a5c; borderColor = 0xFF3a5a3a; }
                case "MAXED"     -> { fallbackColor = 0xFF5db85c; borderColor = 0xFF3a7a3a; }
                default          -> { fallbackColor = 0xFF000000; borderColor = 0xFF000000; }
            }

            // b) Draw node background
            if (nodeTexture == null) {
                graphics.fill(nodePixelX, nodePixelY,
                        nodePixelX + nodeSize, nodePixelY + nodeSize, borderColor);
                graphics.fill(nodePixelX + 1, nodePixelY + 1,
                        nodePixelX + nodeSize - 1, nodePixelY + nodeSize - 1, fallbackColor);
            } else {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                graphics.blit(nodeTexture, nodePixelX, nodePixelY,
                        0, 0, nodeSize, nodeSize, nodeSize, nodeSize);
            }

            // c) Icon (scaled)
            if (node.icon() != null && !node.icon().isEmpty()) {
                ItemStack itemStack = getItemStackFromId(node.icon());
                if (!itemStack.isEmpty()) {
                    float itemScale = (nodeSize * 0.6f) / 16f;
                    float itemCenterX = nodePixelX + nodeSize / 2f;
                    float itemCenterY = nodePixelY + nodeSize / 2f;
                    var poseStack = graphics.pose();
                    poseStack.pushPose();
                    poseStack.translate(itemCenterX, itemCenterY, 200f);
                    poseStack.scale(itemScale, itemScale, itemScale);
                    poseStack.translate(-8f, -8f, 0f);
                    graphics.renderItem(itemStack, 0, 0);
                    poseStack.popPose();
                }
            }

            // d) Level text
            if (showLevelText && !Objects.equals(node.state(), "LOCKED")) {
                String levelText = switch (node.state()) {
                    case "MAXED"                    -> "MAX";
                    case "PARTIAL", "AVAILABLE"     ->
                            node.currentLevel() + "/" + node.maxLevel();
                    default -> "";
                };
                if (!levelText.isEmpty()) {
                    int textWidth = font.width(levelText);
                    int textX = nodePixelX + nodeSize - textWidth - 2;
                    int textY = nodePixelY + nodeSize - 10;
                    graphics.drawString(font, levelText, textX, textY, 0xFFFFFF, true);
                }
            }


            if (hasTransform) {
                graphics.pose().popPose();
            }

            // f) Flash overlay (after pop so it's not scaled)
            if (clickProgress > 0f && "flash".equals(activeClickAnim)) {
                int flashAlpha = (int)(clickProgress * 160);
                graphics.fill(nodePixelX + 1, nodePixelY + 1,
                        nodePixelX + nodeSize - 1, nodePixelY + nodeSize - 1,
                        (flashAlpha << 24) | 0xFFFFFF);
            }
        }

        // Update hover tracking for next frame
        previouslyHoveredNodes.clear();
        previouslyHoveredNodes.addAll(currentlyHovered);

        // Detectar nodo bajo el cursor y renderizar tooltip vanilla
        if (context.containsKey("mouseX") && context.containsKey("mouseY")) {
            int mx = (int) context.get("mouseX");
            int my = (int) context.get("mouseY");
            
            for (SkillNodeData node : tree.nodes().values()) {
                int[] pos = nodePosMap.get(node.id());
                if (pos == null) continue;
                if (mx >= pos[0] && mx <= pos[0] + nodeSize 
                        && my >= pos[1] && my <= pos[1] + nodeSize) {
                    // Construir tooltip vanilla
                    List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                    lines.add(net.minecraft.network.chat.Component.literal("§6" + node.displayName()));
                    lines.add(net.minecraft.network.chat.Component.literal("§7" + node.description()));
                    lines.add(net.minecraft.network.chat.Component.literal(""));
                    lines.add(net.minecraft.network.chat.Component.literal(
                        "§eNivel: §f" + node.currentLevel() + "/" + node.maxLevel()));
                    if (!"MAXED".equals(node.state()) && node.costNextLevel() > 0) {
                        lines.add(net.minecraft.network.chat.Component.literal(
                            "§eCosto siguiente nivel: §f" + node.costNextLevel()));
                    }
                    
                    // Requisitos
                    List<SkillRequirementData> requires = node.requires();
                    if (requires != null && !requires.isEmpty()) {
                        lines.add(net.minecraft.network.chat.Component.literal(""));
                        lines.add(net.minecraft.network.chat.Component.literal("§7Requisitos:"));
                        
                        for (SkillRequirementData req : requires) {
                            SkillNodeData reqNode = tree.nodes().get(req.nodeId());
                            String reqText = getString(req, reqNode);
                            lines.add(net.minecraft.network.chat.Component.literal(reqText));
                        }
                    }
                    
                    graphics.renderTooltip(font, lines, java.util.Optional.empty(), mx, my);
                    break;
                }
            }
        }
    }

    private static @NotNull String getString(SkillRequirementData req, SkillNodeData reqNode) {
        int reqCurrentLevel = (reqNode != null) ? reqNode.currentLevel() : 0;
        boolean met = reqCurrentLevel >= req.minLevel();

        String reqName = (reqNode != null) ? reqNode.displayName() : req.nodeId();
        String checkmark = met ? "§a✔ " : "§c✘ ";
        String reqText = checkmark + "§f" + reqName
                + " §7(Nivel " + req.minLevel() + ")"
                + (met ? "" : " §8[" + reqCurrentLevel + "/" + req.minLevel() + "]");
        return reqText;
    }

    private static int parseHexColor(String hexColor) {
        try {
            // Add alpha channel if not present, assuming opaque
            if (hexColor.length() == 6) {
                hexColor = "FF" + hexColor;
            }
            return (int) Long.parseLong(hexColor, 16);
        } catch (NumberFormatException e) {
            System.err.println("Invalid hex color format: " + hexColor + ". Using default.");
            return 0xFF555555; // Default to a dark gray
        }
    }

    private static ResourceLocation parseResourceLocation(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return ResourceLocation.parse(path);
        } catch (Exception e) {
            System.err.println("Invalid ResourceLocation format: " + path + ". " + e.getMessage());
            return null;
        }
    }

    private static ItemStack getItemStackFromId(String itemId) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != Items.AIR) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns the effective texture ResourceLocation for a node given its state,
     * checking per-node overrides first, then global fallbacks.
     */
    private static ResourceLocation getEffectiveNodeTexture(
            SkillNodeData node,
            ResourceLocation globalLocked, ResourceLocation globalAvailable,
            ResourceLocation globalPartial, ResourceLocation globalMaxed) {
        return switch (node.state()) {
            case "LOCKED"    -> {
                ResourceLocation t = parseResourceLocation(node.textureOverrideLocked());
                yield t != null ? t : globalLocked;
            }
            case "AVAILABLE" -> {
                ResourceLocation t = parseResourceLocation(node.textureOverrideAvailable());
                yield t != null ? t : globalAvailable;
            }
            case "PARTIAL"   -> {
                ResourceLocation t = parseResourceLocation(node.textureOverridePartial());
                yield t != null ? t : globalPartial;
            }
            case "MAXED"     -> {
                ResourceLocation t = parseResourceLocation(node.textureOverrideMaxed());
                yield t != null ? t : globalMaxed;
            }
            default -> null;
        };
    }

    /**
     * Applies a one-shot click transform to the poseStack.
     * t = 1-progress goes 0→1 over the animation duration.
     */
    private static void applyClickTransform(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                            String animType, float progress,
                                            int nodePixelX, int nodePixelY, int nodeSize) {
        float t = 1f - progress; // 0 at click moment, 1 at end
        float cx = nodePixelX + nodeSize / 2f;
        float cy = nodePixelY + nodeSize / 2f;

        switch (animType) {
            case "punch" -> {
                float shrink = (float) Math.sin(t * Math.PI) * 0.18f;
                float scale = 1f - shrink;
                poseStack.translate(cx, cy, 0);
                poseStack.scale(scale, scale, 1f);
                poseStack.translate(-cx, -cy, 0);
            }
            case "shake" -> {
                float offsetX = (float) Math.sin(t * Math.PI * 5) * 3f * progress;
                poseStack.translate(offsetX, 0, 0);
            }
            case "bounce" -> {
                float offsetY = -(float) Math.abs(Math.sin(t * Math.PI)) * 5f * progress;
                poseStack.translate(0, offsetY, 0);
            }
            // "flash" is handled as an overlay, no poseStack transform
        }
    }

    /**
     * Returns the node ID that was clicked at (localX, localY) relative to the 
     * SKILL_TREE element's top-left corner, or null if no node was hit.
     * Uses the same position logic as render().
     */
    public static String getClickedNodeId(UIElement element, int localX, int localY) {
        Map<String, String> properties = element.getProperties();
        String treeId = properties.get("tree_id");
        if (treeId == null) return null;

        int nodeSize = Integer.parseInt(properties.getOrDefault("node_size", "48"));
        int nodeSpacingX = Integer.parseInt(properties.getOrDefault("node_spacing_x", "100"));
        int nodeSpacingY = Integer.parseInt(properties.getOrDefault("node_spacing_y", "80"));

        var cache = ClientEventBridge.getInstance().getCache();
        var trees = cache.getCachedSkillTrees();
        var tree = trees.get(treeId);
        if (tree == null) return null;

        for (Map.Entry<String, SkillNodeData> entry : tree.nodes().entrySet()) {
            String nodeId = entry.getKey();
            SkillNodeData node = entry.getValue();
            int nodePixelX = node.positionX() * nodeSpacingX;
            int nodePixelY = node.positionY() * nodeSpacingY;

            if (localX >= nodePixelX && localX <= nodePixelX + nodeSize &&
                localY >= nodePixelY && localY <= nodePixelY + nodeSize) {
                return nodeId;
            }
        }
        return null;
    }

}