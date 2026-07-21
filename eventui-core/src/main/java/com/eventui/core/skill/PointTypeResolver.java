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

        if (aliases.isEmpty()) {
            aliases.put("puntos de combate", "combat_points");
            aliases.put("puntos de habilidad", "skill_points");
            aliases.put("puntos de recolección", "gathering_points");
            aliases.put("combat points", "combat_points");
            aliases.put("skill points", "skill_points");
            aliases.put("gathering points", "gathering_points");
            
            aliases.put("puntos de combate", "combat_points");
            aliases.put("puntos de habilidad", "skill_points");
            aliases.put("puntos de recolección", "gathering_points");
        }
    }

    public String resolve(String input) {
        if (input == null || input.isEmpty()) return input;

        String lowerInput = input.toLowerCase();

        if (input.contains("_")) {
            return input;
        }

        String resolved = aliases.get(lowerInput);
        if (resolved != null) {
            return resolved;
        }

        return input;
    }

    public String getDisplayName(String internalId) {
        if (internalId == null || internalId.isEmpty()) return internalId;

        String displayName = reverseAliases.get(internalId);
        return displayName != null ? displayName : internalId;
    }

    public boolean isAlias(String name) {
        if (name == null || name.isEmpty()) return false;
        return aliases.containsKey(name.toLowerCase());
    }
}
