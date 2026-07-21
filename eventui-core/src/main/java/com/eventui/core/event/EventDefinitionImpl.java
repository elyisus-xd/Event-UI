package com.eventui.core.event;

import com.eventui.api.event.EventDefinition;
import com.eventui.api.objective.ObjectiveDefinition;

import java.util.List;
import java.util.Map;

public record EventDefinitionImpl(
        String id,
        String displayName,
        String description,
        List<ObjectiveDefinition> objectives,
        Map<String, String> uiResources,
        Map<String, String> metadata,
        List<String> dependencies,
        Boolean alwaysActive
) implements EventDefinition {

    public EventDefinitionImpl {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Event ID cannot be null or blank");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("Event display name cannot be null or blank");

        objectives   = objectives   != null ? List.copyOf(objectives) : List.of();
        uiResources  = uiResources  != null ? Map.copyOf(uiResources) : Map.of();
        metadata     = metadata     != null ? Map.copyOf(metadata) : Map.of();
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
    }

    @Override public String getId()                          { return id; }
    @Override public String getDisplayName()                 { return displayName; }
    @Override public String getDescription()                 { return description; }
    @Override public List<ObjectiveDefinition> getObjectives() { return objectives; }
    @Override public Map<String, String> getUIResources()    { return uiResources; }
    @Override public Map<String, String> getMetadata()       { return metadata; }
    @Override public List<String> getDependencies()          { return dependencies; }

    @Override
    public Boolean isAlwaysActive() {
        return alwaysActive;
    }
}
