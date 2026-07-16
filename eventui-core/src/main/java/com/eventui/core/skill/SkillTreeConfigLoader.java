package com.eventui.core.skill;

import com.eventui.api.skill.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class SkillTreeConfigLoader {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private final Yaml yaml;

    public SkillTreeConfigLoader() {
        this.yaml = new Yaml();
    }

    /**
     * Carga todos los árboles de habilidades desde el directorio skills/.
     * Si un archivo individual falla, loguea el error y continúa con los demás.
     */
    public Map<String, SkillTreeDefinition> loadAllSkillTrees(File skillsFolder) {
        Map<String, SkillTreeDefinition> trees = new HashMap<>();

        if (!skillsFolder.exists()) {
            skillsFolder.mkdirs();
            LOGGER.info("Created skills directory at: " + skillsFolder.getAbsolutePath());
            return trees;
        }

        List<File> files = findYamlFiles(skillsFolder);

        if (files.isEmpty()) {
            LOGGER.warning("No skill tree files found in: " + skillsFolder.getAbsolutePath());
            return trees;
        }

        for (File file : files) {
            try {
                SkillTreeDefinition tree = loadSkillTreeFromFile(file);
                trees.put(tree.getId(), tree);

                String relativePath = skillsFolder.toPath()
                        .relativize(file.toPath())
                        .toString();
                LOGGER.fine("✓ Loaded skill tree: " + tree.getId() + " from " + relativePath);

            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                LOGGER.severe("═══════════════════════════════");
                LOGGER.severe("YAML ERROR en archivo: " + file.getName());

                if (msg.contains("cannot be cast") && msg.contains("number")) {
                    LOGGER.severe("Problema: Un campo numérico tiene comillas.");
                    LOGGER.severe("   Incorrecto:  max_level: \"5\"");
                    LOGGER.severe("   Correcto:    max_level: 5");
                } else if (msg.contains("cannot be cast") && msg.contains("string")) {
                    LOGGER.severe("Problema: Un campo de texto necesita comillas.");
                    LOGGER.severe("   Incorrecto:  point_type: combat_points");
                    LOGGER.severe("   Correcto:    point_type: \"combat_points\"");
                } else if (msg.contains("null") || msg.contains("id")) {
                    LOGGER.severe("Problema: Falta un campo obligatorio (id, display_name, point_type, nodes).");
                } else {
                    LOGGER.severe("Problema: " + e.getMessage());
                    LOGGER.severe("  - Verifica la indentación (espacios, no tabs)");
                    LOGGER.severe("  - Usa un validador online: yamllint.com");
                }
                LOGGER.severe("Archivo: " + file.getAbsolutePath());
                LOGGER.severe("═══════════════════════════════");
            }
        }

        validateSkillTreeReferences(trees);

        if (trees.isEmpty()) {
            LOGGER.warning("No skill trees were loaded successfully. Check the errors above.");
        } else {
            String treeIds = String.join(", ", trees.keySet());
            LOGGER.info("✓ Loaded " + trees.size() + " skill tree(s): [" + treeIds + "]");
        }

        return trees;
    }

    /**
     * Carga un árbol de habilidades desde un archivo YAML.
     */
    public SkillTreeDefinition loadSkillTreeFromFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            Map<String, Object> data = yaml.load(fis);
            return parseSkillTree(data);
        }
    }

    /**
     * Parsea un árbol de habilidades desde un Map YAML.
     */
    @SuppressWarnings("unchecked")
    private SkillTreeDefinition parseSkillTree(Map<String, Object> data) {
        String id = (String) data.get("id");
        String displayName = (String) data.get("display_name");
        String description = (String) data.get("description");
        String pointType = (String) data.get("point_type");

        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing required field: id");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Missing required field: display_name");
        if (pointType == null || pointType.isBlank()) throw new IllegalArgumentException("Missing required field: point_type");

        // Parsear grupos exclusivos primero (para poder asignar IDs a nodos después)
        List<ExclusiveGroup> exclusiveGroups = new ArrayList<>();
        Map<String, Map<String, String>> nodeToBranchMap = new HashMap<>(); // nodeId -> (groupId, branchId)

        Object groupsObj = data.get("exclusive_groups");
        if (groupsObj instanceof List) {
            for (Object groupObj : (List<?>) groupsObj) {
                if (groupObj instanceof Map) {
                    Map<String, Object> groupData = (Map<String, Object>) groupObj;
                    ExclusiveGroup group = parseExclusiveGroup(groupData, nodeToBranchMap);
                    exclusiveGroups.add(group);
                }
            }
        }

        List<SkillNodeDefinition> nodes = new ArrayList<>();
        List<Map<String, Object>> nodesList = (List<Map<String, Object>>) data.get("nodes");

        if (nodesList != null) {
            for (Map<String, Object> nodeData : nodesList) {
                nodes.add(parseNode(nodeData, nodeToBranchMap));
            }
        }

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Skill tree must have at least one node");
        }

        return new SkillTreeDefinitionImpl(id, displayName, description, pointType, nodes, exclusiveGroups);
    }

    /**
     * Parsea un grupo exclusivo de ramas.
     */
    @SuppressWarnings("unchecked")
    private ExclusiveGroup parseExclusiveGroup(Map<String, Object> groupData, Map<String, Map<String, String>> nodeToBranchMap) {
        String id = (String) groupData.get("id");
        String name = (String) groupData.get("name");
        String description = (String) groupData.get("description");
        int maxSelections = parseIntSafe(groupData.get("max_selections"), 1);

        if (id == null || id.isBlank()) throw new IllegalArgumentException("ExclusiveGroup missing required field: id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("ExclusiveGroup missing required field: name");

        List<ExclusiveBranch> branches = new ArrayList<>();
        Object branchesObj = groupData.get("branches");
        if (branchesObj instanceof List) {
            for (Object branchObj : (List<?>) branchesObj) {
                if (branchObj instanceof Map) {
                    Map<String, Object> branchData = (Map<String, Object>) branchObj;
                    ExclusiveBranch branch = parseExclusiveBranch(branchData, id, nodeToBranchMap);
                    branches.add(branch);
                }
            }
        }

        return new ExclusiveGroupImpl(id, name, description, maxSelections, branches);
    }

    /**
     * Parsea una rama exclusiva.
     */
    @SuppressWarnings("unchecked")
    private ExclusiveBranch parseExclusiveBranch(Map<String, Object> branchData, String groupId, Map<String, Map<String, String>> nodeToBranchMap) {
        String id = (String) branchData.get("id");
        String name = (String) branchData.get("name");

        if (id == null || id.isBlank()) throw new IllegalArgumentException("ExclusiveBranch missing required field: id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("ExclusiveBranch missing required field: name");

        List<String> nodeIds = new ArrayList<>();
        Object nodesObj = branchData.get("nodes");
        if (nodesObj instanceof List) {
            for (Object nodeIdObj : (List<?>) nodesObj) {
                String nodeId = nodeIdObj.toString();
                nodeIds.add(nodeId);
                // Guardar mapeo para asignar al nodo después
                Map<String, String> branchInfo = new HashMap<>();
                branchInfo.put("groupId", groupId);
                branchInfo.put("branchId", id);
                nodeToBranchMap.put(nodeId, branchInfo);
            }
        }

        return new ExclusiveBranchImpl(id, name, nodeIds);
    }

    /**
     * Parsea un nodo individual.
     * Maneja cost_per_level como número fijo O como lista.
     */
    @SuppressWarnings("unchecked")
    private SkillNodeDefinition parseNode(Map<String, Object> nodeData, Map<String, Map<String, String>> nodeToBranchMap) {
        String id = (String) nodeData.get("id");
        String displayName = (String) nodeData.get("display_name");
        String description = (String) nodeData.get("description");
        String icon = (String) nodeData.get("icon");
        int maxLevel = parseIntSafe(nodeData.get("max_level"), 1);

        if (id == null || id.isBlank()) throw new IllegalArgumentException("Node missing required field: id");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Node missing required field: display_name");

        // Parsear cost_per_level: puede ser número o lista de números
        List<Integer> costs = new ArrayList<>();
        Object costObj = nodeData.get("cost_per_level");
        if (costObj instanceof Number) {
            // Número fijo: replicar para cada nivel
            int fixedCost = ((Number) costObj).intValue();
            for (int i = 0; i < maxLevel; i++) {
                costs.add(fixedCost);
            }
        } else if (costObj instanceof List) {
            // Lista de costos
            for (Object c : (List<?>) costObj) {
                if (c instanceof Number) {
                    costs.add(((Number) c).intValue());
                }
            }
        }

        // Parsear requisitos
        List<SkillRequirement> requirements = new ArrayList<>();
        Object requiresObj = nodeData.get("requires");
        if (requiresObj instanceof List) {
            for (Object reqObj : (List<?>) requiresObj) {
                if (reqObj instanceof Map) {
                    Map<String, Object> req = (Map<String, Object>) reqObj;
                    String reqNodeId = (String) req.get("node");
                    int minLevel = parseIntSafe(req.get("min_level"), 1);
                    requirements.add(new SkillRequirement(reqNodeId, minLevel));
                }
            }
        }

        String requiresMode = (String) nodeData.getOrDefault("requires_mode", "all");
        int posX = parseIntSafe(nodeData.get("x"), 0);
        int posY = parseIntSafe(nodeData.get("y"), 0);

        // Parsear position
        Object posObj = nodeData.get("position");
        if (posObj instanceof Map) {
            Map<String, Object> pos = (Map<String, Object>) posObj;
            posX = parseIntSafe(pos.get("x"), 0);
            posY = parseIntSafe(pos.get("y"), 0);
        }

        // Parsear efectos
        List<SkillEffect> effects = new ArrayList<>();
        Object effectsObj = nodeData.get("effects");
        if (effectsObj instanceof List) {
            for (Object effObj : (List<?>) effectsObj) {
                if (effObj instanceof Map) {
                    Map<String, Object> eff = (Map<String, Object>) effObj;
                    String type = (String) eff.get("type");
                    Map<String, String> effData = new HashMap<>();
                    for (Map.Entry<String, Object> entry : eff.entrySet()) {
                        if (!entry.getKey().equals("type")) {
                            effData.put(entry.getKey(), entry.getValue().toString());
                        }
                    }
                    effects.add(new SkillEffectImpl(type, effData));
                }
            }
        }

        // Parsear texture overrides
        Map<String, String> textures = new HashMap<>();
        Object texObj = nodeData.get("texture_override");
        if (texObj instanceof Map) {
            Map<String, Object> tex = (Map<String, Object>) texObj;
            for (Map.Entry<String, Object> entry : tex.entrySet()) {
                textures.put(entry.getKey(), entry.getValue().toString());
            }
        }

        // Asignar grupo y rama exclusiva si el nodo está en el mapa
        String exclusiveGroupId = null;
        String exclusiveBranchId = null;
        Map<String, String> branchInfo = nodeToBranchMap.get(id);
        if (branchInfo != null) {
            exclusiveGroupId = branchInfo.get("groupId");
            exclusiveBranchId = branchInfo.get("branchId");
        }

        return new SkillNodeDefinitionImpl(
                id, displayName, description, icon, maxLevel, costs, requirements, requiresMode,
                posX, posY, effects, textures, exclusiveGroupId, exclusiveBranchId
        );
    }

    /**
     * Valida referencias entre nodos (ciclos, requisitos rotos, etc.)
     */
    private void validateSkillTreeReferences(Map<String, SkillTreeDefinition> trees) {
        boolean foundIssues = false;

        for (SkillTreeDefinition tree : trees.values()) {
            // Crear mapa de nodos por ID para validación rápida
            Map<String, SkillNodeDefinition> nodesById = new HashMap<>();
            for (SkillNodeDefinition node : tree.getNodes()) {
                nodesById.put(node.getId(), node);
            }

            // 1. Validar referencias de requisitos (intra-árbol)
            for (SkillNodeDefinition node : tree.getNodes()) {
                for (SkillRequirement req : node.getRequirements()) {
                    if (!nodesById.containsKey(req.getNodeId())) {
                        if (!foundIssues) {
                            LOGGER.warning("═══════════════════════════════════════════════");
                            LOGGER.warning("REFERENCIAS ROTAS EN ÁRBOLES DE HABILIDADES");
                            foundIssues = true;
                        }
                        LOGGER.warning("  Árbol '" + tree.getId() + "', nodo '" + node.getId() +
                                "' → requisito apunta a nodo '" + req.getNodeId() + "' (no encontrado)");
                        LOGGER.warning("    Solución: crea el nodo '" + req.getNodeId() + "' o elimina");
                        LOGGER.warning("    ese requisito del nodo '" + node.getId() + "'.");
                    }
                }
            }

            // 2. Validar ciclos de dependencia
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();
            List<String> currentPath = new ArrayList<>();

            for (String nodeId : nodesById.keySet()) {
                if (!visited.contains(nodeId)) {
                    if (hasNodeCycleDFS(nodeId, nodesById, visited, recursionStack, currentPath)) {
                        String cyclePath = String.join(" → ", currentPath);
                        throw new IllegalStateException(
                                "Dependency cycle detected in skill tree '" + tree.getId() + "': " + cyclePath + " → " + nodeId
                        );
                    }
                }
            }

            // 3. Advertencia: requires_mode=any con 0 o 1 requisito
            for (SkillNodeDefinition node : tree.getNodes()) {
                if ("any".equalsIgnoreCase(node.getRequiresMode())) {
                    if (node.getRequirements().isEmpty() || node.getRequirements().size() == 1) {
                        LOGGER.warning("Árbol '" + tree.getId() + "', nodo '" + node.getId() +
                                "': requires_mode=any no tiene sentido con " + node.getRequirements().size() +
                                " requisito(s). Trata como 'all'.");
                    }
                }
            }
        }

        if (foundIssues) {
            LOGGER.warning("═══════════════════════════════════════════════");
        }
    }

    /**
     * Detecta ciclos en requisitos usando DFS.
     */
    private boolean hasNodeCycleDFS(
            String nodeId,
            Map<String, SkillNodeDefinition> nodesById,
            Set<String> visited,
            Set<String> recursionStack,
            List<String> currentPath
    ) {
        visited.add(nodeId);
        recursionStack.add(nodeId);
        currentPath.add(nodeId);

        SkillNodeDefinition node = nodesById.get(nodeId);
        if (node == null) {
            recursionStack.remove(nodeId);
            currentPath.removeLast();
            return false;
        }

        for (SkillRequirement req : node.getRequirements()) {
            if (recursionStack.contains(req.getNodeId())) {
                currentPath.add(req.getNodeId());
                return true;
            }
            if (!visited.contains(req.getNodeId())) {
                if (hasNodeCycleDFS(req.getNodeId(), nodesById, visited, recursionStack, currentPath)) {
                    return true;
                }
            }
        }

        recursionStack.remove(nodeId);
        currentPath.removeLast();
        return false;
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

    private int parseIntSafe(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
