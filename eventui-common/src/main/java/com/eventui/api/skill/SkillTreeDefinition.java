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

    
    String getId();

    
    String getDisplayName();

    
    String getDescription();

    
    String getPointType();

    
    List<SkillNodeDefinition> getNodes();

    
    List<ExclusiveGroup> getExclusiveGroups();
}
