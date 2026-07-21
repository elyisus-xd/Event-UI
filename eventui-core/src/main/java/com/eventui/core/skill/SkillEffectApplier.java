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

public class SkillEffectApplier {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    public SkillEffectApplier(EventUIPlugin plugin) {
    }

    public void applyNodeEffects(Player player, SkillNodeDefinition node, int newLevel) {
        applyNodeEffects(player, node, newLevel, true);
    }

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

    public void removeNodeEffects(Player player, SkillNodeDefinition node) {
        for (SkillEffect effect : node.getEffects()) {
            if (!"attribute".equalsIgnoreCase(effect.getType())) continue;

            String attributeName = effect.getData().get("attribute");
            if (attributeName == null || attributeName.isBlank()) continue;

            Attribute attribute;
            try {
                attribute = Attribute.valueOf(attributeName);
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Unknown attribute '" + attributeName + "' while removing effects for node " + node.getId());
                continue;
            }

            AttributeInstance attrInstance = player.getAttribute(attribute);
            if (attrInstance == null) continue;

            NamespacedKey modifierKey = new NamespacedKey("eventui", "skill_" + node.getId());
            attrInstance.getModifiers().stream()
                    .filter(m -> modifierKey.equals(m.getKey()))
                    .findFirst()
                    .ifPresent(attrInstance::removeModifier);

            LOGGER.fine("Removed attribute " + attributeName + " from " + player.getName() + " (node: " + node.getId() + ")");
        }
    }

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

            NamespacedKey modifierKey = new NamespacedKey("eventui", "skill_" + node.getId());

            for (AttributeModifier existing : attrInstance.getModifiers()) {
                if (modifierKey.equals(existing.getKey())) {
                    attrInstance.removeModifier(existing);
                    break;
                }
            }

            AttributeModifier.Operation operation = parseOperation(operationStr);

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

    private void applyCommandEffect(Player player, SkillEffect effect, int newLevel) {
        var data = effect.getData();

        String command = data.get("command");
        String atLevelStr = data.get("at_level");

        if (command == null || command.isBlank()) {
            LOGGER.warning("Command effect missing 'command' field");
            return;
        }

        if (atLevelStr != null && !atLevelStr.isBlank()) {
            try {
                int atLevel = Integer.parseInt(atLevelStr);
                if (newLevel != atLevel) {
                    return; 
                }
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid at_level value: " + atLevelStr);
                return;
            }
        }

        String finalCommand = command.replace("{player}", player.getName());

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);

        LOGGER.fine("Executed skill command: " + finalCommand);
    }

    private AttributeModifier.Operation parseOperation(String operationStr) {
        if (operationStr == null || operationStr.isBlank()) {
            return AttributeModifier.Operation.ADD_NUMBER;
        }

        return switch (operationStr.toLowerCase()) {
            case "add", "add_number" -> AttributeModifier.Operation.ADD_NUMBER;
            case "multiply" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            case "add_percent", "add_scalar" -> AttributeModifier.Operation.ADD_SCALAR;
            default -> {
                LOGGER.warning("Unknown operation '" + operationStr + "', defaulting to ADD_NUMBER");
                yield AttributeModifier.Operation.ADD_NUMBER;
            }
        };
    }
}
