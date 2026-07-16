package com.eventui.api.bridge;

import java.util.List;
import java.util.Map;

public record SkillTreeData(
        String id,
        String displayName,
        String pointType,
        Map<String, SkillNodeData> nodes,
        List<ExclusiveGroupData> exclusiveGroups,
        Map<String, String> selectedBranches
) {}
