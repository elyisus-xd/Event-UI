package com.eventui.api.bridge;

import java.util.List;

public record ExclusiveGroupData(
        String id,
        String name,
        String description,
        int maxSelections,
        List<ExclusiveBranchData> branches
) {}
