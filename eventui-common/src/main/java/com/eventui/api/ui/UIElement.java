package com.eventui.api.ui;

import java.util.List;
import java.util.Map;

/**
 * Contrato que define un elemento de interfaz gráfica.
 * ARQUITECTURA:
 * - Puramente declarativo: define QUÉ mostrar, NO CÓMO renderizarlo
 * - El MOD implementa el renderizado según el tipo
 * - Se carga desde JSON (configuración externa)
 * - Soporta jerarquía (elementos dentro de elementos)
 */
public interface UIElement {

    /**
     * @return ID único del elemento (para referencias)
     */
    String getId();

    /**
     * @return Tipo de elemento UI
     */
    UIElementType getType();

    /**
     * Posición X en la pantalla (píxeles desde la esquina superior izquierda)
     */
    int getX();

    /**
     * Posición Y en la pantalla (píxeles desde la esquina superior izquierda)
     */
    int getY();

    /**
     * Ancho del elemento en píxeles
     */
    int getWidth();

    /**
     * Alto del elemento en píxeles
     */
    int getHeight();

    /**
     * Propiedades específicas según el tipo de elemento*
     * @return Mapa inmutable de propiedades
     */
    Map<String, String> getProperties();

    /**
     * Elementos hijo
     *
     * @return Lista inmutable de elementos hijos, vacía si no tiene
     */
    List<UIElement> getChildren();

    /**
     * @return Si el elemento es visible (default: true)
     */
    default boolean isVisible() {
        return true;
    }

    /**
     * Orden de renderizado (mayor = se dibuja encima)
     *
     * @return Prioridad de renderizado (default: 0)
     */
    default int getZIndex() {
        return 0;
    }
}
