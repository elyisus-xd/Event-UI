package com.eventui.fabric.client.bridge;

import java.util.Map;

public record SkillTreeData(
        String id,
        String displayName,
        String pointType,
        Map<String, SkillNodeData> nodes
) {}
