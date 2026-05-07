package com.eventui.api.event;

import com.eventui.api.objective.ObjectiveProgress;
import java.util.List;
import java.util.UUID;

/**
 * Contrato que representa el PROGRESO de un evento para un jugador específico
 * - Separado de EventDefinition (definición vs estado)
 * - El PLUGIN gestiona esta información
 * - El MOD la recibe para mostrar en UI
 * - Es mutable (el progreso cambia)
 */
public interface EventProgress {

    /**
     * @return UUID del jugador dueño de este progreso
     */
    UUID getPlayerId();

    /**
     * @return ID del evento al que pertenece este progreso
     */
    String getEventId();

    /**
     * @return Estado actual del evento para este jugador
     */
    EventState getState();

    /**
     * @return Progreso de cada objetivo individual
     */
    List<ObjectiveProgress> getObjectivesProgress();

    /**
     * Calcula el progreso total del evento (0.0 a 1.0)
     *
     * @return Porcentaje de completado (0.0 = 0%, 1.0 = 100%)
     */
    float getOverallProgress();

    /**
     * @return Timestamp de cuando se inició el evento (epoch millis), 0 si no iniciado
     */
    long getStartedAt();

    /**
     * @return Timestamp de cuando se completó (epoch millis), 0 si no completado
     */
    long getCompletedAt();

    /**
     * Falla el evento (solo si está IN_PROGRESS).
     */
    void fail();
}
