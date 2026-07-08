package com.eventui.core.skill;

import com.eventui.api.skill.*;

import java.util.List;
import java.util.Map;

public record SkillEffectImpl(
        String type,
        Map<String, String> data
) implements SkillEffect {

    public SkillEffectImpl {
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("SkillEffect type cannot be null or blank");
        data = data != null ? Map.copyOf(data) : Map.of();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Map<String, String> getData() {
        return data;
    }
}
