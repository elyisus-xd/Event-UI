package com.eventui.api.ui;

import java.util.List;

/**
 * Contrato que define la configuración completa de una pantalla UI.*
 * - Representa una pantalla completa (ej: pantalla de evento único)
 * - Se carga desde JSON en el PLUGIN
 * - El MOD la recibe y renderiza usando su sistema de UI
 *
 */
public interface UIConfig {

    /**
     * @return ID único de esta configuración UI
     */
    String getId();

    /**
     * @return Título de la pantalla (puede no mostrarse)
     */
    String getTitle();

    /**
     * Ancho de la pantalla en píxeles
     *
     * @return Ancho (default: 176, tamaño estándar de GUI de Minecraft)
     */
    default int getScreenWidth() {
        return 176;
    }

    /**
     * Alto de la pantalla en píxeles
     *
     * @return Alto (default: 166, tamaño estándar de GUI de Minecraft)
     */
    default int getScreenHeight() {
        return 166;
    }

    /**
     * @return Lista de elementos UI raíz de esta pantalla
     */
    List<UIElement> getRootElements();

    /**
     * ID del evento asociado a esta UI
     *
     * @return ID del evento, o null si es una UI genérica
     */
    String getAssociatedEventId();

    /**
     * Configuración adicional de la pantalla*
     * Ejemplos:
     * - "background_color": "#2C2C2C"
     * - "pause_game": "false"
     * - "blur_background": "true"
     *
     * @return Mapa inmutable de configuración
     */
    java.util.Map<String, String> getScreenProperties();
}
