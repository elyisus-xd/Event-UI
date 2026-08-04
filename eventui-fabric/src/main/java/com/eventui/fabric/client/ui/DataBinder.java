package com.eventui.fabric.client.ui;

import com.eventui.fabric.client.bridge.UIStateCache;
import com.eventui.fabric.client.viewmodel.EventViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataBinder.class);
    private static final Pattern BINDING_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    public static String resolveBindings(String template, Map<String, Object> context) {
        if (template == null || !template.contains("{{")) {
            return template;
        }

        Matcher matcher = BINDING_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varPath = matcher.group(1).trim();
            String value = resolveVariable(varPath, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveVariable(String varPath, Map<String, Object> context) {
        String[] parts = varPath.split("\\.");

        if (parts.length == 0) {
            return "{{" + varPath + "}}";
        }

        Object current = context.get(parts[0]);

        if (current == null) {
            LOGGER.debug("Variable not found in context: {}", parts[0]);
            return "{{" + varPath + "}}";
        }

        for (int i = 1; i < parts.length; i++) {
            String property = parts[i];

            if (current instanceof EventViewModel.EventData event) {
                current = getEventProperty(event, property);
            } else if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(property);
            } else {
                LOGGER.debug("Cannot navigate path: {} on {}", property, current.getClass());
                return "{{" + varPath + "}}";
            }

            if (current == null) {
                return "{{" + varPath + "}}";
            }
        }

        return current.toString();
    }

    private static Object getEventProperty(EventViewModel.EventData event, String property) {
        return switch (property) {
            case "id" -> event.id;
            case "displayName", "display_name" -> event.displayName;
            case "description" -> event.description;
            case "state" -> event.state.name();
            case "currentProgress", "current_progress" -> event.currentProgress;
            case "targetProgress", "target_progress" -> event.targetProgress;
            case "currentObjective", "current_objective" ->
                    event.currentObjectiveDescription != null ? event.currentObjectiveDescription : "";
            case "progressPercentage", "progress_percentage" ->
                    String.format("%.0f", event.getProgressPercentage() * 100);
            case "progress", "progressValue" -> event.getProgressPercentage();
            case "repeatable" -> event.repeatable;
            case "icon" -> event.icon != null ? event.icon : "";
            case "entityType", "entity_type" -> {
                LOGGER.debug("DataBinder: entityType requested, value = {}", event.entityType);
                yield event.entityType != null ? event.entityType : "";
            }
            case "blockType", "block_type" -> {
                LOGGER.debug("DataBinder: blockType requested, value = {}", event.blockType);
                yield event.blockType != null ? event.blockType : "";
            }
            default -> {
                LOGGER.debug("Unknown event property: {}", property);
                yield "{{" + property + "}}";
            }
        };
    }

    public static Map<String, Object> createEventContext(EventViewModel.EventData event) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", event);
        context.put("progress", Map.of(
                "current", event.currentProgress,
                "target", event.targetProgress,
                "percentage", event.getProgressPercentage() * 100
        ));

        return context;
    }

    public static Map<String, Object> createGlobalContext(List<EventViewModel.EventData> events) {
        Map<String, Object> context = new HashMap<>();
        context.put("event_count", events.size());
        context.put("events", events);
        long inProgress = events.stream().filter(e -> e.state == com.eventui.api.event.EventState.IN_PROGRESS).count();
        long available = events.stream().filter(e -> e.state == com.eventui.api.event.EventState.AVAILABLE).count();
        long completed = events.stream().filter(e -> e.state == com.eventui.api.event.EventState.COMPLETED).count();

        context.put("in_progress_count", inProgress);
        context.put("available_count", available);
        context.put("completed_count", completed);

        return context;
    }

    public static boolean evaluateCondition(String condition) {
        if (condition == null || condition.isBlank()) return true;
        Map<String, String> state = UIStateCache.getInstance().getAll();
        condition = condition.trim();
        if (condition.contains("==")) {
            String[] parts = condition.split("==", 2);
            String key   = parts[0].trim();
            String expected = parts[1].trim();
            return expected.equals(state.getOrDefault(key, ""));
        }

        if (condition.contains("=") && !condition.contains("==") && !condition.contains("!=")) {
            String[] parts = condition.split("=", 2);
            String key   = parts[0].trim();
            String expected = parts[1].trim();
            return expected.equals(state.getOrDefault(key, ""));
        }

        if (condition.contains("!=")) {
            String[] parts = condition.split("!=", 2);
            String key   = parts[0].trim();
            String expected = parts[1].trim();
            return !expected.equals(state.getOrDefault(key, ""));
        }

        if (condition.startsWith("!")) {
            String key = condition.substring(1).trim();
            String value = state.get(key);
            return value == null || value.equals("false") || value.equals("0");
        }
        String value = state.get(condition);
        return value != null && !value.equals("false") && !value.equals("0");
    }

}
