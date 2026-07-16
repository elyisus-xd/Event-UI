package com.eventui.core.skill;

import com.eventui.api.skill.ExclusiveBranch;
import com.eventui.api.skill.ExclusiveGroup;

import java.util.List;

public record ExclusiveGroupImpl(
        String id,
        String name,
        String description,
        int maxSelections,
        List<ExclusiveBranch> branches
) implements ExclusiveGroup {

    public ExclusiveGroupImpl {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ExclusiveGroup ID cannot be null or blank");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("ExclusiveGroup name cannot be null or blank");
        if (maxSelections < 1)
            throw new IllegalArgumentException("ExclusiveGroup maxSelections must be at least 1");

        branches = branches != null ? List.copyOf(branches) : List.of();
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
    public String getDescription() {
        return description;
    }

    @Override
    public int getMaxSelections() {
        return maxSelections;
    }

    @Override
    public List<ExclusiveBranch> getBranches() {
        return branches;
    }
}
