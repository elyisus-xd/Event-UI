package com.eventui.core.objective;

import com.eventui.api.objective.ObjectiveDefinition;
import com.eventui.api.objective.ObjectiveType;

import java.util.Collections;
import java.util.Map;

public record ObjectiveDefinitionImpl(
        String id,
        ObjectiveType type,
        String description,
        int targetAmount,
        Map<String, String> parameters,
        Map<String, String> uiResources,
        boolean optional
) implements ObjectiveDefinition {

    public ObjectiveDefinitionImpl {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Objective ID cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Objective type cannot be null");
        }
        if (targetAmount <= 0) {
            throw new IllegalArgumentException("Target amount must be positive");
        }

        parameters = parameters != null
                ? Collections.unmodifiableMap(Map.copyOf(parameters))
                : Map.of();
        uiResources = uiResources != null
                ? Collections.unmodifiableMap(Map.copyOf(uiResources))
                : Map.of();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ObjectiveType getType() {
        return type;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getTargetAmount() {
        return targetAmount;
    }

    @Override
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public Map<String, String> getUIResources() {
        return uiResources;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }
}
