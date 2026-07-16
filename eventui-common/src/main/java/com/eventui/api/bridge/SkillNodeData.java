package com.eventui.api.bridge;

import java.util.List;

public record SkillNodeData(
        String id,
        String displayName,
        String description,
        String icon,
        int maxLevel,
        int currentLevel,
        int costNextLevel,
        String state,
        List<SkillRequirementData> requires,
        String requiresMode,
        int positionX,
        int positionY,
        String textureOverrideLocked,
        String textureOverrideAvailable,
        String textureOverridePartial,
        String textureOverrideMaxed,
        String exclusiveGroupId,
        String exclusiveBranchId
) {}
