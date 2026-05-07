package com.eventui.core.tracking;

import com.eventui.api.objective.ObjectiveDefinition;

@FunctionalInterface
public interface ObjectiveMatcher<C> {
    boolean matches(C context, ObjectiveDefinition objective);
}
