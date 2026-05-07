package com.eventui.api.event;

/**
 * Estados posibles de un evento en EventUI
     Este enum es parte del contrato COMÚN entre MOD y PLUGIN.
 * - NO contiene lógica de transición de estados
 * - NO contiene validaciones de negocio
 * - Es puramente declarativo
 */
public enum EventState {
    /**
     * El evento está disponible pero no ha sido iniciado por el jugador
     */
    AVAILABLE,

    /**
     * El evento está actualmente en progreso
     */
    IN_PROGRESS,

    /**
     * El evento ha sido completado exitosamente
     */
    COMPLETED,

    /**
     * El evento ha fallado o expirado
     */
    FAILED,

    /**
     * El evento está bloqueado (requisitos no cumplidos)
     */
    LOCKED
}
