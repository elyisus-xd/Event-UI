package com.eventui.core.skill;

import com.eventui.api.skill.ExclusiveBranch;

import java.util.List;

public record ExclusiveBranchImpl(
        String id,
        String name,
        List<String> nodeIds
) implements ExclusiveBranch {

    public ExclusiveBranchImpl {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ExclusiveBranch ID cannot be null or blank");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("ExclusiveBranch name cannot be null or blank");

        nodeIds = nodeIds != null ? List.copyOf(nodeIds) : List.of();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getNodeIds() {
        return nodeIds;
    }
}
