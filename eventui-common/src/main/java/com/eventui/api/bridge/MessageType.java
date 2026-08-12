package com.eventui.api.bridge;

/**
 * Tipos de mensajes que pueden intercambiarse entre MOD y PLUGIN
 * - Define el protocolo de comunicaciÃƒÂ³n
 * - MOD Ã¢â€ â€™ PLUGIN: solicitudes de datos, notificaciones de acciones UI
 * - PLUGIN Ã¢â€ â€™ MOD: actualizaciones de progreso, datos de eventos
 */
public enum MessageType {
    

    /**
     * Solicitar datos de un evento especÃƒÂ­fico
     * Payload: {"event_id": "..."}
     */
    REQUEST_EVENT_DATA,

    /**
     * Solicitar progreso de un evento para el jugador
     * Payload: {"event_id": "...", "player_uuid": "..."}
     */
    REQUEST_EVENT_PROGRESS,

    /**
     * Solicitar configuraciÃƒÂ³n UI de un evento
     * Payload: {"event_id": "..."}
     */
    REQUEST_UI_CONFIG,

    /**
     * Notificar que el jugador hizo click en un botÃƒÂ³n
     * Payload: {"button_id": "...", "event_id": "..."}
     */
    UI_BUTTON_CLICKED,

    /**
     * MOD Ã¢â€ â€™ PLUGIN: Notificar que el jugador descartÃƒÂ³ un badge
     * Payload: {"screen_id":"...","element_id":"..."}
     */
    BADGE_DISMISS,

    /**
     * Notificar que el jugador abriÃƒÂ³ la pantalla de evento
     * Payload: {"event_id": "...", "player_uuid": "..."}
     */
    UI_SCREEN_OPENED,

    /**
     * Notificar que el jugador cerrÃƒÂ³ la pantalla
     * Payload: {"event_id": "..."}
     */
    UI_SCREEN_CLOSED,

    

    /**
     * Respuesta con datos de un evento
     * Payload: serializaciÃƒÂ³n de EventDefinition
     */
    EVENT_DATA_RESPONSE,

    /**
     * Respuesta con progreso de evento
     * Payload: serializaciÃƒÂ³n de EventProgress
     */
    EVENT_PROGRESS_RESPONSE,

    /**
     * Respuesta con configuraciÃƒÂ³n UI
     * Payload: serializaciÃƒÂ³n de UIConfig
     */
    UI_CONFIG_RESPONSE,

    /**
     * ActualizaciÃƒÂ³n de progreso (push del PLUGIN)
     * Payload: {"event_id": "...", "player_uuid": "...", "objective_id": "...", "current": "5", "target": "10"}
     */
    PROGRESS_UPDATE,

    /**
     * NotificaciÃƒÂ³n de cambio de estado de evento
     * Payload: {"event_id": "...", "player_uuid": "...", "new_state": "COMPLETED"}
     */
    EVENT_STATE_CHANGED,


    EVENT_RELOAD_NOTIFICATION,


    /**
     * MOD Ã¢â€ â€™ PLUGIN: Solicitar modo de UI configurado en el servidor.
     * Payload: (vacÃƒÂ­o)
     * El servidor responde con UI_MODE_RESPONSE indicando si debe usar
     * la UI hardcoded (EventScreen) o una custom (CustomEventScreen + screenId).
     */
    REQUEST_UI_MODE,

    /**
     * PLUGIN Ã¢â€ â€™ MOD: Respuesta con modo de UI y configuraciÃƒÂ³n.
     * Payload: {
     *   mode: "hardcoded" | "custom",
     *   screenId?: "demain"  
     * }
     * El cliente usa este payload para decidir quÃƒÂ© pantalla abrir.
     */
    UI_MODE_RESPONSE,

    /**
     * MOD Ã¢â€ â€™ PLUGIN: Solicitar UIConfig completo con data bindings calculados.
     * Payload: { screen_id: "dedsafio-main" }
     * Usado por CustomEventScreen para obtener la estructura YAML + variables dinÃƒÂ¡micas.
     */
    REQUEST_UI_CONFIG_WITH_DATA,

