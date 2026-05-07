package com.eventui.core.config;

import com.eventui.api.ui.UIConfig;
import com.eventui.api.ui.UIElement;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class UIConfigLoader {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final Yaml yaml;
    private final File uisDirectory;

    public UIConfigLoader(File pluginDataFolder) {
        this.yaml = new Yaml();
        this.uisDirectory = new File(pluginDataFolder, "uis");

        if (!uisDirectory.exists()) {
            uisDirectory.mkdirs();
            LOGGER.info("Created UIs directory at: " + uisDirectory.getAbsolutePath());
            createDefaultUIFile();
        }
    }

        public Map<String, UIConfig> loadAllUIConfigs() {
        Map<String, UIConfig> configs = new HashMap<>();

        List<File> allFiles = collectYamlFiles(uisDirectory);

        if (allFiles.isEmpty()) {
            LOGGER.warning("No UI config files found in " + uisDirectory.getPath() + " (including subdirectories)");
            return configs;
        }

        LOGGER.info("Found " + allFiles.size() + " UI file(s) across all directories");

        int loaded = 0;
        int failed = 0;

        for (File file : allFiles) {
            String relativePath = uisDirectory.toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace("\\", "/");

            try {
                UIConfig config = loadUIConfigFromFile(file);
                configs.put(config.getId(), config);
                LOGGER.fine("✓ Loaded UI: [" + config.getId() + "] ← " + relativePath);
                loaded++;

            } catch (Exception e) {
                logYamlError(relativePath, e);
                failed++;
            }
        }

        return configs;
    }

        private List<File> collectYamlFiles(File directory) {
        List<File> result = new ArrayList<>();

        File[] entries = directory.listFiles();
        if (entries == null) return result;

        Arrays.sort(entries, Comparator.comparing(File::getPath));

        for (File entry : entries) {
            if (entry.isDirectory()) {
                result.addAll(collectYamlFiles(entry));
            } else if (entry.isFile()) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    result.add(entry);
                }
            }
        }

        return result;
    }

        private void logYamlError(String relativePath, Exception e) {
        LOGGER.severe("════════════════════════════════════════");
        LOGGER.severe("  Failed to load UI: " + relativePath);
        LOGGER.severe("════════════════════════════════════════");
        LOGGER.severe("  Error: " + e.getMessage());

        Throwable cause = e.getCause();
        int depth = 1;
        while (cause != null && depth <= 3) {
            LOGGER.severe("  Cause[" + depth + "]: " + cause.getMessage());
            cause = cause.getCause();
            depth++;
        }

        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        LOGGER.severe("  ─────────────────────────────────────");
        if (msg.contains("null") || msg.contains("npe")) {
            LOGGER.severe("  Hint: A required field is missing or null.");
            LOGGER.severe("        Check that 'id', 'type', 'x', 'y' exist on all elements.");
        } else if (msg.contains("mapping") || msg.contains("indent") || msg.contains("tab")) {
            LOGGER.severe("  Hint: YAML indentation error detected.");
            LOGGER.severe("        Use 2 spaces per level. Tabs are NOT allowed in YAML.");
        } else if (msg.contains("illegalargument") || msg.contains("enum") || msg.contains("valueof")) {
            LOGGER.severe("  Hint: Unknown element type.");
            LOGGER.severe("        Valid types: IMAGE, IMAGE_BUTTON, TEXT, BUTTON,");
            LOGGER.severe("                     PANEL, PROGRESS_BAR, ICON, TOOLTIP,");
            LOGGER.severe("                     ENTITY_RENDER, ITEM_RENDER, BLOCK_RENDER");
        } else if (msg.contains("duplicate")) {
            LOGGER.severe("  Hint: Duplicate key found in YAML.");
            LOGGER.severe("        Each element 'id' must be unique within the file.");
        } else {
            LOGGER.severe("  Hint: Common causes:");
            LOGGER.severe("        - Comment '# text' at column 0 inside 'elements:' block");
            LOGGER.severe("          → Indent it with 2 spaces to match the elements level");
            LOGGER.severe("        - anchor/anchor_offset_x written outside 'properties:' block");
            LOGGER.severe("          → Move them inside the 'properties:' block");
            LOGGER.severe("        - Missing 'properties:' block on an element that needs it");
        }
        LOGGER.severe("════════════════════════════════════════");
    }

        public UIConfig loadUIConfigFromFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            Map<String, Object> data = yaml.load(fis);

            if (data == null) {
                throw new IllegalStateException("File is empty or contains only comments");
            }

            if (!data.containsKey("id")) {
                throw new IllegalStateException("Missing required field 'id' at root level");
            }

            if (!data.containsKey("elements")) {
                throw new IllegalStateException("Missing required field 'elements' at root level");
            }

            return parseUIConfig(data);
        }
    }

        @SuppressWarnings("unchecked")
    private UIConfig parseUIConfig(Map<String, Object> data) {
        String id = (String) data.get("id");
        String title = (String) data.getOrDefault("title", "EventUI");
        int screenWidth = ((Number) data.getOrDefault("screen_width", 320)).intValue();
        int screenHeight = ((Number) data.getOrDefault("screen_height", 240)).intValue();
        String associatedEventId = (String) data.get("associated_event_id");

        Map<String, String> screenProperties = new HashMap<>();
        Map<String, Object> propsData = (Map<String, Object>) data.get("screen_properties");
        if (propsData != null) {
            propsData.forEach((key, value) -> screenProperties.put(key, value.toString()));
        }

        List<UIElement> elements = new ArrayList<>();
        List<Map<String, Object>> elementsData = (List<Map<String, Object>>) data.get("elements");

        if (elementsData != null) {
            for (int i = 0; i < elementsData.size(); i++) {
                Map<String, Object> elementData = elementsData.get(i);
                try {
                    elements.add(parseUIElement(elementData));
                } catch (Exception e) {
                    String elemId = elementData.containsKey("id")
                            ? (String) elementData.get("id")
                            : "#" + i;
                    throw new IllegalStateException(
                            "Error parsing element '" + elemId + "' at index " + i + ": " + e.getMessage(), e
                    );
                }
            }
        }

        return new UIConfigImpl(
                id, title, screenWidth, screenHeight, elements, associatedEventId, screenProperties
        );
    }

        @SuppressWarnings("unchecked")
    private UIElement parseUIElement(Map<String, Object> data) {
        String id = (String) data.get("id");
        String typeStr = (String) data.get("type");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Element is missing required field 'id'");
        }
        if (typeStr == null || typeStr.isBlank()) {
            throw new IllegalStateException("Element '" + id + "' is missing required field 'type'");
        }

        com.eventui.api.ui.UIElementType type;
        try {
            type = com.eventui.api.ui.UIElementType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Element '" + id + "' has unknown type '" + typeStr + "'. " +
                            "Valid types: IMAGE, IMAGE_BUTTON, TEXT, BUTTON, PANEL, " +
                            "PROGRESS_BAR, ICON, TOOLTIP, ENTITY_RENDER, ITEM_RENDER, BLOCK_RENDER"
            );
        }

        if (!data.containsKey("x") || !data.containsKey("y")) {
            throw new IllegalStateException(
                    "Element '" + id + "' is missing required field(s): " +
                            (!data.containsKey("x") ? "'x' " : "") +
                            (!data.containsKey("y") ? "'y'" : "")
            );
        }

        int x = ((Number) data.get("x")).intValue();
        int y = ((Number) data.get("y")).intValue();
        int width = ((Number) data.getOrDefault("width", 100)).intValue();
        int height = ((Number) data.getOrDefault("height", 20)).intValue();
        int zIndex = ((Number) data.getOrDefault("z_index", 0)).intValue();
        boolean visible = (Boolean) data.getOrDefault("visible", true);

        Map<String, String> properties = new HashMap<>();
        Map<String, Object> propsData = (Map<String, Object>) data.get("properties");
        if (propsData != null) {
            propsData.forEach((key, value) -> properties.put(key, value != null ? value.toString() : ""));
        }

        if (data.containsKey("texture")) {
            properties.put("texture", (String) data.get("texture"));
        }
        if (data.containsKey("hover_texture")) {
            properties.put("hover_texture", (String) data.get("hover_texture"));
        }
        if (data.containsKey("action")) {
            Map<String, Object> actionData = (Map<String, Object>) data.get("action");
            properties.put("action_type", (String) actionData.get("type"));
            properties.put("action_target", (String) actionData.get("target"));
        }

        Object badgeObj = data.get("badge");
        if (badgeObj instanceof Map<?, ?> rawBadge) {
            Map<String, Object> badgeMap = (Map<String, Object>) rawBadge;
            properties.put("badge_enabled", String.valueOf(badgeMap.getOrDefault("enabled", true)));
            if (badgeMap.containsKey("texture"))
                properties.put("badge_texture", (String) badgeMap.get("texture"));
            properties.put("badge_x_offset", String.valueOf(badgeMap.getOrDefault("offset_x", badgeMap.getOrDefault("x_offset", 0))));
            properties.put("badge_y_offset", String.valueOf(badgeMap.getOrDefault("offset_y", badgeMap.getOrDefault("y_offset", 0))));
            properties.put("badge_width",  String.valueOf(badgeMap.getOrDefault("width",  16)));
            properties.put("badge_height", String.valueOf(badgeMap.getOrDefault("height", 16)));
            if (badgeMap.containsKey("visible_if"))
                properties.put("badge_condition", (String) badgeMap.get("visible_if"));
            if (badgeMap.containsKey("condition"))
                properties.put("badge_condition", (String) badgeMap.get("condition"));
            if (badgeMap.containsKey("disappear_on"))
                properties.put("badge_disappear_on", (String) badgeMap.get("disappear_on"));
        }

        if (data.containsKey("tooltip")) {
            Map<String, Object> tooltipData = (Map<String, Object>) data.get("tooltip");
            properties.put("tooltip_type", (String) tooltipData.getOrDefault("type", "INLINE"));
            if (tooltipData.containsKey("lines")) {
                List<Map<String, Object>> linesData = (List<Map<String, Object>>) tooltipData.get("lines");
                StringBuilder jsonBuilder = new StringBuilder("[");
                for (int i = 0; i < linesData.size(); i++) {
                    Map<String, Object> line = linesData.get(i);
                    jsonBuilder.append("{")
                            .append("\"text\":\"").append(line.get("text")).append("\",")
                            .append("\"x\":").append(line.getOrDefault("x", 0)).append(",")
                            .append("\"y\":").append(line.getOrDefault("y", 0))
                            .append("}");
                    if (i < linesData.size() - 1) jsonBuilder.append(",");
                }
                jsonBuilder.append("]");
                properties.put("tooltip_lines", jsonBuilder.toString());
            }
        }

        List<UIElement> children = new ArrayList<>();
        List<Map<String, Object>> childrenData = (List<Map<String, Object>>) data.get("children");
        if (childrenData != null) {
            for (int i = 0; i < childrenData.size(); i++) {
                Map<String, Object> childData = childrenData.get(i);
                try {
                    children.add(parseUIElement(childData));
                } catch (Exception e) {
                    String childId = childData.containsKey("id")
                            ? (String) childData.get("id")
                            : "#" + i;
                    throw new IllegalStateException(
                            "Error parsing child '" + childId + "' of element '" + id + "': " + e.getMessage(), e
                    );
                }
            }
        }

        return new UIElementImpl(
                id, type, x, y, width, height, properties, children, visible, zIndex
        );
    }

    private void createDefaultUIFile() {
        File defaultFile = new File(uisDirectory, "default_event_list.yml");
        if (defaultFile.exists()) return;

        String defaultYaml = """
                id: default_event_list
                title: "Events"
                screen_width: 320
                screen_height: 240
                screen_properties:
                  blur_background: true
                  pause_game: false
                elements:
                  - id: title_text
                    type: TEXT
                    x: 160
                    y: 20
                    width: 200
                    height: 20
                    z_index: 10
                    properties:
                      content: "§6§lEVENTS"
                      align: "center"
                      shadow: true
                  - id: close_button
                    type: BUTTON
                    x: 135
                    y: 210
                    width: 50
                    height: 20
                    z_index: 100
                    properties:
                      text: "Close"
                      action: "close_screen"
                """;

        try {
            java.nio.file.Files.writeString(defaultFile.toPath(), defaultYaml);
            LOGGER.fine("Created default UI config file: " + defaultFile.getName());
        } catch (IOException e) {
            LOGGER.severe("Failed to create default UI file: " + e.getMessage());
        }
    }

    public File getUisDirectory() {
        return uisDirectory;
    }
}
