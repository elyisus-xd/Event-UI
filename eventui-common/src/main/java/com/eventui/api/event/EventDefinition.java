package com.eventui.api.event;

import com.eventui.api.objective.ObjectiveDefinition;
import java.util.List;
import java.util.Map;

/**
 * Contrato que define QUÉ ES un evento.
 * - Define estructura, NO comportamiento
 * - NO contiene progreso ni estado (eso va en EventProgress)
 * - Es inmutable una vez cargado del YML
 * - El PLUGIN carga estos datos, el MOD los consume
 */
public interface EventDefinition {

    
    String getId();

    
    String getDisplayName();

    
    String getDescription();

    
    List<ObjectiveDefinition> getObjectives();

    /**
     * Recursos UI asociados a este evento (paths de imágenes, etc.)
     * Ejemplos de keys:
     * - "icon": "textures/events/mining_icon.png"
     * - "background": "textures/events/mining_bg.png"
     * - "banner": "textures/events/mining_banner.png"
     *
     * @return Mapa inmutable de recursos
     */
    Map<String, String> getUIResources();

    /**
     * Metadata adicional configurable desde YML.
     * Ejemplos:
     * - "category": "tutorial"
     * - "repeatable": "true"
     *
     * @return Mapa inmutable de propiedades custom
     */
    Map<String, String> getMetadata();

    
    List<String> getDependencies();

    /**
     * Indica si este evento se auto-inicia al detectar progreso relevante,
     * sin necesidad de iniciarlo manualmente.
     * Sobreescribe el valor global de events.always_active en config.yml.
     * Si es null, se usa el valor global.
     * Configurable en el YML del evento:
     * <pre>
     *   always_active: true   # este evento siempre se auto-inicia
     *   always_active: false  # este evento requiere inicio manual
     * </pre>
     *
     * @return true/false si está definido en el evento, null si usa el global
     */
    Boolean isAlwaysActive();
}
