package com.eventui.api.objective;

/**
 * Handler personalizado para objetivos CUSTOM.
 * Permite que plugins externos registren lógica de tracking personalizada.
 *
 * Nota: Los parámetros Player, Object y ObjectiveDefinition son del tipo Bukkit/EventUI,
 * pero se usan como Object aquí para que esta interfaz no dependa de Bukkit en la API.
 */
public interface CustomObjectiveHandler {
    
    /**
     * Obtiene el ID único de este handler personalizado.
     * Formato recomendado: "plugin_name:handler_id"
     * Ejemplo: "mylib:golden_apple_quest", "custom:parkour_jumps"
     */
    String getCustomId();
    
    /**
     * Se invoca cuando ocurre una acción que podría avanzar un objetivo CUSTOM.
     *
     * @param player     Jugador que realizó la acción (org.bukkit.entity.Player)
     * @param context    Contexto de la acción (puede ser cualquier objeto)
     * @param objective  Definición del objetivo siendo tracked (ObjectiveDefinition)
     * @return           true si el objetivo fue avanzado, false si no aplica
     */
    boolean onAction(Object player, Object context, ObjectiveDefinition objective);
    
    /**
     * Se invoca cuando el handler es registrado en ObjectiveTracker.
     * Usar para inicializar listeners o schedulers necesarios.
     */
    void onRegister();
    
    /**
     * Se invoca cuando el handler es deregistrado.
     * Usar para limpiar listeners, cancelar schedulers, etc.
     */
    void onUnregister();
}

