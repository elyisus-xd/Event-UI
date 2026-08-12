package com.eventui.api.skill;

import java.util.List;
import java.util.Map;

/**
 * Contrato que define un nodo (habilidad) dentro de un árbol de habilidades.
 */
public interface SkillNodeDefinition {

    
    String getId();

    
    String getDisplayName();

    
    String getDescription();

    
    String getIcon();

    
    int getMaxLevel();

    
    int getCostForLevel(int level);

    
    List<SkillRequirement> getRequirements();

    
    String getRequiresMode();

    
    int getPositionX();

    
    int getPositionY();

    
    List<SkillEffect> getEffects();

    /**
     * @return Mapa inmutable de texturas custom (keys: "locked", "available", "maxed")
     * Puede estar vacío si no se definen overrides en el YAML
     */
    Map<String, String> getTextureOverrides();

    /**
     * @return ID del grupo exclusivo al que pertenece este nodo (si aplica)
     * null si el nodo no está en ningún grupo exclusivo
     */
    String getExclusiveGroupId();

    /**
     * @return ID de la rama exclusiva a la que pertenece este nodo (si aplica)
     * null si el nodo no está en ningún grupo exclusivo
     */
    String getExclusiveBranchId();

    /**
     * @return Tipo de punto requerido para subir este nodo (ej: "combat_points")
     * null si usa el tipo de punto por defecto del árbol
     */
    String getPointType();
}
