package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIElement;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.bridge.SkillNodeData;
import com.eventui.fabric.client.bridge.SkillRequirementData;
import com.eventui.fabric.client.bridge.SkillTreeData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items; // Assuming this is how to get ItemStacks
import net.minecraft.core.registries.BuiltInRegistries; // For getting Item from ResourceLocation
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SkillTreeRenderer {

    public static void render(GuiGraphics graphics, Font font,
                              UIElement element, int elementX, int elementY,
                              int elementWidth, int elementHeight,
                              Map<String, Object> context) {

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
        for (SkillNodeData node : tree.nodes().values()) {
            int nodePixelX = elementX + (node.positionX() * nodeSpacingX);
            int nodePixelY = elementY + (node.positionY() * nodeSpacingY);
            nodePosMap.put(node.id(), new int[]{nodePixelX, nodePixelY});
        }

        // Paso 4: Dibujar líneas de conexión en L (ANTES que los nodos)
        for (SkillNodeData node : tree.nodes().values()) {
            if (node.requires() != null && !node.requires().isEmpty()) {
                int[] childPos = nodePosMap.get(node.id());
                if (childPos == null) continue; // Should not happen

                int childCenterX = childPos[0] + nodeSize / 2;
                int childCenterY = childPos[1] + nodeSize / 2;

                int connectionColor;
                switch (node.state()) {
                    case "LOCKED":
                        connectionColor = connectionColorLocked;
                        break;
                    case "AVAILABLE":
                        connectionColor = connectionColorAvailable;
                        break;
                    case "PARTIAL":
                    case "MAXED":
                        connectionColor = connectionColorUnlocked;
                        break;
                    default:
                        connectionColor = 0xFF555555; // Default to locked color
                        break;
                }

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
        for (SkillNodeData node : tree.nodes().values()) {
            int[] nodePos = nodePosMap.get(node.id());
            if (nodePos == null) continue;

            int nodePixelX = nodePos[0];
            int nodePixelY = nodePos[1];

            // a) Obtener textura según estado del nodo
            ResourceLocation nodeTexture = null;
            int fallbackColor = 0;
            int borderColor = 0;

            switch (node.state()) {
                case "LOCKED":
                    nodeTexture = nodeTextureLocked;
                    fallbackColor = 0xFF555555; // gris oscuro
                    borderColor = 0xFF333333;
                    break;
                case "AVAILABLE":
                    nodeTexture = nodeTextureAvailable;
                    fallbackColor = 0xFF888888; // gris medio
                    borderColor = 0xFF666666;
                    break;
                case "PARTIAL":
                    nodeTexture = nodeTexturePartial;
                    fallbackColor = 0xFF5c8a5c; // verde oscuro
                    borderColor = 0xFF3a5a3a;
                    break;
                case "MAXED":
                    nodeTexture = nodeTextureMaxed;
                    fallbackColor = 0xFF5db85c; // verde brillante
                    borderColor = 0xFF3a7a3a;
                    break;
                default:
                    fallbackColor = 0xFF000000; // Black for unknown state
                    borderColor = 0xFF000000;
                    break;
            }

            // c) Si NO hay textura (fallback): dibujar cuadro coloreado con borde
            if (nodeTexture == null) {
                // Draw border
                graphics.fill(nodePixelX, nodePixelY, nodePixelX + nodeSize, nodePixelY + nodeSize, borderColor);
                // Draw inner square
                graphics.fill(nodePixelX + 1, nodePixelY + 1, nodePixelX + nodeSize - 1, nodePixelY + nodeSize - 1, fallbackColor);
            } else {
                // b) Si hay textura configurada: dibujar con graphics.blit()
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // Reset color tint
                graphics.blit(nodeTexture, nodePixelX, nodePixelY, 0, 0, nodeSize, nodeSize, nodeSize, nodeSize);
            }

            // d) Renderizar el ícono del nodo encima del cuadro/textura
            if (node.icon() != null && !node.icon().isEmpty()) {
                ItemStack itemStack = getItemStackFromId(node.icon());
                if (!itemStack.isEmpty()) {
                    int itemSize = 16; // Minecraft items are 16x16
                    int itemX = nodePixelX + (nodeSize - itemSize) / 2;
                    int itemY = nodePixelY + (nodeSize - itemSize) / 2;
                    graphics.renderItem(itemStack, itemX, itemY);
                }
            }

            // e) Si show_level_text es true Y el nodo no está LOCKED
            if (showLevelText && !Objects.equals(node.state(), "LOCKED")) {
                String levelText = "";
                if (Objects.equals(node.state(), "MAXED")) {
                    levelText = "MAX";
                } else if (Objects.equals(node.state(), "PARTIAL") || Objects.equals(node.state(), "AVAILABLE")) {
                    levelText = node.currentLevel() + "/" + node.maxLevel();
                }

                if (!levelText.isEmpty()) {
                    // Renderizar el texto de nivel en la esquina inferior derecha
                    int textWidth = font.width(levelText);
                    int textX = nodePixelX + nodeSize - textWidth - 2;
                    int textY = nodePixelY + nodeSize - 10; // Adjust for font height

                    // Renderizar con sombra
                    graphics.drawString(font, levelText, textX, textY, 0xFFFFFF, true);
                }
            }
        }
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
}