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
import net.minecraft.world.item.Items; 
import net.minecraft.core.registries.BuiltInRegistries; 
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class SkillTreeRenderer {

    private static final HoverAnimationManager nodeHoverAnimManager =
            new HoverAnimationManager();

    private static final java.util.Set<String> previouslyHoveredNodes =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static final Map<String, PanZoomState> panZoomStates = new HashMap<>();

    private static boolean isDragging = false;
    private static int dragStartX = 0;
    private static int dragStartY = 0;
    private static String draggingTreeId = null;

    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 2.0f;

    private static long animationTime = System.currentTimeMillis();

    private static int cachedMouseX = -1;
    private static int cachedMouseY = -1;

    private static final java.util.Set<String> pendingSpendNodes =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

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

        if (ClientEventBridge.skillDataDirty) {
            ClientEventBridge.skillDataDirty = false;
        }

        Map<String, String> properties = element.getProperties();
        String treeId = properties.get("tree_id");
        if (treeId == null) {
            
            graphics.drawString(font, "§cError: tree_id no especificado para SKILL_TREE",
                    elementX + elementWidth / 2 - font.width("§cError: tree_id no especificado para SKILL_TREE") / 2,
                    elementY + elementHeight / 2, 0xFF0000, false);
            return;
        }

        int nodeSize = Integer.parseInt(properties.getOrDefault("node_size", "48"));
        int nodeSpacingX = Integer.parseInt(properties.getOrDefault("node_spacing_x", "100"));
        int nodeSpacingY = Integer.parseInt(properties.getOrDefault("node_spacing_y", "80"));
        boolean showLevelText = Boolean.parseBoolean(properties.getOrDefault("show_level_text", "true"));

        SkillConnectionsConfig connectionsConfig = ClientEventBridge.getInstance().getCache().getConnectionsConfig();

        int connectionColorLocked = parseHexColor(connectionsConfig.getLockedColor());
        int connectionColorAvailable = parseHexColor(connectionsConfig.getAvailableColor());
        int connectionColorPartial = parseHexColor(connectionsConfig.getPartialColor());
        int connectionColorMaxed = parseHexColor(connectionsConfig.getMaxedColor());

        ResourceLocation nodeTextureLocked = parseResourceLocation(properties.get("node_texture_locked"));
        ResourceLocation nodeTextureAvailable = parseResourceLocation(properties.get("node_texture_available"));
        ResourceLocation nodeTexturePartial = parseResourceLocation(properties.get("node_texture_partial"));
        ResourceLocation nodeTextureMaxed = parseResourceLocation(properties.get("node_texture_maxed"));

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
                default           -> null; 
            };

        com.eventui.api.ui.HoverAnimation hoverAnim = (hoverAnimType != null)
            ? new com.eventui.api.ui.HoverAnimation(
                    hoverAnimType, hoverAnimDuration, hoverAnimIntensity, hoverAnimEasing)
            : null;

        var cache = ClientEventBridge.getInstance().getCache();
        var trees = cache.getCachedSkillTrees();
        var tree = trees.get(treeId);

        if (tree == null) {
            graphics.drawString(font, "§7Cargando árbol...",
                    elementX + elementWidth / 2 - font.width("§7Cargando árbol...") / 2,
                    elementY + elementHeight / 2, 0xAAAAAA, false);
            return;
        }

        PanZoomState state = panZoomStates.computeIfAbsent(treeId, k -> new PanZoomState());

        graphics.pose().pushPose();
        graphics.pose().translate(elementX + elementWidth / 2f, elementY + elementHeight / 2f, 0);
        graphics.pose().scale(state.zoom, state.zoom, 1f);
        graphics.pose().translate(-elementX - elementWidth / 2f + state.offsetX, -elementY - elementHeight / 2f + state.offsetY, 0);

        Map<String, int[]> nodePosMap = new HashMap<>();
        for (Map.Entry<String, SkillNodeData> entry : tree.nodes().entrySet()) {
            String nodeId = entry.getKey();
            SkillNodeData node = entry.getValue();
            int nodePixelX = elementX + (node.positionX() * nodeSpacingX);
            int nodePixelY = elementY + (node.positionY() * nodeSpacingY);
            nodePosMap.put(nodeId, new int[]{nodePixelX, nodePixelY});
        }

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
                
                ResourceLocation candidateTexture = getEffectiveNodeTexture(
                        candidateNode, nodeTextureLocked, nodeTextureAvailable,
                        nodeTexturePartial, nodeTextureMaxed);
                if (candidateTexture != null) {
                    TextureAlphaCache.AlphaData alphaData =
                            TextureAlphaCache.getAlphaData(candidateTexture);
                    if (!TextureAlphaCache.isMouseOverOpaque(
                            alphaData, pos[0], pos[1], nodeSize, nodeSize, (int)transformedMouseX, (int)transformedMouseY)) {
                        continue; 
                    }
                }
                hoveredNodeId = candidateId;
                break;
            }
        }

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
                if (childPos == null) continue; 

                int childCenterX = childPos[0] + nodeSize / 2;
                int childCenterY = childPos[1] + nodeSize / 2;

                int connectionColor = switch (node.state()) {
                    case "LOCKED" -> connectionColorLocked;
                    case "AVAILABLE" -> connectionColorAvailable;
                    case "PARTIAL" -> connectionColorPartial;
                    case "MAXED" -> connectionColorMaxed;
                    default -> 0xFF555555; 
                };

                for (SkillRequirementData requirement : node.requires()) {
                    String requiredNodeId = requirement.nodeId();
                    int[] parentPos = nodePosMap.get(requiredNodeId);
                    if (parentPos == null) continue;

                    int parentCenterX = parentPos[0] + nodeSize / 2;
                    int parentCenterY = parentPos[1] + nodeSize / 2;

                    if (!isConnectionVisible(parentCenterX, parentCenterY, childCenterX, childCenterY,
                            elementX, elementY, elementWidth, elementHeight, state)) {
                        continue;
                    }

                    boolean shouldDraw = true;
                    if (connectionsConfig.showOnHover()) {
                        
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

            if (isHovered) {
                currentlyHovered.add(nodeId);
                if (hoverAnim != null) {
                    nodeHoverAnimManager.startAnimation(animKey, hoverAnim);
                }
                
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

            if (isSpendPending(treeId, nodeId)) {
                long t = System.currentTimeMillis() % 600;
                float pulse = (float) Math.abs(Math.sin(t / 600.0 * Math.PI));
                int pulseAlpha = (int)(pulse * 100);
                
            }

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

            if (clickProgress > 0f && "flash".equals(activeClickAnim)) {
                int flashAlpha = (int)(clickProgress * 160);
                graphics.fill(nodePixelX + 1, nodePixelY + 1,
                        nodePixelX + nodeSize - 1, nodePixelY + nodeSize - 1,
                        (flashAlpha << 24) | 0xFFFFFF);
            }
        }

        previouslyHoveredNodes.clear();
        previouslyHoveredNodes.addAll(currentlyHovered);

        graphics.pose().popPose();

        if (context.containsKey("mouseX") && context.containsKey("mouseY")) {
            int mx = (int) context.get("mouseX");
            int my = (int) context.get("mouseY");

            float transformedMx = ((mx - centerX) / state.zoom) - state.offsetX + centerX;
            float transformedMy = ((my - centerY) / state.zoom) - state.offsetY + centerY;
            
            for (SkillNodeData node : tree.nodes().values()) {
                int[] pos = nodePosMap.get(node.id());
                if (pos == null) continue;
                if (transformedMx >= pos[0] && transformedMx <= pos[0] + nodeSize 
                        && transformedMy >= pos[1] && transformedMy <= pos[1] + nodeSize) {
                    
                    List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                    lines.add(net.minecraft.network.chat.Component.literal("§6" + node.displayName()));
                    lines.add(net.minecraft.network.chat.Component.literal("§7" + node.description()));
                    lines.add(net.minecraft.network.chat.Component.literal(""));
                    lines.add(net.minecraft.network.chat.Component.literal(
                        "§eNivel: §f" + node.currentLevel() + "/" + node.maxLevel()));
                    if (!"MAXED".equals(node.state()) && node.costNextLevel() > 0) {
                        String pointType = node.pointType() != null ? node.pointType() : tree.pointType();
                        String pointTypeDisplay = getPointTypeDisplayName(pointType);
                        lines.add(net.minecraft.network.chat.Component.literal(
                            "§eCosto siguiente nivel: §f" + node.costNextLevel() + " §7" + pointTypeDisplay));
                    }

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

                    if (node.exclusiveGroupId() != null && node.exclusiveBranchId() != null) {
                        lines.add(net.minecraft.network.chat.Component.literal(""));
                        String selectedBranch = tree.selectedBranches() != null ? tree.selectedBranches().get(node.exclusiveGroupId()) : null;

                        com.eventui.api.bridge.ExclusiveGroupData group = null;
                        com.eventui.api.bridge.ExclusiveBranchData branch = null;
                        if (tree.exclusiveGroups() != null) {
                            for (var g : tree.exclusiveGroups()) {
                                if (g.id().equals(node.exclusiveGroupId())) {
                                    group = g;
                                    for (var b : g.branches()) {
                                        if (b.id().equals(node.exclusiveBranchId())) {
                                            branch = b;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                        }

                        if (group != null) {
                            lines.add(net.minecraft.network.chat.Component.literal("§6Grupo: §f" + group.name()));
                            if (group.description() != null && !group.description().isEmpty()) {
                                lines.add(net.minecraft.network.chat.Component.literal("§7" + group.description()));
                            }
                            int selectionsMade = tree.selectedBranches() != null && tree.selectedBranches().containsKey(group.id()) ? 1 : 0;
                            int remaining = group.maxSelections() - selectionsMade;
                            lines.add(net.minecraft.network.chat.Component.literal("§7Selecciones: §f" + selectionsMade + "/" + group.maxSelections() + " §7(" + remaining + " restantes)"));
                        }

                        if (branch != null && !branch.nodeIds().isEmpty()) {
                            String firstNodeId = branch.nodeIds().get(0);
                            if (node.id().equals(firstNodeId)) {
                                lines.add(net.minecraft.network.chat.Component.literal(""));
                                lines.add(net.minecraft.network.chat.Component.literal("§bRama: §f" + branch.name()));
                                lines.add(net.minecraft.network.chat.Component.literal("§7Nodos en esta rama:"));
                                for (String nodeId : branch.nodeIds()) {
                                    SkillNodeData branchNode = tree.nodes().get(nodeId);
                                    String nodeName = branchNode != null ? branchNode.displayName() : nodeId;
                                    lines.add(net.minecraft.network.chat.Component.literal("  §f- " + nodeName));
                                }
                            }
                        }

                        lines.add(net.minecraft.network.chat.Component.literal(""));
                        if (selectedBranch != null && !selectedBranch.equals(node.exclusiveBranchId())) {
                            
                            lines.add(net.minecraft.network.chat.Component.literal("§cRama bloqueada"));
                            lines.add(net.minecraft.network.chat.Component.literal("§7Ya seleccionaste otra rama en este grupo"));
                        } else if (selectedBranch == null) {
                            
                            lines.add(net.minecraft.network.chat.Component.literal("§aRama disponible"));
                            lines.add(net.minecraft.network.chat.Component.literal("§7Seleccionar esta rama bloqueará las otras"));
                        } else {
                            
                            lines.add(net.minecraft.network.chat.Component.literal("§aRama seleccionada"));
                        }
                    }

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
            
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }
            
            if (hexColor.length() == 6) {
                hexColor = "FF" + hexColor;
            }
            return (int) Long.parseLong(hexColor, 16);
        } catch (NumberFormatException e) {
            System.err.println("Invalid hex color format: " + hexColor + ". Using default.");
            return 0xFF555555; 
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

    private static void applyClickTransform(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                            String animType, float progress,
                                            int nodePixelX, int nodePixelY, int nodeSize) {
        float t = 1f - progress; 
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
            
        }
    }

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

    public static void handleMouseWheel(UIElement element, int mouseX, int mouseY, 
                                         double horizontal, double vertical, boolean ctrlPressed) {
        String treeId = element.getProperties().get("tree_id");
        if (treeId == null) return;

        PanZoomState state = panZoomStates.get(treeId);
        if (state == null) return;

        if (ctrlPressed) {
            
            float zoomFactor = 1.0f + (float) vertical * 0.1f;
            float newZoom = state.zoom * zoomFactor;
            state.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        } else {
            
            state.offsetX += (float) horizontal * 20;
            state.offsetY += (float) vertical * 20;
        }
    }

    public static void handleMousePress(UIElement element, int mouseX, int mouseY, int button, 
                                         Integer elementX, Integer elementY) {
        String treeId = element.getProperties().get("tree_id");
        if (treeId == null) return;

        if (button == 0) { 
            
            int elemX = (elementX != null) ? elementX : element.getX();
            int elemY = (elementY != null) ? elementY : element.getY();
            
            int localX = mouseX - elemX;
            int localY = mouseY - elemY;
            String clickedNodeId = getClickedNodeId(element, localX, localY);
            
            if (clickedNodeId == null) {
                
                isDragging = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                draggingTreeId = treeId;
            }
        }
    }

    public static void handleMousePress(UIElement element, int mouseX, int mouseY, int button) {
        handleMousePress(element, mouseX, mouseY, button, null, null);
    }

    public static void handleMouseRelease(UIElement element, int mouseX, int mouseY, int button) {
        if (button == 0) { 
            isDragging = false;
            draggingTreeId = null;
        }
    }

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

    public static void clearPanZoomState(String treeId) {
        panZoomStates.remove(treeId);
    }

    public static void clearAllPanZoomStates() {
        panZoomStates.clear();
    }

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

        if (config.hasGlow()) {
            drawGlow(graphics, x1, y1, x2, y2, alphaColor, thickness, config.getType());
        }

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

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1;
        int y = y1;

        while (true) {
            
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

        int controlX = x1;
        int controlY = y2;

        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * 2;
        steps = Math.min(steps, 100); 
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
                    color
            );
        }
    }

    private static void drawDashedCurvedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                              int color, int thickness) {
        int halfThickness = thickness / 2;
        int dashLength = 8;
        int gapLength = 4;

        int controlX = x1;
        int controlY = y2;

        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * 2;
        steps = Math.min(steps, 100); 
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
        int glowColor = (color & 0x00FFFFFF) | 0x40000000; 

        int halfThickness = glowThickness / 2;

        switch (type) {
            case STRAIGHT:
                
                int dx = Math.abs(x2 - x1);
                int dy = Math.abs(y2 - y1);
                int sx = x1 < x2 ? 1 : -1;
                int sy = y1 < y2 ? 1 : -1;
                int err = dx - dy;

                int x = x1;
                int y = y1;
                int stepCount = 0;
                int glowStep = 2; 

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
                
                int controlX = x1;
                int controlY = y2;

                int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * 2;
                steps = Math.min(steps, 100); 
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
                
                graphics.fill(
                        Math.min(x1, x2) - halfThickness,
                        y2 - halfThickness,
                        Math.max(x1, x2) + halfThickness + 1,
                        y2 + halfThickness + 1,
                        glowColor
                );
                
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
        
        long currentTime = System.currentTimeMillis();
        animationTime = currentTime;

        float animOffset = (currentTime % 2000) / 2000.0f;

        int halfThickness = thickness / 2;
        int particleSize = thickness + 2;

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

            int particleColor = (color & 0x00FFFFFF) | 0xFFFFFFFF; 

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
        int threshold = nodeSize + 20; 
        return mouseX >= nodeX - threshold && mouseX <= nodeX + nodeSize + threshold &&
               mouseY >= nodeY - threshold && mouseY <= nodeY + nodeSize + threshold;
    }

    private static boolean isMouseNearConnection(float mouseX, float mouseY, int x1, int y1, int x2, int y2,
                                                  SkillConnectionsConfig.ConnectionType type) {
        int threshold = 15; 

        switch (type) {
            case STRAIGHT:
                return distanceToLine(mouseX, mouseY, x1, y1, x2, y2) <= threshold;
            case CURVED:
                
                return distanceToCurve(mouseX, mouseY, x1, y1, x2, y2, threshold);
            case ORTHOGONAL:
                
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
        
        int margin = 50;
        int screenMinX = elementX - margin;
        int screenMinY = elementY - margin;
        int screenMaxX = elementX + elementWidth + margin;
        int screenMaxY = elementY + elementHeight + margin;

        float transformedX1 = (x1 + state.offsetX) * state.zoom;
        float transformedY1 = (y1 + state.offsetY) * state.zoom;
        float transformedX2 = (x2 + state.offsetX) * state.zoom;
        float transformedY2 = (y2 + state.offsetY) * state.zoom;

        boolean p1Visible = transformedX1 >= screenMinX && transformedX1 <= screenMaxX &&
                           transformedY1 >= screenMinY && transformedY1 <= screenMaxY;
        boolean p2Visible = transformedX2 >= screenMinX && transformedX2 <= screenMaxX &&
                           transformedY2 >= screenMinY && transformedY2 <= screenMaxY;

        if (p1Visible || p2Visible) return true;

        int minX = Math.min((int) transformedX1, (int) transformedX2);
        int maxX = Math.max((int) transformedX1, (int) transformedX2);
        int minY = Math.min((int) transformedY1, (int) transformedY2);
        int maxY = Math.max((int) transformedY1, (int) transformedY2);

        return maxX >= screenMinX && minX <= screenMaxX &&
               maxY >= screenMinY && minY <= screenMaxY;
    }

    public static void markPendingSpend(String treeId, String nodeId) {
        pendingSpendNodes.add(treeId + ":" + nodeId);
    }

    public static void confirmSpend(String treeId, String nodeId, String clickAnimType, int clickAnimDuration) {
        String key = treeId + ":" + nodeId;
        pendingSpendNodes.remove(key);
        if (clickAnimType != null && !clickAnimType.equals("none")) {
            ClickAnimationManager.getInstance().triggerClick(
                "click:" + key, clickAnimType, clickAnimDuration);
        }
    }

    public static void cancelSpend(String treeId, String nodeId) {
        pendingSpendNodes.remove(treeId + ":" + nodeId);
    }

    private static String getPointTypeDisplayName(String pointType) {
        if (pointType == null) return "puntos";
        return switch (pointType) {
            case "combat_points" -> "Puntos de Combate";
            case "skill_points" -> "Puntos de Habilidad";
            case "gathering_points" -> "Puntos de Recolección";
            default -> pointType.replace("_", " ");
        };
    }

    public static boolean isSpendPending(String treeId, String nodeId) {
        return pendingSpendNodes.contains(treeId + ":" + nodeId);
    }

}