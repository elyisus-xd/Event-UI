package com.eventui.core.skill;

import com.eventui.api.skill.SkillNodeDefinition;
import com.eventui.api.skill.SkillRequirement;
import com.eventui.api.skill.PlayerSkillProgress;
import com.eventui.core.EventUIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Servicio para gestionar el gasto de puntos y la mejora de nodos de habilidad.
 */
public class SkillNodeService {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final EventUIPlugin plugin;
    private final SkillEffectApplier effectApplier;

    public SkillNodeService(EventUIPlugin plugin) {
        this.plugin = plugin;
        this.effectApplier = new SkillEffectApplier(plugin);
    }

    /**
     * Intenta gastar puntos para subir de nivel un nodo.
     * Valida requisitos, costo, e inmediatamente aplica efectos.
     */
    public SpendResult trySpendNode(UUID playerId, String treeId, String nodeId) {
        // 1. Buscar el árbol
        var treeOpt = plugin.getSkillTreeStorage().getSkillTree(treeId);
        if (treeOpt.isEmpty()) {
            return SpendResult.TREE_NOT_FOUND;
        }

        var treeDef = treeOpt.get();

        // 2. Buscar el nodo dentro del árbol
        var nodeOpt = treeDef.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst();
        if (nodeOpt.isEmpty()) {
            return SpendResult.NODE_NOT_FOUND;
        }

        var nodeDef = nodeOpt.get();

        // 3. Obtener progreso del jugador
        var skillProgress = plugin.getSkillProgressStorage().getOrCreateProgress(playerId);

        // Verificar si ya está al máximo
        int currentLevel = skillProgress.getNodeLevel(treeId, nodeId);
        if (currentLevel >= nodeDef.getMaxLevel()) {
            sendIfOnline(playerId, player ->
                    plugin.getMessenger().sendAlreadyMaxed(player, nodeDef.getDisplayName(), nodeDef.getMaxLevel()));
            return SpendResult.ALREADY_MAXED;
        }

        int nextLevel = currentLevel + 1;

        // 4. Verificar requisitos
        if (!requirementsMet(skillProgress, treeId, nodeDef)) {
            sendIfOnline(playerId, player ->
                    plugin.getMessenger().sendRequirementsNotMet(player, nodeDef.getDisplayName(), treeDef.getDisplayName()));
            return SpendResult.REQUIREMENTS_NOT_MET;
        }

        // 5. Verificar costo de puntos
        int cost = nodeDef.getCostForLevel(nextLevel);
        String pointType = treeDef.getPointType();
        int available = skillProgress.getAvailablePoints(pointType);

        if (available < cost) {
            sendIfOnline(playerId, player ->
                    plugin.getMessenger().sendInsufficientPoints(player, nodeDef.getDisplayName(), cost, available, pointType));
            return SpendResult.INSUFFICIENT_POINTS;
        }

        // 6. Realizar cambios: gastar puntos, subir nivel, aplicar efectos
        skillProgress.spendPoints(pointType, cost);
        skillProgress.setNodeLevel(treeId, nodeId, nextLevel);

        // Aplicar efectos del nodo
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            effectApplier.applyNodeEffects(player, nodeDef, nextLevel);
            plugin.getMessenger().sendNodeLeveledUp(
                    player,
                    nodeDef.getDisplayName(),
                    treeDef.getDisplayName(),
                    nextLevel,
                    nodeDef.getMaxLevel(),
                    pointType);
            if (nextLevel == nodeDef.getMaxLevel()) {
                plugin.getMessenger().sendNodeMaxed(player, nodeDef.getDisplayName(), treeDef.getDisplayName());
            }
        }

        // 7. Persistir
        plugin.getPlayerDataManager().requestSave(playerId,
                "skill spend: " + treeId + "/" + nodeId + " -> level " + nextLevel);

        LOGGER.info("Player " + playerId + " upgraded " + treeId + ":" + nodeId + " to level " + nextLevel);

        return SpendResult.SUCCESS;
    }

    public void grantPoints(Player player, String pointType, int amount) {
        var skillProgress = plugin.getSkillProgressStorage().getOrCreateProgress(player.getUniqueId());
        skillProgress.addEarnedPoints(pointType, amount);

        plugin.getPlayerDataManager().requestSave(player.getUniqueId(), "skill grant: " + pointType + " x" + amount);
        plugin.getMessenger().sendPointsGranted(player, amount, pointType);
    }

    private void sendIfOnline(UUID playerId, java.util.function.Consumer<Player> sender) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            sender.accept(player);
        }
    }

    /**
     * Verifica si se cumplen todos los requisitos del nodo.
     */
    private boolean requirementsMet(PlayerSkillProgress progress, String treeId, SkillNodeDefinition nodeDef) {
        var requirements = nodeDef.getRequirements();
        if (requirements.isEmpty()) {
            return true;
        }

        String requiresMode = nodeDef.getRequiresMode();
        boolean isAll = !"any".equalsIgnoreCase(requiresMode);

        for (SkillRequirement req : requirements) {
            // Obtener el nivel del jugador en el nodo requerido dentro del mismo árbol
            int playerLevel = progress.getNodeLevel(treeId, req.getNodeId());

            boolean requirementMet = playerLevel >= req.getMinLevel();

            if (isAll && !requirementMet) {
                return false; // En modo "all", falla si uno no se cumple
            }
            if (!isAll && requirementMet) {
                return true; // En modo "any", basta con uno que se cumpla
            }
        }

        // Si mode es "all", llegamos aquí con todos cumplidos → true
        // Si mode es "any" y ninguno cumplió, llegamos aquí → false
        return isAll;
    }

    public SkillEffectApplier getEffectApplier() {
        return effectApplier;
    }
}
