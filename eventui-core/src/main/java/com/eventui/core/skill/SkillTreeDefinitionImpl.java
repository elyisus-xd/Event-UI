package com.eventui.core.skill;

import com.eventui.api.skill.*;

import java.util.List;

public record SkillTreeDefinitionImpl(
        String id,
        String displayName,
        String description,
        String pointType,
        List<SkillNodeDefinition> nodes,
        List<ExclusiveGroup> exclusiveGroups
) implements SkillTreeDefinition {

    public SkillTreeDefinitionImpl {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("SkillTree ID cannot be null or blank");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("SkillTree display name cannot be null or blank");
        if (pointType == null || pointType.isBlank())
            throw new IllegalArgumentException("SkillTree pointType cannot be null or blank");

        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        exclusiveGroups = exclusiveGroups != null ? List.copyOf(exclusiveGroups) : List.of();
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
    public String getPointType() {
        return pointType;
    }

    @Override
    public List<SkillNodeDefinition> getNodes() {
        return nodes;
    }

    @Override
    public List<ExclusiveGroup> getExclusiveGroups() {
        return exclusiveGroups;
    }
}
