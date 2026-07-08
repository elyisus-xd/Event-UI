package com.eventui.core.storage;

import com.eventui.api.skill.SkillTreeDefinition;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Almacenamiento en memoria de definiciones de árboles de habilidades.
 * SOLO contiene definiciones (estructura), NO progreso del jugador.
 */
public class SkillTreeStorage {

    private static final Logger LOGGER = Logger.getLogger("EventUI SkillTreeStorage");

    private final Map<String, SkillTreeDefinition> skillTreeDefinitions;

    public SkillTreeStorage() {
        this.skillTreeDefinitions = new ConcurrentHashMap<>();
    }

    /**
     * Registra un árbol de habilidades.
     */
    public void registerSkillTree(SkillTreeDefinition definition) {
        skillTreeDefinitions.put(definition.getId(), definition);
        LOGGER.info("Registered skill tree definition: " + definition.getId());
    }

    /**
     * Registra múltiples árboles de habilidades.
     */
    public void registerSkillTrees(Map<String, SkillTreeDefinition> trees) {
        skillTreeDefinitions.putAll(trees);
        LOGGER.info("Registered " + trees.size() + " skill tree definitions");
    }

    /**
     * Obtiene la definición de un árbol por ID.
     */
    public Optional<SkillTreeDefinition> getSkillTree(String treeId) {
        return Optional.ofNullable(skillTreeDefinitions.get(treeId));
    }

    /**
     * Obtiene todos los árboles de habilidades registrados.
     */
    public Map<String, SkillTreeDefinition> getAllSkillTrees() {
        return Collections.unmodifiableMap(skillTreeDefinitions);
    }

    /**
     * Retorna el número de árboles registrados.
     */
    public int getSkillTreeCount() {
        return skillTreeDefinitions.size();
    }
}
