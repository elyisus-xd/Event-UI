package com.eventui.api.bridge;

import java.util.Map;
import java.util.UUID;

/**
 * Mensaje que se intercambia entre MOD y PLUGIN.
 * - Estructura serializable (JSON o bytes)
 * - Contiene tipo + payload genérico
 * - Incluye metadata para tracking y debugging
 */
public interface BridgeMessage {

    /**
     * @return Tipo de mensaje
     */
    MessageType getType();

    /**
     * Payload del mensaje (datos específicos según el tipo)
     * El contenido depende del MessageType.
     * Ver documentación de cada tipo en MessageType enum.
     *
     * @return Mapa inmutable con los datos del mensaje
     */
    Map<String, String> getPayload();

    /**
     * @return UUID del jugador relacionado con este mensaje (puede ser null)
     */
    UUID getPlayerId();

    /**
     * @return Timestamp de creación del mensaje (epoch millis)
     */
    long getTimestamp();

    /**
     * ID único del mensaje (para rastreo de request/response)
     *
     * @return UUID del mensaje
     */
    UUID getMessageId();

    /**
     * ID del mensaje al que responde (para REQUEST/RESPONSE pattern)
     *
     * @return UUID del mensaje original, null si es un mensaje inicial
     */
    UUID getReplyToMessageId();
}
