package com.eventui.api.skill;

import java.util.List;
import java.util.Map;

/**
 * Contrato que define un nodo (habilidad) dentro de un árbol de habilidades.
 */
public interface SkillNodeDefinition {

    /** @return ID único del nodo dentro del árbol (ej: "vitality_1") */
    String getId();

    /** @return Nombre visible del nodo */
    String getDisplayName();

    /** @return Descripción del nodo (texto corto) */
    String getDescription();

    /** @return Icono del nodo en formato ResourceLocation (ej: "minecraft:apple") */
    String getIcon();

    /** @return Número máximo de niveles que puede alcanzar este nodo */
    int getMaxLevel();

    /** @return Costo para subir AL nivel indicado (1-indexed, ej: getCostForLevel(1) = costo para llegar a nivel 1) */
    int getCostForLevel(int level);

    /** @return Lista INMUTABLE de requisitos que este nodo tiene */
    List<SkillRequirement> getRequirements();

    /** @return Modo de requisitos: "all" (todos deben cumplirse) o "any" (al menos uno) */
    String getRequiresMode();

    /** @return Posición X en el árbol (para UI) */
    int getPositionX();

    /** @return Posición Y en el árbol (para UI) */
    int getPositionY();

    /** @return Lista INMUTABLE de efectos que se aplican al subir este nodo */
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
}
