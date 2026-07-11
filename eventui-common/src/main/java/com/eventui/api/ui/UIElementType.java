package com.eventui.api.ui;

/**
 * Tipos de elementos UI soportados por EventUI.
 * ARQUITECTURA:
 * - Define los componentes básicos de la interfaz
 * - Cada tipo se renderiza de forma diferente en el MOD
 * - Son bloques de construcción declarativos
 */
public enum UIElementType {
    /**
     * Imagen estática (fondo, decoración, iconos)
     */
    IMAGE,

    /**
     * Botón clickeable (texto)
     */
    BUTTON,

    /**
     * Botón clickeable con texturas
     * Properties:
     * - texture: Textura normal
     * - hover_texture: Textura al hacer hover
     * - badge_texture: Textura del badge
     * - badge_x_offset: Offset X del badge
     * - badge_y_offset: Offset Y del badge
     */
    IMAGE_BUTTON,

    /**
     * Texto renderizado
     */
    TEXT,

    /**
     * Barra de progreso visual
     */
    PROGRESS_BAR,

    /**
     * Lista scrolleable de elementos
     */
    LIST,

    /**
     * Panel contenedor
     */
    PANEL,

    ICON,

    ENTITY_RENDER, ITEM_RENDER, BLOCK_RENDER, TOOLTIP,

    /**
     * Árbol de habilidades
     */
    SKILL_TREE
}