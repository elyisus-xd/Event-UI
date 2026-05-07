package com.eventui.api.bridge;

import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventProgress;
import com.eventui.api.ui.UIConfig;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Contrato principal de comunicación entre MOD y PLUGIN.
 * - Interface implementada por AMBOS lados (MOD y PLUGIN)
 * - Comunicación asíncrona usando CompletableFuture
 * - El PLUGIN es la fuente de verdad para datos
 * - El MOD es el cliente que consume y renderiza
 */
public interface EventBridge {

    /**
     * Envía un mensaje al otro lado del bridge
     *
     * @param message Mensaje a enviar
     * @return Future que se completa cuando el mensaje es enviado (no necesariamente procesado)
     */
    CompletableFuture<Void> sendMessage(BridgeMessage message);

    /**
     * Registra un listener para recibir mensajes de un tipo específico
     *
     * @param type Tipo de mensaje a escuchar
     * @param handler Función que procesa el mensaje
     */
    void registerMessageHandler(MessageType type, MessageHandler handler);

    /**
     * Solicita los datos de un evento específico*
     * MOD → PLUGIN
     *
     * @param eventId ID del evento
     * @return Future con la definición del evento
     */
    CompletableFuture<EventDefinition> requestEventData(String eventId);

    /**
     * Solicita el progreso de un evento para un jugador*
     * MOD → PLUGIN
     *
     * @param eventId ID del evento
     * @param playerId UUID del jugador
     * @return Future con el progreso del evento
     */
    CompletableFuture<EventProgress> requestEventProgress(String eventId, UUID playerId);

    /**
     * Solicita la configuración UI de un evento*
     * MOD → PLUGIN
     *
     * @param eventId ID del evento
     * @return Future con la configuración UI
     */
    CompletableFuture<UIConfig> requestUIConfig(String eventId);

    /**
     * Notifica que un botón fue clickeado en la UI*
     * MOD → PLUGIN
     *
     * @param buttonId ID del botón
     * @param eventId ID del evento asociado
     * @param playerId UUID del jugador
     */
    void notifyButtonClick(String buttonId, String eventId, UUID playerId);

    /**
     * @return Si el bridge está conectado y listo para enviar mensajes
     */
    boolean isConnected();

    /**
     * Handler funcional para procesar mensajes
     */
    @FunctionalInterface
    interface MessageHandler {
        /**
         * Procesa un mensaje recibido
         *
         * @param message Mensaje recibido
         */
        void handle(BridgeMessage message);
    }
}
