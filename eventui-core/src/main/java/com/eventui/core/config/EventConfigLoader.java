package com.eventui.core.config;

import com.eventui.api.event.EventDefinition;
import com.eventui.api.objective.ObjectiveDefinition;
import com.eventui.api.objective.ObjectiveType;
import com.eventui.core.event.EventDefinitionImpl;
import com.eventui.core.objective.ObjectiveDefinitionImpl;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class EventConfigLoader {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final Yaml yaml;
    private final File eventsDirectory;

    public EventConfigLoader(File pluginDataFolder) {
        this.yaml = new Yaml();
        this.eventsDirectory = new File(pluginDataFolder, "events");

        if (!eventsDirectory.exists()) {
            eventsDirectory.mkdirs();
            LOGGER.info("Created events directory at: " + eventsDirectory.getAbsolutePath());
        }
    }

        public Map<String, EventDefinition> loadAllEvents() {
        Map<String, EventDefinition> events = new HashMap<>();
        List<File> files = findYamlFiles(eventsDirectory);

        if (files.isEmpty()) {
            LOGGER.warning("No event files found in: " + eventsDirectory.getAbsolutePath());
            createExampleEventFile();
            return events;
        }

        for (File file : files) {
            try {
                EventDefinition event = loadEventFromFile(file);
                events.put(event.getId(), event);

                String relativePath = eventsDirectory.toPath()
                        .relativize(file.toPath())
                        .toString();
                LOGGER.fine("✓ Loaded event: " + event.getId() + " from " + relativePath);

            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                LOGGER.severe("═══════════════════════════════");
                LOGGER.severe("YAML ERROR en archivo: " + file.getName());

                if (msg.contains("cannot be cast") && msg.contains("number")) {
                    LOGGER.severe("Problema: Un campo numérico tiene comillas.");
                    LOGGER.severe("   Incorrecto:  target_amount: \"25\"");
                    LOGGER.severe("   Correcto:    target_amount: 25");
                } else if (msg.contains("cannot be cast") && msg.contains("string")) {
                    LOGGER.severe("Problema: Un campo de texto necesita comillas.");
                    LOGGER.severe("   Incorrecto:  description: Mata 25: enemigos");
                    LOGGER.severe("   Correcto:    description: \"Mata 25: enemigos\"");
                } else if (msg.contains("null") || msg.contains("id")) {
                    LOGGER.severe("Problema: Falta un campo obligatorio (id, type, description).");
                } else if (msg.contains("enum") || msg.contains("valueof")) {
                    LOGGER.severe("Problema: Tipo de objetivo inválido.");
                    LOGGER.severe("  Tipos válidos: KILL_ENTITY, MINE_BLOCK, PLACE_BLOCK,");
                    LOGGER.severe("  CRAFT_ITEM, SMELT_ITEM, INTERACT, REACH_LEVEL, etc.");
                } else {
                    LOGGER.severe("Problema: " + e.getMessage());
                    LOGGER.severe("  - Verifica la indentación (espacios, no tabs)");
                    LOGGER.severe("  - Usa un validador online: yamllint.com");
                }
                LOGGER.severe("Archivo: " + file.getAbsolutePath());
                LOGGER.severe("═══════════════════════════════");
            }

        }

            validateDependencyGraph(events);      // ya existía — detecta ciclos
            validateDependencyReferences(events);

        if (events.isEmpty()) {
            LOGGER.warning("No events were loaded successfully. Check the errors above.");
        } else {
            LOGGER.info("Successfully loaded " + events.size() + " event(s) from " +
                    countDirectories(eventsDirectory) + " directory/directories");
        }

        return events;
    }

    private void validateDependencyReferences(Map<String, EventDefinition> events) {
        boolean foundBroken = false;

        for (EventDefinition event : events.values()) {
            List<String> deps = event.getDependencies();
            if (deps == null || deps.isEmpty()) continue;

            for (String depId : deps) {
                if (!events.containsKey(depId)) {
                    if (!foundBroken) {
                        LOGGER.warning("═══════════════════════════════════════════════");
                        LOGGER.warning("DEPENDENCIAS ROTAS DETECTADAS");
                        LOGGER.warning("Los siguientes eventos referencian dependencias");
                        LOGGER.warning("que no existen. Quedarán bloqueados para siempre.");
                        LOGGER.warning("═══════════════════════════════════════════════");
                        foundBroken = true;
                    }
                    LOGGER.warning("  Evento '" + event.getId() + "' → depende de '" + depId + "' (no encontrado)");
                    LOGGER.warning("    Solución: crea el evento '" + depId + "' o elimina");
                    LOGGER.warning("    esa dependencia del archivo de '" + event.getId() + "'.");
                }
            }
        }

        if (foundBroken) {
            LOGGER.warning("═══════════════════════════════════════════════");
        }
    }

        private void validateDependencyGraph(Map<String, EventDefinition> events) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<String> currentPath = new ArrayList<>();

        for (String eventId : events.keySet()) {
            if (!visited.contains(eventId)) {
                if (hasCycleDFS(eventId, events, visited, recursionStack, currentPath)) {
                    String cyclePath = String.join(" → ", currentPath);
                    throw new IllegalStateException(
                            "Dependency cycle detected: " + cyclePath + " → " + eventId
                    );
                }
            }
        }

        LOGGER.info("✓ Dependency graph validated: no cycles detected");
    }

        private boolean hasCycleDFS(
            String eventId,
            Map<String, EventDefinition> events,
            Set<String> visited,
            Set<String> recursionStack,
            List<String> currentPath
    ) {
        visited.add(eventId);
        recursionStack.add(eventId);
        currentPath.add(eventId);

        EventDefinition event = events.get(eventId);

        if (event == null) {
            LOGGER.warning("Event '" + eventId + "' referenced as dependency but not found");
            recursionStack.remove(eventId);
            currentPath.removeLast();
            return false;
        }

        List<String> dependencies = event.getDependencies();
        if (dependencies != null) {
            for (String depId : dependencies) {
                if (recursionStack.contains(depId)) {
                    currentPath.add(depId);
                    return true;
                }
                if (!visited.contains(depId)) {
                    if (hasCycleDFS(depId, events, visited, recursionStack, currentPath)) {
                        return true;
                    }
                }
            }
        }

        recursionStack.remove(eventId);
        currentPath.removeLast();
        return false;
    }

        private int countDirectories(File directory) {
        Set<File> directories = new HashSet<>();
        List<File> files = findYamlFiles(directory);

        for (File file : files) {
            directories.add(file.getParentFile());
        }

        return directories.size();
    }

        private List<File> findYamlFiles(File directory) {
        List<File> yamlFiles = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            return yamlFiles;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return yamlFiles;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                yamlFiles.addAll(findYamlFiles(file));
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                yamlFiles.add(file);
            }
        }

        return yamlFiles;
    }


        public EventDefinition loadEventFromFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            Map<String, Object> data = yaml.load(fis);
            return parseEvent(data);
        }
    }

        @SuppressWarnings("unchecked")
    private EventDefinition parseEvent(Map<String, Object> data) {
        String id = (String) data.get("id");
        String displayName = (String) data.get("displayName");
        if (displayName == null) {
            displayName = (String) data.get("display_name");
        }

        String description = (String) data.get("description");
        List<ObjectiveDefinition> objectives = new ArrayList<>();
        List<Map<String, Object>> objectivesList = (List<Map<String, Object>>) data.get("objectives");

        if (objectivesList != null) {
            for (Map<String, Object> objData : objectivesList) {
                objectives.add(parseObjective(objData));
            }
        }

        Map<String, String> uiResources = new HashMap<>();
        Map<String, Object> uiResourcesData = (Map<String, Object>) data.get("ui_resources");
        if (uiResourcesData != null) {
            uiResourcesData.forEach((key, value) -> uiResources.put(key, value.toString()));
        }

        Map<String, String> metadata = new HashMap<>();
        Map<String, Object> metadataData = (Map<String, Object>) data.get("metadata");
        if (metadataData != null) {
            metadataData.forEach((key, value) -> metadata.put(key, value.toString()));
        }

        String icon = (String) data.getOrDefault("icon", "minecraft:paper");
        metadata.put("icon", icon);

        List<String> dependencies = new ArrayList<>();
        Object depsObj = data.get("dependencies");
        if (depsObj instanceof List) {
            for (Object dep : (List<?>) depsObj) {
                if (dep instanceof String) {
                    dependencies.add((String) dep);
                }
            }
        }

        if (!dependencies.isEmpty()) {
            metadata.put("dependencies", new com.google.gson.Gson().toJson(dependencies));
            LOGGER.fine("Event '" + id + "' has " + dependencies.size() + " dependencies: " + dependencies);
        }

        Map<String, Object> rewardsData = (Map<String, Object>) data.get("rewards");

        if (rewardsData != null) {

            List<Map<String, Object>> rewardsList = new ArrayList<>();
            if (rewardsData.containsKey("xp")) {
                Map<String, Object> xpReward = new HashMap<>();
                xpReward.put("type", "xp");
                xpReward.put("amount", rewardsData.get("xp"));
                rewardsList.add(xpReward);
            }

            if (rewardsData.containsKey("items")) {
                Object itemsObj = rewardsData.get("items");
                if (itemsObj instanceof List) {
                    List<String> items = (List<String>) itemsObj;

                    for (String itemStr : items) {
                        String[] parts = itemStr.trim().split("\\s+");
                        String itemId = parts[0];
                        int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                        Map<String, Object> reward = new HashMap<>();
                        reward.put("type", "item");
                        reward.put("id", itemId);
                        reward.put("count", count);

                        rewardsList.add(reward);
                    }
                }
            }

            if (!rewardsList.isEmpty()) {
                String rewardsJson = new com.google.gson.Gson().toJson(rewardsList);
                metadata.put("rewards_data", rewardsJson);

                LOGGER.fine("Event '" + id + "' rewards serialized: " + rewardsJson);
            }
        }

        Boolean alwaysActive = null;
        if (data.containsKey("always_active")) {
            alwaysActive = (Boolean) data.get("always_active");
        }

        return new EventDefinitionImpl(id, displayName, description, objectives, uiResources, metadata, dependencies, alwaysActive);
    }


    @SuppressWarnings("unchecked")
    private ObjectiveDefinition parseObjective(Map<String, Object> data) {
        String id = (String) data.get("id");
        ObjectiveType type = ObjectiveType.valueOf(((String) data.get("type")).toUpperCase());
        String description = (String) data.get("description");

        if (UNIMPLEMENTED_TYPES.contains(type)) {
            LOGGER.warning("AVISO en objetivo '" + id + "':");
            LOGGER.warning("  El tipo '" + type.name() + "' está declarado pero NO tiene");
            LOGGER.warning("  handler activo en esta versión de EventUI.");
            LOGGER.warning("  El objetivo nunca avanzará.");
            if (type == ObjectiveType.CUSTOM) {
                LOGGER.warning("  Para usar CUSTOM necesitas registrar un handler");
                LOGGER.warning("  externo vía API. Ver documentación de API Bridge.");
            }
        }

        if (PARTIAL_TYPES.contains(type)) {
            LOGGER.warning("AVISO en objetivo '" + id + "':");
            if (type == ObjectiveType.COLLECT_ITEM) {
                LOGGER.warning("  COLLECT_ITEM tiene implementación parcial.");
                LOGGER.warning("  Solo avanza cuando el jugador GANA ítems.");
                LOGGER.warning("  Si los pierde o usa, el progreso no retrocede.");
                LOGGER.warning("  No apto para objetivos de tipo 'recolecta y entrega'.");
            } else if (type == ObjectiveType.REACH_LOCATION) {
                LOGGER.warning("  REACH_LOCATION tiene implementación parcial.");
                LOGGER.warning("  El scheduler usa valores hardcodeados y puede");
                LOGGER.warning("  no detectar la ubicación de forma consistente.");
            }
        }
        
        int targetAmount = 0;
        Map<String, String> parameters = new HashMap<>();
        
        Map<String, Object> parametersData = (Map<String, Object>) data.get("parameters");
        if (parametersData != null) {
            parametersData.forEach((key, value) -> {
                if (!"target_amount".equals(key)) {   // target_amount se lee aparte
                    parameters.put(key, value.toString());
                }
            });
            if (parametersData.containsKey("target_amount")) {
                targetAmount = parseTargetAmount(parametersData.get("target_amount"), id);
            }
        }
        
        if (data.containsKey("target_amount")) {
            targetAmount = parseTargetAmount(data.get("target_amount"), id);
        } else if (data.containsKey("target")) {
            Map<String, Object> targetData = (Map<String, Object>) data.get("target");
            if (targetData != null) {
                if (targetData.containsKey("count")) {
                    targetAmount = parseTargetAmount(targetData.get("count"), id);
                }
                targetData.forEach((key, value) -> {
                    if (!"count".equals(key)) parameters.put(key, value.toString());
                });
            }
        }
        
        // Validación de parámetros requeridos
        if (type == ObjectiveType.REACH_LOCATION) {
            if (!parameters.containsKey("x") || !parameters.containsKey("y") || !parameters.containsKey("z")) {
                LOGGER.warning("⚠ REACH_LOCATION objetivo '" + id + "' falta parámetros requeridos:");
                LOGGER.warning("  Requiere: x, y, z");
                LOGGER.warning("  Opcionales: radius (default 3), world (default actual)");
            }
        }
        
        if (type == ObjectiveType.CUSTOM) {
            if (!parameters.containsKey("custom_id")) {
                LOGGER.warning("⚠ CUSTOM objetivo '" + id + "' falta parámetro requerido: custom_id");
                LOGGER.warning("  Ejemplo: custom_id: 'mylib:my_handler_id'");
            }
        }
        
        if (type == ObjectiveType.BREW_POTION) {
            if (!parameters.containsKey("potion_type")) {
                LOGGER.warning("⚠ BREW_POTION objetivo '" + id + "' falta parámetro: potion_type");
                LOGGER.warning("  Ejemplo: potion_type: 'minecraft:healing'");
            }
        }
        
        if (type == ObjectiveType.COLLECT_ITEM) {
            if (!parameters.containsKey("item_id")) {
                LOGGER.warning("⚠ COLLECT_ITEM objetivo '" + id + "' falta parámetro: item_id");
                LOGGER.warning("  Ejemplo: item_id: 'minecraft:diamond'");
            }
        }

        Map<String, String> uiResources = new HashMap<>();
        Map<String, Object> uiResourcesData = (Map<String, Object>) data.get("ui_resources");
        if (uiResourcesData != null)
            uiResourcesData.forEach((key, value) -> uiResources.put(key, value.toString()));

        boolean optional = data.containsKey("optional") && Boolean.TRUE.equals(data.get("optional"));
        return new ObjectiveDefinitionImpl(id, type, description, targetAmount, parameters, uiResources, optional);
    }

    private static final Set<ObjectiveType> UNIMPLEMENTED_TYPES = Set.of();

    // Tipos con implementación parcial — funcionan con limitaciones conocidas
    private static final Set<ObjectiveType> PARTIAL_TYPES = Set.of();

    private int parseTargetAmount(Object value, String objectiveId) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                LOGGER.warning("Objetivo '" + objectiveId + "': target_amount \"" + s
                        + "\" no es un número válido. Se usará 1.");
                return 1;
            }
        }
        LOGGER.warning("Objetivo '" + objectiveId + "': target_amount tiene tipo inesperado "
                + value.getClass().getSimpleName() + ". Se usará 1.");
        return 1;
    }



    private void createExampleEventFile() {
        File exampleFile = new File(eventsDirectory, "tutorial_mining.yml");

        if (exampleFile.exists()) {
            return;
        }

        String exampleYaml = """
                id: tutorial-mining
                displayName: "Tutorial de Minería"
                description: "Aprende a minar tus primeros recursos"
                category: "tutorial"
                difficulty: "easy"
                icon: "minecraft:stone_pickaxe"
                repeatable: false
                
                objectives:
                  - id: mine_stone
                    type: MINE_BLOCK
                    description: "Mina 10 bloques de piedra"
                    target:
                      block_id: "minecraft:stone"
                      count: 10
                
                rewards:
                  xp: 100
                  items:
                    - "minecraft:iron_pickaxe 1"
                    - "minecraft:cooked_beef 5"
                """;

        try {
            java.nio.file.Files.writeString(exampleFile.toPath(), exampleYaml);
            LOGGER.info("Created example event file: " + exampleFile.getName());
        } catch (IOException e) {
            LOGGER.severe("Failed to create example event file: " + e.getMessage());
        }
    }
}