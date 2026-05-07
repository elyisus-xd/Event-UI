package com.eventui.api.bridge;

/**
 * Tipos de mensajes que pueden intercambiarse entre MOD y PLUGIN
 * - Define el protocolo de comunicación
 * - MOD → PLUGIN: solicitudes de datos, notificaciones de acciones UI
 * - PLUGIN → MOD: actualizaciones de progreso, datos de eventos
 */
public enum MessageType {
    // ========== MOD → PLUGIN ==========

    /**
     * Solicitar datos de un evento específico
     * Payload: {"event_id": "..."}
     */
    REQUEST_EVENT_DATA,

    /**
     * Solicitar progreso de un evento para el jugador
     * Payload: {"event_id": "...", "player_uuid": "..."}
     */
    REQUEST_EVENT_PROGRESS,

    /**
     * Solicitar configuración UI de un evento
     * Payload: {"event_id": "..."}
     */
    REQUEST_UI_CONFIG,

    /**
     * Notificar que el jugador hizo click en un botón
     * Payload: {"button_id": "...", "event_id": "..."}
     */
    UI_BUTTON_CLICKED,

    /**
     * Notificar que el jugador abrió la pantalla de evento
     * Payload: {"event_id": "...", "player_uuid": "..."}
     */
    UI_SCREEN_OPENED,

    /**
     * Notificar que el jugador cerró la pantalla
     * Payload: {"event_id": "..."}
     */
    UI_SCREEN_CLOSED,

    // ========== PLUGIN → MOD ==========

    /**
     * Respuesta con datos de un evento
     * Payload: serialización de EventDefinition
     */
    EVENT_DATA_RESPONSE,

    /**
     * Respuesta con progreso de evento
     * Payload: serialización de EventProgress
     */
    EVENT_PROGRESS_RESPONSE,

    /**
     * Respuesta con configuración UI
     * Payload: serialización de UIConfig
     */
    UI_CONFIG_RESPONSE,

    /**
     * Actualización de progreso (push del PLUGIN)
     * Payload: {"event_id": "...", "player_uuid": "...", "objective_id": "...", "current": "5", "target": "10"}
     */
    PROGRESS_UPDATE,

    /**
     * Notificación de cambio de estado de evento
     * Payload: {"event_id": "...", "player_uuid": "...", "new_state": "COMPLETED"}
     */
    EVENT_STATE_CHANGED,


    EVENT_RELOAD_NOTIFICATION,


    /**
     * MOD → PLUGIN: Solicitar modo de UI configurado en el servidor.
     * Payload: (vacío)
     * El servidor responde con UI_MODE_RESPONSE indicando si debe usar
     * la UI hardcoded (EventScreen) o una custom (CustomEventScreen + screenId).
     */
    REQUEST_UI_MODE,

    /**
     * PLUGIN → MOD: Respuesta con modo de UI y configuración.
     * Payload: {
     *   mode: "hardcoded" | "custom",
     *   screenId?: "demain"  // Solo si mode = custom
     * }
     * El cliente usa este payload para decidir qué pantalla abrir.
     */
    UI_MODE_RESPONSE,

    /**
     * MOD → PLUGIN: Solicitar UIConfig completo con data bindings calculados.
     * Payload: { screen_id: "dedsafio-main" }
     * Usado por CustomEventScreen para obtener la estructura YAML + variables dinámicas.
     */
    REQUEST_UI_CONFIG_WITH_DATA,

    /**
     * PLUGIN → MOD: Respuesta con UIConfig serializado + data bindings.
     * Payload: {
     *   ui_config: "<json>",  // UIConfig serializado
     *   data_bindings: "<json>"  // {"eventCount:exploration": "5", ...}
     * }
     * El cliente deserializa y construye la UI dinámicamente.
     */
    UI_CONFIG_DATA_RESPONSE,

    /**
     * PLUGIN → MOD: Actualiza variables de estado de UI del jugador.
     * Payload: { "variables": "{\"mob_guardian_unlocked\":\"true\"}" }
     * El cliente actualiza su caché local y re-evalúa visible_if en pantallas activas.
     */
    UI_STATE_UPDATE,

    /**
     * MOD → PLUGIN: Solicita todas las variables de estado actuales.
     * Payload: vacío
     * Respuesta: UI_STATE_UPDATE con todas las variables del jugador.
     */
    REQUEST_UI_STATE,

    /**
     * Error en el procesamiento de un mensaje
     * Payload: {"error_code": "...", "message": "..."}
     */
    ERROR
}
