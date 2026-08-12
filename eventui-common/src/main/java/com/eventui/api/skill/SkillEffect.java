package com.eventui.api.skill;

import java.util.Map;

/**
 * Contrato que define un efecto asociado a un nodo.
 * Los datos específicos están en un Map genérico para permitir
 * extensión sin cambiar la interfaz.
 */
public interface SkillEffect {

    
    String getType();

    /**
     * @return Mapa genérico con datos del efecto según su tipo.
     * Ejemplos para "attribute":
     *   - "attribute": "GENERIC_MAX_HEALTH"
     *   - "operation": "add" o "multiply_base"
     *   - "value_per_level": "2.0"
     *
     * Ejemplos para "command":
     *   - "command": "mythicmobs skill add {player} berserker_passive"
     *   - "at_level": "1"
     */
    Map<String, String> getData();
}
