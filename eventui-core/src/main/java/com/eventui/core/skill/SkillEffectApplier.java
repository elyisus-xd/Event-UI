package com.eventui.core.skill;

import com.eventui.api.skill.SkillEffect;
import com.eventui.api.skill.SkillNodeDefinition;
import com.eventui.core.EventUIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Aplica efectos de nodos de habilidad a jugadores.
 * Maneja atributos de Bukkit y ejecución de comandos.
 */
public class SkillEffectApplier {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    public SkillEffectApplier(EventUIPlugin plugin) {
    }

    /**
     * Aplica los efectos de un nodo al jugador.
     * Parámetro includeCommands: si false, solo aplica atributos (para reconexión)
     */
    public void applyNodeEffects(Player player, SkillNodeDefinition node, int newLevel) {
        applyNodeEffects(player, node, newLevel, true);
    }

    /**
     * Aplica los efectos de un nodo al jugador.
     */
    public void applyNodeEffects(Player player, SkillNodeDefinition node, int newLevel, boolean includeCommands) {
        for (SkillEffect effect : node.getEffects()) {
            String type = effect.getType();

            if ("attribute".equalsIgnoreCase(type)) {
                applyAttributeEffect(player, node, effect, newLevel);
            } else if ("command".equalsIgnoreCase(type) && includeCommands) {
                applyCommandEffect(player, effect, newLevel);
            } else if (!("attribute".equalsIgnoreCase(type) || "command".equalsIgnoreCase(type))) {
                LOGGER.warning("Unknown skill effect type: " + type + " for node " + node.getId());
            }
        }
    }

    /**
     * Aplica un efecto de atributo de Bukkit.
     * Usa NamespacedKey determinística para que los modifiers se reemplacen, no se acumulen.
     */
    private void applyAttributeEffect(Player player, SkillNodeDefinition node, SkillEffect effect, int newLevel) {
        var data = effect.getData();

        String attributeName = data.get("attribute");
        String operationStr = data.get("operation");
        String valuePerLevelStr = data.get("value_per_level");

        if (attributeName == null || attributeName.isBlank()) {
            LOGGER.warning("Attribute effect missing 'attribute' field for node " + node.getId());
            return;
        }

        if (valuePerLevelStr == null || valuePerLevelStr.isBlank()) {
            LOGGER.warning("Attribute effect missing 'value_per_level' field for node " + node.getId());
            return;
        }

        Attribute attribute;
        try {
            attribute = Attribute.valueOf(attributeName);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown attribute: " + attributeName + " for node " + node.getId());
            return;
        }

        AttributeInstance attrInstance = player.getAttribute(attribute);
        if (attrInstance == null) {
            LOGGER.warning("Could not get AttributeInstance for " + attributeName + " for player " + player.getName());
            return;
        }

        try {
            double valuePerLevel = Double.parseDouble(valuePerLevelStr);
            double totalValue = valuePerLevel * newLevel;

            // NamespacedKey determinística para el modifier
            NamespacedKey modifierKey = new NamespacedKey("eventui", "skill_" + node.getId());

            // Remover modifier anterior si existe (por key)
            for (AttributeModifier existing : attrInstance.getModifiers()) {
                if (modifierKey.equals(existing.getKey())) {
                    attrInstance.removeModifier(existing);
                    break;
                }
            }

            // Crear e aplicar nuevo modifier con NamespacedKey (1.21+ compatible)
            AttributeModifier.Operation operation = AttributeModifier.Operation.ADD_NUMBER;

            AttributeModifier modifier = new AttributeModifier(
                    modifierKey,
                    totalValue,
                    operation
            );

            attrInstance.addModifier(modifier);

            LOGGER.fine("Applied attribute " + attributeName + " (" + operation + ") = "
                    + totalValue + " to player " + player.getName() + " for skill " + node.getId());

        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid value_per_level for node " + node.getId() + ": " + valuePerLevelStr);
        }
    }

    /**
     * Ejecuta un comando asociado a un efecto.
     * Solo ejecuta si el level actual coincide con at_level (si está especificado).
     */
    private void applyCommandEffect(Player player, SkillEffect effect, int newLevel) {
        var data = effect.getData();

        String command = data.get("command");
        String atLevelStr = data.get("at_level");

        if (command == null || command.isBlank()) {
            LOGGER.warning("Command effect missing 'command' field");
            return;
        }

        // Si at_level está especificado, solo ejecutar cuando newLevel sea exactamente ese valor
        if (atLevelStr != null && !atLevelStr.isBlank()) {
            try {
                int atLevel = Integer.parseInt(atLevelStr);
                if (newLevel != atLevel) {
                    return; // No ejecutar en este level
                }
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid at_level value: " + atLevelStr);
                return;
            }
        }

        // Reemplazar placeholder {player} con el nombre del jugador
        String finalCommand = command.replace("{player}", player.getName());

        // Ejecutar comando desde la consola
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);

        LOGGER.fine("Executed skill command: " + finalCommand);
    }
}
