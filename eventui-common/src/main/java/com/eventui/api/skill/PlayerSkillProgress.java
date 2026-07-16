package com.eventui.api.skill;

import java.util.UUID;

/**
 * Contrato del progreso de un jugador en árboles de habilidades.
 * Almacena qué nivel tiene en cada nodo de cada árbol, y cuántos
 * puntos ha ganado/disponibles de cada tipo.
 */
public interface PlayerSkillProgress {

    /** @return UUID del jugador */
    UUID getPlayerId();

    /**
     * @return Nivel actual en un nodo específico (0 si nunca se subió)
     */
    int getNodeLevel(String treeId, String nodeId);

    /**
     * Establece el nivel de un nodo.
     */
    void setNodeLevel(String treeId, String nodeId, int level);

    /**
     * @return Puntos disponibles de un tipo (sin gastar)
     */
    int getAvailablePoints(String pointType);

    /**
     * @return Total de puntos ganados de un tipo (histórico, nunca baja)
     */
    int getTotalEarnedPoints(String pointType);

    /**
     * Establece los puntos disponibles.
     */
    void setAvailablePoints(String pointType, int amount);

    /**
     * Establece directamente el total historico ganado, sin afectar los puntos
     * disponibles. Uso exclusivo para restaurar datos guardados; para otorgar
     * puntos en tiempo real usar addEarnedPoints.
     */
    void setTotalEarnedPoints(String pointType, int amount);

    /**
     * Suma puntos ganados. Incrementa AMBOS available y total_earned.
     */
    void addEarnedPoints(String pointType, int amount);

    /**
     * Resetea a 0 todos los nodos registrados para un arbol.
     */
    void resetTreeProgress(String treeId);

    /**
     * @return ID de la rama seleccionada para un grupo exclusivo (null si no seleccionó)
     */
    String getSelectedBranch(String treeId, String groupId);

    /**
     * Establece la rama seleccionada para un grupo exclusivo.
     */
    void setSelectedBranch(String treeId, String groupId, String branchId);

    /**
     * @return Mapa de grupos exclusivos a ramas seleccionadas para un árbol
     */
    java.util.Map<String, String> getSelectedBranches(String treeId);
}
