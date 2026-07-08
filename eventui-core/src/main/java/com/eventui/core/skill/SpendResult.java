package com.eventui.core.skill;

/**
 * Resultado de intentar gastar puntos para subir de nivel un nodo.
 */
public enum SpendResult {
    SUCCESS("Node upgraded successfully"),
    TREE_NOT_FOUND("Skill tree not found"),
    NODE_NOT_FOUND("Node not found in tree"),
    ALREADY_MAXED("Node is already at max level"),
    INSUFFICIENT_POINTS("Not enough points to upgrade"),
    REQUIREMENTS_NOT_MET("Requirements not met for this node");

    private final String message;

    SpendResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
