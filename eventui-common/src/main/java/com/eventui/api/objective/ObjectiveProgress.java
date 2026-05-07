package com.eventui.api.objective;

/**
 * Contrato que representa el PROGRESO de un objetivo para un jugador.*
 * ARQUITECTURA:
 * - Separado de ObjectiveDefinition (definición vs estado)
 * - El PLUGIN actualiza estos valores según eventos del juego
 * - El MOD los consume para mostrar barras de progreso
 */
public interface ObjectiveProgress {

    /**
     * @return ID del objetivo al que pertenece este progreso
     */
    String getObjectiveId();

    /**
     * @return Cantidad actual conseguida por el jugador
     */
    int getCurrentAmount();

    /**
     * @return Cantidad objetivo (copiado de ObjectiveDefinition para conveniencia)
     */
    int getTargetAmount();

    /**
     * @return Si el objetivo está completado
     */
    boolean isCompleted();

    /**
     * Calcula el progreso como porcentaje (0.0 a 1.0)
     *
     * @return Porcentaje de completado (0.0 = 0%, 1.0 = 100%)
     */
    default float getProgressPercentage() {
        if (getTargetAmount() <= 0) return 0.0f;
        return Math.min(1.0f, (float) getCurrentAmount() / getTargetAmount());
    }
}
