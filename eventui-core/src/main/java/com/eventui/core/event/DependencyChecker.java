package com.eventui.core.event;

import com.eventui.api.event.EventDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DependencyChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyChecker.class);

        public static boolean isLocked(EventDefinition eventDef, Set<String> completedEventIds) {
        List<String> dependencies = eventDef.getDependencies();

        if (dependencies.isEmpty()) {
            return false;        }

        for (String depId : dependencies) {
            if (!completedEventIds.contains(depId)) {
                LOGGER.debug("Event {} is locked. Missing dependency: {}", eventDef.getId(), depId);
                return true;            }
        }

        LOGGER.debug("Event {} is unlocked. All dependencies met.", eventDef.getId());
        return false;
    }

        public static List<String> getMissingDependencies(EventDefinition eventDef, Set<String> completedEventIds) {
        List<String> missing = new ArrayList<>();

        for (String depId : eventDef.getDependencies()) {
            if (!completedEventIds.contains(depId)) {
                missing.add(depId);
            }
        }

        return missing;
    }

        public static List<String> getUnlockedEvents(String completedEventId,
                                                 List<EventDefinition> allEvents,
                                                 Set<String> completedEventIds) {
        List<String> unlocked = new ArrayList<>();

        for (EventDefinition event : allEvents) {
            if (event.getDependencies().isEmpty()) continue;

            if (!event.getDependencies().contains(completedEventId)) continue;

            if (!isLocked(event, completedEventIds)) {
                unlocked.add(event.getId());
            }
        }

        return unlocked;
    }
}
