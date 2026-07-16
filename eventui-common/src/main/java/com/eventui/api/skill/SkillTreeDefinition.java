package com.eventui.api.skill;

import java.util.List;

/**
 * Contrato que define QUÉ ES un árbol de habilidades.
 * - Define estructura, NO comportamiento
 * - NO contiene progreso ni estado del jugador (eso va en futuro SkillTreeProgress)
 * - Es inmutable una vez cargado del YAML
 * - El PLUGIN carga estos datos, el MOD los consume
 */
public interface SkillTreeDefinition {

    /** @return ID único del árbol (ej: "combat-tree") */
    String getId();

    /** @return Nombre visible del árbol */
    String getDisplayName();

    /** @return Descripción del árbol (texto corto) */
    String getDescription();

    /** @return Tipo de puntos usado en este árbol (ej: "combat_points", "magic_points") */
    String getPointType();

    /** @return Lista INMUTABLE de nodos que componen este árbol */
    List<SkillNodeDefinition> getNodes();

    /** @return Lista INMUTABLE de grupos exclusivos de ramas (puede estar vacía) */
    List<ExclusiveGroup> getExclusiveGroups();
}
