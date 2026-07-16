package com.eventui.api.bridge;

import java.util.List;

public record ExclusiveBranchData(
        String id,
        String name,
        List<String> nodeIds
) {}
