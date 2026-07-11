package com.eventui.fabric.client.bridge;

import java.util.List;

import com.google.gson.annotations.SerializedName;

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
        @SerializedName("position_x") int positionX,
        @SerializedName("position_y") int positionY
) {}
