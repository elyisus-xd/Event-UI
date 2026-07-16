package com.eventui.core.skill;

import com.eventui.api.skill.*;

import java.util.List;
import java.util.Map;

public record SkillNodeDefinitionImpl(
        String id,
        String displayName,
        String description,
        String icon,
        int maxLevel,
        List<Integer> costPerLevel,  // almacenamos internamente como lista
        List<SkillRequirement> requirements,
        String requiresMode,
        int positionX,
        int positionY,
        List<SkillEffect> effects,
        Map<String, String> textureOverrides,
        String exclusiveGroupId,
        String exclusiveBranchId
) implements SkillNodeDefinition {

    public SkillNodeDefinitionImpl {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("SkillNode ID cannot be null or blank");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("SkillNode display name cannot be null or blank");
        if (maxLevel <= 0)
            throw new IllegalArgumentException("SkillNode maxLevel must be > 0");

        costPerLevel = costPerLevel != null ? List.copyOf(costPerLevel) : List.of();
        requirements = requirements != null ? List.copyOf(requirements) : List.of();
        effects = effects != null ? List.copyOf(effects) : List.of();
        textureOverrides = textureOverrides != null ? Map.copyOf(textureOverrides) : Map.of();

        if (requiresMode == null || requiresMode.isBlank())
            requiresMode = "all";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getIcon() {
        return icon;
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public int getCostForLevel(int level) {
        if (level < 1 || level > maxLevel) {
            throw new IllegalArgumentException("Invalid level: " + level + " (valid: 1-" + maxLevel + ")");
        }
        // Si costPerLevel es una lista, usar el index (level-1)
        if (!costPerLevel.isEmpty()) {
            return costPerLevel.get(Math.min(level - 1, costPerLevel.size() - 1));
        }
        // Si está vacía, retornar 0 (no debería pasar con validación adecuada)
        return 0;
    }

    @Override
    public List<SkillRequirement> getRequirements() {
        return requirements;
    }

    @Override
    public String getRequiresMode() {
        return requiresMode;
    }

    @Override
    public int getPositionX() {
        return positionX;
    }

    @Override
    public int getPositionY() {
        return positionY;
    }

    @Override
    public List<SkillEffect> getEffects() {
        return effects;
    }

    @Override
    public Map<String, String> getTextureOverrides() {
        return textureOverrides;
    }

    @Override
    public String getExclusiveGroupId() {
        return exclusiveGroupId;
    }

    @Override
    public String getExclusiveBranchId() {
        return exclusiveBranchId;
    }
}