    /**
     * PLUGIN Ã¢â€ â€™ MOD: Respuesta con UIConfig serializado + data bindings.
     * Payload: {
     *   ui_config: "<json>",  
     *   data_bindings: "<json>"  
     * }
     * El cliente deserializa y construye la UI dinÃƒÂ¡micamente.
     */
    UI_CONFIG_DATA_RESPONSE,

    /**
     * PLUGIN Ã¢â€ â€™ MOD: Actualiza variables de estado de UI del jugador.
     * Payload: { "variables": "{\"mob_guardian_unlocked\":\"true\"}" }
     * El cliente actualiza su cachÃƒÂ© local y re-evalÃƒÂºa visible_if en pantallas activas.
     */
    UI_STATE_UPDATE,

    /**
     * PLUGIN Ã¢â€ â€™ MOD: Enviar lista de badges descartados al cliente
     * Payload: {"badges":"[\"s1:btn1\",\"s2:btn2\"]"}
     */
    BADGES_UPDATE,

    /**
     * MOD Ã¢â€ â€™ PLUGIN: Solicita todas las variables de estado actuales.
     * Payload: vacÃƒÂ­o
     * Respuesta: UI_STATE_UPDATE con todas las variables del jugador.
     */
    REQUEST_UI_STATE,

    /**
     * PLUGIN Ã¢â€ â€™ MOD: Ordena al cliente abrir una pantalla custom especÃƒÂ­fica.
     * Enviado por ejemplo desde /ev open <screen_id>.
     * Payload: { "screen_id": "main-menu" }
     */
    OPEN_UI_COMMAND,

    /**
     * MOD → PLUGIN: Solicitar datos de árboles de habilidades del jugador.
     * Payload: (vacío)
     * El servidor responde con SKILL_DATA_RESPONSE.
     */
    REQUEST_SKILL_DATA,

    /**
     * PLUGIN → MOD: Respuesta con datos de árboles de habilidades y progreso.
     * Payload JSON con estructura completa de skills, niveles de nodos y puntos disponibles.
     */
    SKILL_DATA_RESPONSE,

    /**
     * MOD → PLUGIN: Solicitar gastar puntos en un nodo de habilidad.
     * Payload: { "tree_id": "...", "node_id": "..." }
     * El servidor responde con SKILL_NODE_UPDATE o SKILL_SPEND_ERROR.
     */
    REQUEST_SKILL_SPEND,

    /**
     * PLUGIN → MOD: Notificación de que un nodo fue actualizado (nivel cambió).
     * Payload: { "tree_id": "...", "node_id": "...", "new_level": "...", "new_state": "...", ... }
     */
    SKILL_NODE_UPDATE,

    /**
     * PLUGIN → MOD: Error al intentar gastar puntos.
     * Payload: { "tree_id": "...", "node_id": "...", "error": "INSUFFICIENT_POINTS", "cost": "2", "available": "1" }
     */
    SKILL_SPEND_ERROR,

    /**
     * Error en el procesamiento de un mensaje
     * Payload: {"error_code": "...", "message": "..."}
     */
    ERROR,

    /**
     * PLUGIN → MOD: Notificación de objetivo completado para el HUD
     * Payload: {"objective_name": "...", "points": "..."}
     */
    OBJECTIVE_COMPLETED_NOTIFICATION,

    /**
     * PLUGIN → MOD: Notificación de progreso de quest para el HUD
     * Payload: {"quest_name": "...", "objective": "...", "progress": "..."}
     */
    QUEST_PROGRESS_NOTIFICATION,

    /**
     * PLUGIN → MOD: Notificación de evento iniciado para el HUD
     * Payload: {"quest_name": "..."}
     */
    EVENT_STARTED_NOTIFICATION,

    /**
     * PLUGIN → MOD: Notificación de evento completado para el HUD
     * Payload: {"quest_name": "..."}
     */
    EVENT_COMPLETED_NOTIFICATION,

    /**
     * PLUGIN → MOD: Notificación de evento fallado para el HUD
     * Payload: {"quest_name": "..."}
     */
    EVENT_FAILED_NOTIFICATION,

    /**
     * PLUGIN → MOD: Notificación de evento bloqueado para el HUD
     * Payload: {}
     */
    EVENT_LOCKED_NOTIFICATION
}
