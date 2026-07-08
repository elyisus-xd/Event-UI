package com.eventui.api.skill;

/**
 * Representa un requisito para desbloquear o mejorar un nodo.
 * (record o simple class)
 */
public record SkillRequirement(
        String nodeId,      // ID del nodo requerido
        int minLevel        // Nivel mínimo requerido en ese nodo
) {
    public String getNodeId() {
        return nodeId;
    }

    public int getMinLevel() {
        return minLevel;
    }
}
