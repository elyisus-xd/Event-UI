package com.eventui.core.skill;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class PointTypeResolver {
    private final Map<String, String> aliases;
    private final Map<String, String> reverseAliases;

    public PointTypeResolver(FileConfiguration config) {
        this.aliases = new HashMap<>();
        this.reverseAliases = new HashMap<>();

        ConfigurationSection aliasesSection = config.getConfigurationSection("skills.point_type_aliases");
        if (aliasesSection != null) {
            for (String alias : aliasesSection.getKeys(false)) {
                String internalId = aliasesSection.getString(alias);
                aliases.put(alias.toLowerCase(), internalId);
                reverseAliases.put(internalId, alias);
            }
        }

        // Default aliases if not configured (case-insensitive)
        if (aliases.isEmpty()) {
            aliases.put("puntos de combate", "combat_points");
            aliases.put("puntos de habilidad", "skill_points");
            aliases.put("puntos de recolección", "gathering_points");
            aliases.put("combat points", "combat_points");
            aliases.put("skill points", "skill_points");
            aliases.put("gathering points", "gathering_points");
            // Also add capitalized variants
            aliases.put("puntos de combate", "combat_points");
            aliases.put("puntos de habilidad", "skill_points");
            aliases.put("puntos de recolección", "gathering_points");
        }
    }

    /**
     * Resolves a point type name (could be alias or internal ID) to internal ID.
     * @param input The input name (alias or internal ID)
     * @return The internal ID (e.g., "combat_points")
     */
    public String resolve(String input) {
        if (input == null || input.isEmpty()) return input;

        String lowerInput = input.toLowerCase();

        // If it's already an internal ID (contains underscore), return as-is
        if (input.contains("_")) {
            return input;
        }

        // Check if it's an alias
        String resolved = aliases.get(lowerInput);
        if (resolved != null) {
            return resolved;
        }

        // Return as-is if no alias found
        return input;
    }

    /**
     * Gets the display name for a point type (reverse lookup).
     * @param internalId The internal ID (e.g., "combat_points")
     * @return The display name (e.g., "Puntos de Combate") or the internal ID if no alias exists
     */
    public String getDisplayName(String internalId) {
        if (internalId == null || internalId.isEmpty()) return internalId;

        String displayName = reverseAliases.get(internalId);
        return displayName != null ? displayName : internalId;
    }

    /**
     * Checks if a name is an alias (not an internal ID).
     */
    public boolean isAlias(String name) {
        if (name == null || name.isEmpty()) return false;
        return aliases.containsKey(name.toLowerCase());
    }
}
