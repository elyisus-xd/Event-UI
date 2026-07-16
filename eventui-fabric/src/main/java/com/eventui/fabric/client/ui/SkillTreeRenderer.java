package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIElement;
import com.eventui.api.bridge.SkillConnectionsConfig;
import com.eventui.api.bridge.SkillNodeData;
import com.eventui.api.bridge.SkillRequirementData;
import com.eventui.fabric.client.bridge.ClientEventBridge;
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

    // Pan and zoom state per skill tree
    private static final Map<String, PanZoomState> panZoomStates = new HashMap<>();

    // Drag state
    private static boolean isDragging = false;
    private static int dragStartX = 0;
    private static int dragStartY = 0;
    private static String draggingTreeId = null;

    // Zoom limits
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 2.0f;

    // Animation time for animated connections
    private static long animationTime = System.currentTimeMillis();

    // Cached mouse position for show_on_hover optimization
    private static int cachedMouseX = -1;
    private static int cachedMouseY = -1;

    // Tracks nodes with pending spend requests (waiting for server response)
    private static final java.util.Set<String> pendingSpendNodes =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    // Pan and zoom state class
    private static class PanZoomState {
        float offsetX = 0f;
        float offsetY = 0f;
        float zoom = 1.0f;
    }


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

        // Get connections config from cache
        SkillConnectionsConfig connectionsConfig = ClientEventBridge.getInstance().getCache().getConnectionsConfig();

        // Use config colors if available, otherwise fall back to properties
        int connectionColorLocked = parseHexColor(connectionsConfig.getLockedColor());
        int connectionColorAvailable = parseHexColor(connectionsConfig.getAvailableColor());
        int connectionColorPartial = parseHexColor(connectionsConfig.getPartialColor());
        int connectionColorMaxed = parseHexColor(connectionsConfig.getMaxedColor());

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

        // Get or create pan/zoom state for this tree
        PanZoomState state = panZoomStates.computeIfAbsent(treeId, k -> new PanZoomState());

        // Apply pan/zoom transform
        graphics.pose().pushPose();
        graphics.pose().translate(elementX + elementWidth / 2f, elementY + elementHeight / 2f, 0);
        graphics.pose().scale(state.zoom, state.zoom, 1f);
        graphics.pose().translate(-elementX - elementWidth / 2f + state.offsetX, -elementY - elementHeight / 2f + state.offsetY, 0);

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
        // Transform mouse coordinates to account for pan/zoom
        // Inverse of: translate(center) * scale(zoom) * translate(-center + offset)
        float centerX = elementX + elementWidth / 2f;
        float centerY = elementY + elementHeight / 2f;
        float transformedMouseX = ((mouseX - centerX) / state.zoom) - state.offsetX + centerX;
        float transformedMouseY = ((mouseY - centerY) / state.zoom) - state.offsetY + centerY;

        String hoveredNodeId = null;
        for (Map.Entry<String, int[]> posEntry : nodePosMap.entrySet()) {
            int[] pos = posEntry.getValue();
            if (transformedMouseX >= pos[0] && transformedMouseX <= pos[0] + nodeSize &&
                    transformedMouseY >= pos[1] && transformedMouseY <= pos[1] + nodeSize) {
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
                            alphaData, pos[0], pos[1], nodeSize, nodeSize, (int)transformedMouseX, (int)transformedMouseY)) {
                        continue; // transparent pixel — not hovered
                    }
                }
                hoveredNodeId = candidateId;
                break;
            }
        }


        // Paso 4: Dibujar líneas de conexión en L (ANTES que los nodos)
        // Update cached mouse position if changed
        int currentMouseX = (int) transformedMouseX;
        int currentMouseY = (int) transformedMouseY;
        boolean mouseChanged = (currentMouseX != cachedMouseX || currentMouseY != cachedMouseY);
        if (mouseChanged) {
            cachedMouseX = currentMouseX;
            cachedMouseY = currentMouseY;
        }

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
                    case "PARTIAL" -> connectionColorPartial;
                    case "MAXED" -> connectionColorMaxed;
                    default -> 0xFF555555; // Default to locked color
                };

                for (SkillRequirementData requirement : node.requires()) {
                    String requiredNodeId = requirement.nodeId();
                    int[] parentPos = nodePosMap.get(requiredNodeId);
                    if (parentPos == null) continue;

                    int parentCenterX = parentPos[0] + nodeSize / 2;
                    int parentCenterY = parentPos[1] + nodeSize / 2;

                    // Culling: check if connection is visible on screen
                    if (!isConnectionVisible(parentCenterX, parentCenterY, childCenterX, childCenterY,
                            elementX, elementY, elementWidth, elementHeight, state)) {
                        continue;
                    }

                    // Check if show_on_hover is enabled
                    boolean shouldDraw = true;
                    if (connectionsConfig.showOnHover()) {
                        // Only draw if mouse is near either node or the connection
                        boolean nearChild = isMouseNearNode(transformedMouseX, transformedMouseY, childCenterX, childCenterY, nodeSize);
                        boolean nearParent = isMouseNearNode(transformedMouseX, transformedMouseY, parentCenterX, parentCenterY, nodeSize);
                        boolean nearConnection = isMouseNearConnection(transformedMouseX, transformedMouseY, parentCenterX, parentCenterY, childCenterX, childCenterY, connectionsConfig.getType());

                        shouldDraw = nearChild || nearParent || nearConnection;
                    }

                    if (shouldDraw) {
                        drawConnection(graphics, parentCenterX, parentCenterY, childCenterX, childCenterY,
                                connectionColor, connectionsConfig);
                    }
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

            // Pending spend indicator (replaces click animation while waiting for server)
            if (isSpendPending(treeId, nodeId)) {
                long t = System.currentTimeMillis() % 600;
                float pulse = (float) Math.abs(Math.sin(t / 600.0 * Math.PI));
                int pulseAlpha = (int)(pulse * 100);
                // draw after node background — handled at end of node loop
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
                    poseStack.translate(itemCenterX, itemCenterY, -50f);
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

            if (isSpendPending(treeId, nodeId)) {
                long t = System.currentTimeMillis() % 600;
                float pulse = (float) Math.abs(Math.sin(t / 600.0 * Math.PI));
                int pulseAlpha = (int)(pulse * 80);
                graphics.fill(nodePixelX + 1, nodePixelY + 1,
                              nodePixelX + nodeSize - 1, nodePixelY + nodeSize - 1,
                              (pulseAlpha << 24) | 0xFFFFAA);
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

        // Pop pan/zoom transform
        graphics.pose().popPose();

        // Detectar nodo bajo el cursor y renderizar tooltip vanilla (outside transform)
        if (context.containsKey("mouseX") && context.containsKey("mouseY")) {
            int mx = (int) context.get("mouseX");
            int my = (int) context.get("mouseY");
            
            // Transform mouse coordinates to account for pan/zoom
            // Inverse of: translate(center) * scale(zoom) * translate(-center + offset)
            float transformedMx = ((mx - centerX) / state.zoom) - state.offsetX + centerX;
            float transformedMy = ((my - centerY) / state.zoom) - state.offsetY + centerY;
            
            for (SkillNodeData node : tree.nodes().values()) {
                int[] pos = nodePosMap.get(node.id());
                if (pos == null) continue;
                if (transformedMx >= pos[0] && transformedMx <= pos[0] + nodeSize 
                        && transformedMy >= pos[1] && transformedMy <= pos[1] + nodeSize) {
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

                    // Exclusive branch info
                    if (node.exclusiveGroupId() != null && node.exclusiveBranchId() != null) {
                        lines.add(net.minecraft.network.chat.Component.literal(""));
                        String selectedBranch = tree.selectedBranches() != null ? tree.selectedBranches().get(node.exclusiveGroupId()) : null;

                        if (selectedBranch != null && !selectedBranch.equals(node.exclusiveBranchId())) {
                            // Nodo bloqueado por selección de rama diferente
                            lines.add(net.minecraft.network.chat.Component.literal("§cRama bloqueada"));
                            lines.add(net.minecraft.network.chat.Component.literal("§7Ya seleccionaste otra rama en este grupo"));
                        } else if (selectedBranch == null) {
                            // Nodo disponible para selección
                            lines.add(net.minecraft.network.chat.Component.literal("§aRama disponible"));
                            lines.add(net.minecraft.network.chat.Component.literal("§7Seleccionar esta rama bloqueará las otras"));
                        } else {
                            // Nodo en la rama seleccionada
                            lines.add(net.minecraft.network.chat.Component.literal("§aRama seleccionada"));
                        }
                    }

                    // Use screen mouse coordinates for tooltip position
                    graphics.renderTooltip(font, lines, java.util.Optional.empty(), screenMouseX, screenMouseY);
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
            // Remove # prefix if present
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }
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

        PanZoomState state = panZoomStates.get(treeId);
        if (state == null) return null;

        int nodeSize = Integer.parseInt(properties.getOrDefault("node_size", "48"));
        int nodeSpacingX = Integer.parseInt(properties.getOrDefault("node_spacing_x", "100"));
        int nodeSpacingY = Integer.parseInt(properties.getOrDefault("node_spacing_y", "80"));
        int elementWidth = element.getWidth();
        int elementHeight = element.getHeight();

        var cache = ClientEventBridge.getInstance().getCache();
        var trees = cache.getCachedSkillTrees();
        var tree = trees.get(treeId);
        if (tree == null) return null;

        // Transform local coordinates to account for pan/zoom
        // Inverse of: translate(center) * scale(zoom) * translate(-center + offset)
        float centerX = elementWidth / 2f;
        float centerY = elementHeight / 2f;
        float transformedX = ((localX - centerX) / state.zoom) - state.offsetX + centerX;
        float transformedY = ((localY - centerY) / state.zoom) - state.offsetY + centerY;

        for (Map.Entry<String, SkillNodeData> entry : tree.nodes().entrySet()) {
            String nodeId = entry.getKey();
            SkillNodeData node = entry.getValue();
            int nodePixelX = node.positionX() * nodeSpacingX;
            int nodePixelY = node.positionY() * nodeSpacingY;

            if (transformedX >= nodePixelX && transformedX <= nodePixelX + nodeSize &&
                transformedY >= nodePixelY && transformedY <= nodePixelY + nodeSize) {
                return nodeId;
            }
        }
        return null;
    }

    /**
     * Handle mouse wheel scroll event for skill tree.
     * @param element The SKILL_TREE element
     * @param mouseX Mouse X position relative to screen
     * @param mouseY Mouse Y position relative to screen
     * @param horizontal Horizontal scroll amount
     * @param vertical Vertical scroll amount
     * @param ctrlPressed Whether Ctrl key is pressed (for zoom)
     */
    public static void handleMouseWheel(UIElement element, int mouseX, int mouseY, 
                                         double horizontal, double vertical, boolean ctrlPressed) {
        String treeId = element.getProperties().get("tree_id");
        if (treeId == null) return;

        PanZoomState state = panZoomStates.get(treeId);
        if (state == null) return;

        if (ctrlPressed) {
            // Zoom with Ctrl+scroll
            float zoomFactor = 1.0f + (float) vertical * 0.1f;
            float newZoom = state.zoom * zoomFactor;
            state.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        } else {
            // Pan with scroll
            state.offsetX += (float) horizontal * 20;
            state.offsetY += (float) vertical * 20;
        }
    }

    /**
     * Handle mouse press event for skill tree (for drag-to-pan).
     * @param element The SKILL_TREE element
     * @param mouseX Mouse X position relative to screen
     * @param mouseY Mouse Y position relative to screen
     * @param button Mouse button pressed
     * @param elementX Optional resolved element X position (for anchored elements)
     * @param elementY Optional resolved element Y position (for anchored elements)
     */
    public static void handleMousePress(UIElement element, int mouseX, int mouseY, int button, 
                                         Integer elementX, Integer elementY) {
        String treeId = element.getProperties().get("tree_id");
        if (treeId == null) return;

        if (button == 0) { // Left click
            // Use resolved coords if provided, otherwise use element's coords
            int elemX = (elementX != null) ? elementX : element.getX();
            int elemY = (elementY != null) ? elementY : element.getY();
            
            int localX = mouseX - elemX;
            int localY = mouseY - elemY;
            String clickedNodeId = getClickedNodeId(element, localX, localY);
            
            if (clickedNodeId == null) {
                // Only start drag if not clicking on a node
                isDragging = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                draggingTreeId = treeId;
            }
        }
    }

    /**
     * Handle mouse press event for skill tree (for drag-to-pan) - overload without resolved coords.
     */
    public static void handleMousePress(UIElement element, int mouseX, int mouseY, int button) {
        handleMousePress(element, mouseX, mouseY, button, null, null);
    }

    /**
     * Handle mouse release event for skill tree (end drag-to-pan).
     * @param element The SKILL_TREE element
     * @param mouseX Mouse X position relative to screen
     * @param mouseY Mouse Y position relative to screen
     * @param button Mouse button released
     */
    public static void handleMouseRelease(UIElement element, int mouseX, int mouseY, int button) {
        if (button == 0) { // Left click release
            isDragging = false;
            draggingTreeId = null;
        }
    }

    /**
     * Handle mouse drag event for skill tree (pan).
     * @param element The SKILL_TREE element
     * @param mouseX Mouse X position relative to screen
     * @param mouseY Mouse Y position relative to screen
     * @param button Mouse button being dragged
     */
    public static void handleMouseDrag(UIElement element, int mouseX, int mouseY, int button) {
        if (!isDragging || button != 0 || draggingTreeId == null) return;

        String treeId = element.getProperties().get("tree_id");
        if (!treeId.equals(draggingTreeId)) return;

        PanZoomState state = panZoomStates.get(treeId);
        if (state == null) return;

        int deltaX = mouseX - dragStartX;
        int deltaY = mouseY - dragStartY;

        state.offsetX += deltaX / state.zoom;
        state.offsetY += deltaY / state.zoom;

        dragStartX = mouseX;
        dragStartY = mouseY;
    }

    /**
     * Resets the pan/zoom state for a specific tree.
     * Call this when the screen containing the skill tree is closed.
     */
    public static void clearPanZoomState(String treeId) {
        panZoomStates.remove(treeId);
    }

    /**
     * Resets pan/zoom state for all trees.
     * Call this on full screen close if the tree ID is not known.
     */
    public static void clearAllPanZoomStates() {
        panZoomStates.clear();
    }

    /**
     * Draw a connection line between two nodes based on the configured style.
     */
    private static void drawConnection(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                       int color, SkillConnectionsConfig config) {
        int thickness = config.getThickness();
        int alphaColor = applyOpacity(color, config.getOpacity());

        switch (config.getType()) {
            case STRAIGHT:
                drawStraightLine(graphics, x1, y1, x2, y2, alphaColor, thickness, config.isDashed());
                break;
            case CURVED:
                drawCurvedLine(graphics, x1, y1, x2, y2, alphaColor, thickness, config.isDashed());
                break;
            case ORTHOGONAL:
                drawOrthogonalLine(graphics, x1, y1, x2, y2, alphaColor, thickness, config.isDashed());
                break;
        }

        // Draw glow if enabled
        if (config.hasGlow()) {
            drawGlow(graphics, x1, y1, x2, y2, alphaColor, thickness, config.getType());
        }

        // Draw animated effect if enabled
        if (config.isAnimated()) {
            drawAnimatedFlow(graphics, x1, y1, x2, y2, alphaColor, thickness, config.getType());
        }
    }

    private static int applyOpacity(int color, float opacity) {
        int alpha = (int) (255 * opacity);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static void drawStraightLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                         int color, int thickness, boolean dashed) {
        if (dashed) {
            drawDashedStraightLine(graphics, x1, y1, x2, y2, color, thickness);
        } else {
            drawSolidStraightLine(graphics, x1, y1, x2, y2, color, thickness);
        }
    }

    private static void drawSolidStraightLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                              int color, int thickness) {
        int halfThickness = thickness / 2;

        // Use Bresenham's algorithm to draw a diagonal line
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1;
        int y = y1;

        while (true) {
            // Draw a small square at each point along the line
            graphics.fill(
                    x - halfThickness,
                    y - halfThickness,
                    x + halfThickness + 1,
                    y + halfThickness + 1,
                    color
            );

            if (x == x2 && y == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void drawDashedStraightLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                                int color, int thickness) {
        int halfThickness = thickness / 2;
        int dashLength = 8;
        int gapLength = 4;

        // Use Bresenham's algorithm to draw a dashed diagonal line
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1;
        int y = y1;
        int distance = 0;
        boolean inDash = true;
        int dashCounter = 0;

        while (true) {
            if (inDash) {
                graphics.fill(
                        x - halfThickness,
                        y - halfThickness,
                        x + halfThickness + 1,
                        y + halfThickness + 1,
                        color
                );
                dashCounter++;
                if (dashCounter >= dashLength) {
                    inDash = false;
                    dashCounter = 0;
                }
            } else {
                dashCounter++;
                if (dashCounter >= gapLength) {
                    inDash = true;
                    dashCounter = 0;
                }
            }

            if (x == x2 && y == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
            distance++;
        }
    }

    private static void drawCurvedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                        int color, int thickness, boolean dashed) {
        if (dashed) {
            drawDashedCurvedLine(graphics, x1, y1, x2, y2, color, thickness);
        } else {
            drawSolidCurvedLine(graphics, x1, y1, x2, y2, color, thickness);
        }
    }

    private static void drawSolidCurvedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                             int color, int thickness) {
        int halfThickness = thickness / 2;

        // Calculate control point for quadratic Bézier curve
        // Use a control point that creates a smooth curve
        int controlX = x1;
        int controlY = y2;

        // Draw curve using Bresenham-like approach for Bézier
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * 2;
        steps = Math.min(steps, 100); // Limit to maximum 100 steps for performance
        if (steps < 20) steps = 20; // Minimum steps for smooth curve

        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float t2 = t * t;
            float mt = 1 - t;
            float mt2 = mt * mt;

            // Quadratic Bézier formula: (1-t)² * P0 + 2(1-t)t * P1 + t² * P2
            int bx = (int) (mt2 * x1 + 2 * mt * t * controlX + t2 * x2);
            int by = (int) (mt2 * y1 + 2 * mt * t * controlY + t2 * y2);

            graphics.fill(
                    bx - halfThickness,
                    by - halfThickness,
                    bx + halfThickness + 1,
                    by + halfThickness + 1,
                    color
            );
        }
    }

    private static void drawDashedCurvedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                              int color, int thickness) {
        int halfThickness = thickness / 2;
        int dashLength = 8;
        int gapLength = 4;

        // Calculate control point for quadratic Bézier curve
        int controlX = x1;
        int controlY = y2;

        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * 2;
        steps = Math.min(steps, 100); // Limit to maximum 100 steps for performance
        if (steps < 20) steps = 20;

        boolean inDash = true;
        int dashCounter = 0;

        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float t2 = t * t;
            float mt = 1 - t;
            float mt2 = mt * mt;

            int bx = (int) (mt2 * x1 + 2 * mt * t * controlX + t2 * x2);
            int by = (int) (mt2 * y1 + 2 * mt * t * controlY + t2 * y2);

            if (inDash) {
                graphics.fill(
                        bx - halfThickness,
                        by - halfThickness,
                        bx + halfThickness + 1,
                        by + halfThickness + 1,
                        color
                );
                dashCounter++;
                if (dashCounter >= dashLength) {
                    inDash = false;
                    dashCounter = 0;
                }
            } else {
                dashCounter++;
                if (dashCounter >= gapLength) {
                    inDash = true;
                    dashCounter = 0;
                }
            }
        }
    }

    private static void drawOrthogonalLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                           int color, int thickness, boolean dashed) {
        int halfThickness = thickness / 2;

        if (dashed) {
            int dashLength = 8;
            int gapLength = 4;

            // Horizontal line (dashed)
            int hDistance = Math.abs(x2 - x1);
            int hSegments = hDistance / (dashLength + gapLength);
            for (int i = 0; i < hSegments; i++) {
                int startX = Math.min(x1, x2) + i * (dashLength + gapLength);
                int endX = startX + dashLength;
                graphics.fill(
                        startX - halfThickness,
                        y2 - halfThickness,
                        endX + halfThickness + 1,
                        y2 + halfThickness + 1,
                        color
                );
            }

            // Vertical line (dashed)
            int vDistance = Math.abs(y2 - y1);
            int vSegments = vDistance / (dashLength + gapLength);
            for (int i = 0; i < vSegments; i++) {
                int startY = Math.min(y1, y2) + i * (dashLength + gapLength);
                int endY = startY + dashLength;
                graphics.fill(
                        x1 - halfThickness,
                        startY - halfThickness,
                        x1 + halfThickness + 1,
                        endY + halfThickness + 1,
                        color
                );
            }
        } else {
            // Solid lines
            graphics.fill(
                    Math.min(x1, x2) - halfThickness,
                    y2 - halfThickness,
                    Math.max(x1, x2) + halfThickness + 1,
                    y2 + halfThickness + 1,
                    color
            );

            graphics.fill(
                    x1 - halfThickness,
                    Math.min(y1, y2) - halfThickness,
                    x1 + halfThickness + 1,
                    Math.max(y1, y2) + halfThickness + 1,
                    color
            );
        }
    }

    private static void drawGlow(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                  int color, int thickness, SkillConnectionsConfig.ConnectionType type) {
        int glowThickness = thickness + 4;
        int glowColor = (color & 0x00FFFFFF) | 0x40000000; // 25% opacity for glow

        int halfThickness = glowThickness / 2;

        switch (type) {
            case STRAIGHT:
                // Use Bresenham's algorithm for diagonal glow with reduced sampling
                int dx = Math.abs(x2 - x1);
                int dy = Math.abs(y2 - y1);
                int sx = x1 < x2 ? 1 : -1;
                int sy = y1 < y2 ? 1 : -1;
                int err = dx - dy;

                int x = x1;
                int y = y1;
                int stepCount = 0;
                int glowStep = 2; // Skip every other pixel for performance

                while (true) {
                    if (stepCount % glowStep == 0) {
                        graphics.fill(
                                x - halfThickness,
                                y - halfThickness,
                                x + halfThickness + 1,
                                y + halfThickness + 1,
                                glowColor
                        );
                    }

                    if (x == x2 && y == y2) break;

                    int e2 = 2 * err;
                    if (e2 > -dy) {
                        err -= dy;
                        x += sx;
                    }
                    if (e2 < dx) {
                        err += dx;
                        y += sy;
                    }
                    stepCount++;
                }
                break;
            case CURVED:
                // Calculate control point for quadratic Bézier curve
                int controlX = x1;
                int controlY = y2;

                int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * 2;
                steps = Math.min(steps, 100); // Limit to maximum 100 steps for performance
                if (steps < 20) steps = 20;

                for (int i = 0; i <= steps; i++) {
                    float t = (float) i / steps;
                    float t2 = t * t;
                    float mt = 1 - t;
                    float mt2 = mt * mt;

                    int bx = (int) (mt2 * x1 + 2 * mt * t * controlX + t2 * x2);
                    int by = (int) (mt2 * y1 + 2 * mt * t * controlY + t2 * y2);

                    graphics.fill(
                            bx - halfThickness,
                            by - halfThickness,
                            bx + halfThickness + 1,
                            by + halfThickness + 1,
                            glowColor
                    );
                }
                break;
            case ORTHOGONAL:
                // Horizontal glow
                graphics.fill(
                        Math.min(x1, x2) - halfThickness,
                        y2 - halfThickness,
                        Math.max(x1, x2) + halfThickness + 1,
                        y2 + halfThickness + 1,
                        glowColor
                );
                // Vertical glow
                graphics.fill(
                        x1 - halfThickness,
                        Math.min(y1, y2) - halfThickness,
                        x1 + halfThickness + 1,
                        Math.max(y1, y2) + halfThickness + 1,
                        glowColor
                );
                break;
        }
    }

    private static void drawAnimatedFlow(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                          int color, int thickness, SkillConnectionsConfig.ConnectionType type) {
        // Update animation time
        long currentTime = System.currentTimeMillis();
        animationTime = currentTime;

        // Calculate animation offset (cycles every 2000ms)
        float animOffset = (currentTime % 2000) / 2000.0f;

        int halfThickness = thickness / 2;
        int particleSize = thickness + 2;

        // Draw moving particles along the path
        int numParticles = 3;
        for (int i = 0; i < numParticles; i++) {
            float t = (animOffset + (float) i / numParticles) % 1.0f;

            int px, py;

            switch (type) {
                case STRAIGHT:
                    px = x1 + (int) ((x2 - x1) * t);
                    py = y1 + (int) ((y2 - y1) * t);
                    break;
                case CURVED:
                    // Quadratic Bézier
                    int controlX = x1;
                    int controlY = y2;
                    float t2 = t * t;
                    float mt = 1 - t;
                    float mt2 = mt * mt;
                    px = (int) (mt2 * x1 + 2 * mt * t * controlX + t2 * x2);
                    py = (int) (mt2 * y1 + 2 * mt * t * controlY + t2 * y2);
                    break;
                case ORTHOGONAL:
                    if (t < 0.5f) {
                        float ht = t * 2;
                        px = x1 + (int) ((x2 - x1) * ht);
                        py = y2;
                    } else {
                        float vt = (t - 0.5f) * 2;
                        px = x1;
                        py = y1 + (int) ((y2 - y1) * vt);
                    }
                    break;
                default:
                    px = x1 + (int) ((x2 - x1) * t);
                    py = y1 + (int) ((y2 - y1) * t);
            }

            // Draw particle with brighter color
            int particleColor = (color & 0x00FFFFFF) | 0xFFFFFFFF; // Full opacity

            graphics.fill(
                    px - particleSize / 2,
                    py - particleSize / 2,
                    px + particleSize / 2 + 1,
                    py + particleSize / 2 + 1,
                    particleColor
            );
        }
    }

    private static boolean isMouseNearNode(float mouseX, float mouseY, int nodeX, int nodeY, int nodeSize) {
        int threshold = nodeSize + 20; // Extra margin around node
        return mouseX >= nodeX - threshold && mouseX <= nodeX + nodeSize + threshold &&
               mouseY >= nodeY - threshold && mouseY <= nodeY + nodeSize + threshold;
    }

    private static boolean isMouseNearConnection(float mouseX, float mouseY, int x1, int y1, int x2, int y2,
                                                  SkillConnectionsConfig.ConnectionType type) {
        int threshold = 15; // Distance threshold for connection

        switch (type) {
            case STRAIGHT:
                return distanceToLine(mouseX, mouseY, x1, y1, x2, y2) <= threshold;
            case CURVED:
                // Check distance to curve by sampling points
                return distanceToCurve(mouseX, mouseY, x1, y1, x2, y2, threshold);
            case ORTHOGONAL:
                // Check distance to both segments
                boolean nearHorizontal = distanceToLine(mouseX, mouseY, x1, y2, x2, y2) <= threshold;
                boolean nearVertical = distanceToLine(mouseX, mouseY, x1, y1, x1, y2) <= threshold;
                return nearHorizontal || nearVertical;
            default:
                return false;
        }
    }

    private static float distanceToLine(float px, float py, int x1, int y1, int x2, int y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return (float) Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }

        float t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        float closestX = x1 + t * dx;
        float closestY = y1 + t * dy;

        return (float) Math.sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY));
    }

    private static boolean distanceToCurve(float px, float py, int x1, int y1, int x2, int y2, int threshold) {
        // Sample points along the Bézier curve
        int steps = 20;
        int controlX = x1;
        int controlY = y2;

        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float t2 = t * t;
            float mt = 1 - t;
            float mt2 = mt * mt;

            int bx = (int) (mt2 * x1 + 2 * mt * t * controlX + t2 * x2);
            int by = (int) (mt2 * y1 + 2 * mt * t * controlY + t2 * y2);

            float dist = (float) Math.sqrt((px - bx) * (px - bx) + (py - by) * (py - by));
            if (dist <= threshold) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConnectionVisible(int x1, int y1, int x2, int y2,
                                               int elementX, int elementY, int elementWidth, int elementHeight,
                                               PanZoomState state) {
        // Calculate screen bounds with margin
        int margin = 50;
        int screenMinX = elementX - margin;
        int screenMinY = elementY - margin;
        int screenMaxX = elementX + elementWidth + margin;
        int screenMaxY = elementY + elementHeight + margin;

        // Apply pan/zoom to connection points
        float transformedX1 = (x1 + state.offsetX) * state.zoom;
        float transformedY1 = (y1 + state.offsetY) * state.zoom;
        float transformedX2 = (x2 + state.offsetX) * state.zoom;
        float transformedY2 = (y2 + state.offsetY) * state.zoom;

        // Check if either endpoint is visible
        boolean p1Visible = transformedX1 >= screenMinX && transformedX1 <= screenMaxX &&
                           transformedY1 >= screenMinY && transformedY1 <= screenMaxY;
        boolean p2Visible = transformedX2 >= screenMinX && transformedX2 <= screenMaxX &&
                           transformedY2 >= screenMinY && transformedY2 <= screenMaxY;

        if (p1Visible || p2Visible) return true;

        // Check if bounding box intersects screen
        int minX = Math.min((int) transformedX1, (int) transformedX2);
        int maxX = Math.max((int) transformedX1, (int) transformedX2);
        int minY = Math.min((int) transformedY1, (int) transformedY2);
        int maxY = Math.max((int) transformedY1, (int) transformedY2);

        return maxX >= screenMinX && minX <= screenMaxX &&
               maxY >= screenMinY && minY <= screenMaxY;
    }

    /** Called when the player clicks a node — marks it as pending without animating yet. */
    public static void markPendingSpend(String treeId, String nodeId) {
        pendingSpendNodes.add(treeId + ":" + nodeId);
    }

    /** Called on SKILL_NODE_UPDATE success — removes from pending and triggers the click animation. */
    public static void confirmSpend(String treeId, String nodeId, String clickAnimType, int clickAnimDuration) {
        String key = treeId + ":" + nodeId;
        pendingSpendNodes.remove(key);
        if (clickAnimType != null && !clickAnimType.equals("none")) {
            ClickAnimationManager.getInstance().triggerClick(
                "click:" + key, clickAnimType, clickAnimDuration);
        }
    }

    /** Called on SKILL_SPEND_ERROR — removes from pending without animating. */
    public static void cancelSpend(String treeId, String nodeId) {
        pendingSpendNodes.remove(treeId + ":" + nodeId);
    }

    /** Returns true if this node has a spend request in flight. */
    public static boolean isSpendPending(String treeId, String nodeId) {
        return pendingSpendNodes.contains(treeId + ":" + nodeId);
    }

}