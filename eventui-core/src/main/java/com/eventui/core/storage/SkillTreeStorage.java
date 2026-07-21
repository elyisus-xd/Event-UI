package com.eventui.core.storage;

import com.eventui.api.skill.SkillTreeDefinition;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SkillTreeStorage {

    private static final Logger LOGGER = Logger.getLogger("EventUI SkillTreeStorage");

    private final Map<String, SkillTreeDefinition> skillTreeDefinitions;

    public SkillTreeStorage() {
        this.skillTreeDefinitions = new ConcurrentHashMap<>();
    }

    public void registerSkillTree(SkillTreeDefinition definition) {
        skillTreeDefinitions.put(definition.getId(), definition);
    }

    public void registerSkillTrees(Map<String, SkillTreeDefinition> trees) {
        skillTreeDefinitions.putAll(trees);
    }

    public Optional<SkillTreeDefinition> getSkillTree(String treeId) {
        return Optional.ofNullable(skillTreeDefinitions.get(treeId));
    }

    public Map<String, SkillTreeDefinition> getAllSkillTrees() {
        return Collections.unmodifiableMap(skillTreeDefinitions);
    }

    public int getSkillTreeCount() {
        return skillTreeDefinitions.size();
    }
}
