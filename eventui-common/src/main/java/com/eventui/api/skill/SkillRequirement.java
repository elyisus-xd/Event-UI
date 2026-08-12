package com.eventui.api.skill;

/**
 * Representa un requisito para desbloquear o mejorar un nodo.
 * (record o simple class)
 */
public record SkillRequirement(
        String nodeId,      
        int minLevel        
) {
    public String getNodeId() {
        return nodeId;
    }

    public int getMinLevel() {
        return minLevel;
    }
}
