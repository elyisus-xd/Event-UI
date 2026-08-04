package com.eventui.fabric.client.ui;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class QuestTrackerConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestTrackerConfig.class);
    private static QuestTrackerConfig instance;
    
    private boolean persistentHudEnabled = true;
    private String hudMode = "custom"; 
    private PositionConfig hudPosition = new PositionConfig();
    private List<ElementConfig> hudElements = new ArrayList<>();
    
    private boolean notificationsEnabled = true;
    private int maxNotifications = 5;
    private String stackDirection = "up";
    private int notificationMargin = 20; 
    private AnimationConfig defaultAnimation = new AnimationConfig();
    private Map<String, NotificationTemplate> notificationTemplates = new HashMap<>();
    
    public static QuestTrackerConfig getInstance() {
        if (instance == null) {
            instance = loadConfig();
        }
        return instance;
    }
    
    public static QuestTrackerConfig loadConfig() {
        QuestTrackerConfig config = new QuestTrackerConfig();

        
        File configFile = getConfigFile();
        LOGGER.info("Loading config from: {}", configFile.getAbsolutePath());
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config = loadFromStream(fis);
                LOGGER.info("Successfully loaded config from file");
            } catch (IOException e) {
                LOGGER.error("Failed to load quest_tracker_config.yml from config directory", e);
            }
        } else {
            LOGGER.info("Config file does not exist, loading default from resources");
            
            try (InputStream is = QuestTrackerConfig.class.getResourceAsStream("/quest_tracker_config.yml")) {
                if (is != null) {
                    config = loadFromStream(is);
                    
                    copyDefaultConfig(configFile);
                    LOGGER.info("Loaded default config and copied to config directory");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load default quest_tracker_config.yml", e);
            }
        }

        instance = config;
        return config;
    }

    private static void copyDefaultConfig(File configFile) {
        try (InputStream is = QuestTrackerConfig.class.getResourceAsStream("/quest_tracker_config.yml")) {
            if (is != null) {
                configFile.getParentFile().mkdirs();
                java.nio.file.Files.copy(is, configFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to copy default config to config directory", e);
        }
    }

    public static void reload() {
        instance = loadConfig();
    }

    public static QuestTrackerConfig loadFromString(String configContent) {
        QuestTrackerConfig config = new QuestTrackerConfig();
        try {
            byte[] bytes = configContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            config = loadFromStream(new java.io.ByteArrayInputStream(bytes));
        } catch (IOException e) {
            LOGGER.error("Failed to load quest_tracker_config from string", e);
        }
        instance = config;
        return config;
    }

    private static QuestTrackerConfig loadFromStream(InputStream is) throws IOException {
        byte[] bytes = is.readAllBytes();
        Yaml yaml = new Yaml();
        Map<String, Object> data = null;

        try {
            data = yaml.load(new InputStreamReader(
                new java.io.ByteArrayInputStream(bytes),
                java.nio.charset.StandardCharsets.UTF_8
            ));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse YAML as UTF-8, retrying with default encoding");
            try {
                data = yaml.load(new java.io.ByteArrayInputStream(bytes));
            } catch (Exception e2) {
                LOGGER.error("Failed to parse YAML", e2);
                return new QuestTrackerConfig();
            }
        }

        QuestTrackerConfig config = new QuestTrackerConfig();
        
        if (data == null || !data.containsKey("quest_tracker")) {
            return config;
        }
        
        Map<String, Object> questTracker = (Map<String, Object>) data.get("quest_tracker");
        
        
        if (questTracker.containsKey("persistent_hud")) {
            Map<String, Object> persistentHud = (Map<String, Object>) questTracker.get("persistent_hud");
            config.persistentHudEnabled = parseBoolean(persistentHud.get("enabled"), true);
            config.hudMode = parseString(persistentHud.get("mode"), "custom");
            
            if (persistentHud.containsKey("position")) {
                Map<String, Object> position = (Map<String, Object>) persistentHud.get("position");
                config.hudPosition = new PositionConfig(
                    parseString(position.get("x"), "screen_width - 210"),
                    parseString(position.get("y"), "10"),
                    parseString(position.get("anchor"), "top_right")
                );
            }
            
            if (persistentHud.containsKey("elements")) {
                List<Map<String, Object>> elements = (List<Map<String, Object>>) persistentHud.get("elements");
                for (Map<String, Object> elementData : elements) {
                    config.hudElements.add(ElementConfig.fromMap(elementData));
                }
            }
        }
        
        
        if (questTracker.containsKey("notifications")) {
            Map<String, Object> notifications = (Map<String, Object>) questTracker.get("notifications");
            config.notificationsEnabled = parseBoolean(notifications.get("enabled"), true);
            config.maxNotifications = parseInt(notifications.get("max_notifications"), 5);
            config.stackDirection = parseString(notifications.get("stack_direction"), "up");
            config.notificationMargin = parseInt(notifications.get("margin"), 20);
            
            if (notifications.containsKey("default_animation")) {
                Map<String, Object> animData = (Map<String, Object>) notifications.get("default_animation");
                config.defaultAnimation = new AnimationConfig(
                    parseString(animData.get("entry"), "slide_left"),
                    parseString(animData.get("exit"), "fade_out"),
                    parseInt(animData.get("duration"), 2000),
                    parseInt(animData.get("entry_duration"), 300),
                    parseInt(animData.get("exit_duration"), 200)
                );
            }
            
            if (notifications.containsKey("templates")) {
                Map<String, Object> templates = (Map<String, Object>) notifications.get("templates");
                for (Map.Entry<String, Object> entry : templates.entrySet()) {
                    Map<String, Object> templateData = (Map<String, Object>) entry.getValue();
                    config.notificationTemplates.put(entry.getKey(), NotificationTemplate.fromMap(templateData));
                }
            }
        }
        
        return config;
    }
    
    private static File getConfigFile() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        return new File(configDir, "quest_tracker_config.yml");
    }
    
    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }
    
    private static int parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static String parseString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        return value.toString();
    }
    
    
    public boolean isPersistentHudEnabled() { return persistentHudEnabled; }
    public String getHudMode() { return hudMode; }
    public PositionConfig getHudPosition() { return hudPosition; }
    public List<ElementConfig> getHudElements() { return hudElements; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public int getMaxNotifications() { return maxNotifications; }
    public String getStackDirection() { return stackDirection; }
    public int getNotificationMargin() { return notificationMargin; }
    public AnimationConfig getDefaultAnimation() { return defaultAnimation; }
    public Map<String, NotificationTemplate> getNotificationTemplates() { return notificationTemplates; }
    
    
    public static class PositionConfig {
        private final String x;
        private final String y;
        private final String anchor;
        
        public PositionConfig() {
            this("screen_width - 210", "10", "top_right");
        }
        
        public PositionConfig(String x, String y, String anchor) {
            this.x = x;
            this.y = y;
            this.anchor = anchor;
        }
        
        public String getX() { return x; }
        public String getY() { return y; }
        public String getAnchor() { return anchor; }
    }
    
    public static class AnimationConfig {
        private final String entry;
        private final String exit;
        private final int duration;
        private final int entryDuration;
        private final int exitDuration;
        
        public AnimationConfig() {
            this("slide_left", "fade_out", 2000, 300, 200);
        }
        
        public AnimationConfig(String entry, String exit, int duration, int entryDuration, int exitDuration) {
            this.entry = entry;
            this.exit = exit;
            this.duration = duration;
            this.entryDuration = entryDuration;
            this.exitDuration = exitDuration;
        }
        
        public String getEntry() { return entry; }
        public String getExit() { return exit; }
        public int getDuration() { return duration; }
        public int getEntryDuration() { return entryDuration; }
        public int getExitDuration() { return exitDuration; }
    }
    
    public static class ElementConfig {
        private final String type;
        private final String id;
        private final String x;
        private final String y;
        private final String width;
        private final String height;
        private final Map<String, String> properties;
        private final List<ElementConfig> children;
        
        public ElementConfig() {
            this("panel", "unknown", "0", "0", "0", "0", new HashMap<>(), new ArrayList<>());
        }
        
        public ElementConfig(String type, String id, String x, String y, String width, String height, 
                           Map<String, String> properties, List<ElementConfig> children) {
            this.type = type;
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.properties = properties;
            this.children = children;
        }
        
        public static ElementConfig fromMap(Map<String, Object> data) {
            if (data == null) {
                LOGGER.error("ElementConfig.fromMap called with null data");
                return new ElementConfig("unknown", "unknown", "0", "0", "0", "0", new HashMap<>(), new ArrayList<>());
            }
            
            String type = parseString(data.get("type"), "panel");
            String id = parseString(data.get("id"), "unknown");
            String x = parseString(data.get("x"), "0");
            String y = parseString(data.get("y"), "0");
            String width = parseString(data.get("width"), "0");
            String height = parseString(data.get("height"), "0");
            
            Map<String, String> properties = new HashMap<>();
            if (data.containsKey("properties")) {
                Object propsObj = data.get("properties");
                if (propsObj instanceof Map) {
                    Map<String, Object> props = (Map<String, Object>) propsObj;
                    for (Map.Entry<String, Object> entry : props.entrySet()) {
                        properties.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
                    }
                } else {
                    LOGGER.error("Invalid 'properties' in config for element '{}': expected Map, got {}. Value: {}", 
                        id, propsObj.getClass().getSimpleName(), propsObj);
                }
            }
            
            List<ElementConfig> children = new ArrayList<>();
            if (data.containsKey("children")) {
                Object childrenObj = data.get("children");
                if (childrenObj instanceof List) {
                    List<?> childrenData = (List<?>) childrenObj;
                    for (Object childObj : childrenData) {
                        if (childObj instanceof Map) {
                            children.add(fromMap((Map<String, Object>) childObj));
                        } else {
                            LOGGER.error("Invalid child element in config: expected Map, got {}. Element ID: {}", 
                                childObj.getClass().getSimpleName(), id);
                        }
                    }
                } else {
                    LOGGER.error("Invalid 'children' in config for element '{}': expected List, got {}. Value: {}", 
                        id, childrenObj.getClass().getSimpleName(), childrenObj);
                }
            }
            
            return new ElementConfig(type, id, x, y, width, height, properties, children);
        }
        
        public String getType() { return type; }
        public String getId() { return id; }
        public String getX() { return x; }
        public String getY() { return y; }
        public String getWidth() { return width; }
        public String getHeight() { return height; }
        public Map<String, String> getProperties() { return properties; }
        public List<ElementConfig> getChildren() { return children; }
    }
    
    public static class NotificationTemplate {
        private final List<ElementConfig> elements;
        
        public NotificationTemplate() {
            this.elements = new ArrayList<>();
        }
        
        public NotificationTemplate(List<ElementConfig> elements) {
            this.elements = elements;
        }
        
        public static NotificationTemplate fromMap(Map<String, Object> data) {
            List<ElementConfig> elements = new ArrayList<>();
            if (data.containsKey("elements")) {
                List<Map<String, Object>> elementsData = (List<Map<String, Object>>) data.get("elements");
                for (Map<String, Object> elementData : elementsData) {
                    elements.add(ElementConfig.fromMap(elementData));
                }
            }
            return new NotificationTemplate(elements);
        }
        
        public List<ElementConfig> getElements() { return elements; }
    }
}
