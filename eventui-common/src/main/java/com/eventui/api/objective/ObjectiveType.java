package com.eventui.api.objective;

/**
 * Tipos de objetivos soportados por EventUI.*
 * ARQUITECTURA:
 * - Define QUÉ tipos de objetivos existen
 * - Cada tipo tiene su propia lógica de tracking (implementada en PLUGIN)
 * - El MOD solo necesita saber el tipo para mostrar iconos apropiados
 * - Extensible: agregar nuevos tipos no rompe el sistema
 */
public enum ObjectiveType {
    /**
     * Minar bloques específicos
     * Ejemplo: "Mina 10 diamantes"
     */
    MINE_BLOCK,

    /**
     * Matar entidades específicas
     * Ejemplo: "Mata 5 zombies"
     */
    KILL_ENTITY,

    /**
     * Craftear items específicos
     * Ejemplo: "Craftea 1 espada de diamante"
     */
    CRAFT_ITEM,

    /**
     * Recolectar items en el inventario
     * Ejemplo: "Consigue 64 troncos de roble"
     */
    COLLECT_ITEM,

    /**
     * Visitar una ubicación específica
     * Ejemplo: "Ve a las coordenadas X:100, Z:200"
     */
    REACH_LOCATION,

    /**
     * Interactuar con un bloque/entidad
     * Ejemplo: "Habla con el NPC aldeano"
     */
    INTERACT,

    /**
     * Colocar bloques específicos
     * Ejemplo: "Coloca 100 antorchas"
     */
    PLACE_BLOCK,

    /**
     * Visitar una dimensión específica
     * Ejemplo: "Ve al Nether"
     */
    VISIT_DIMENSION,

    /**
     * Alcanzar un nivel de experiencia específico
     * Ejemplo: "Alcanza nivel 30"
     */
    REACH_LEVEL,
    /**
     * Visitar un bioma específico
     * Ejemplo: "Encuentra un desierto"
     */
    VISIT_BIOME,

    /**
     * Domesticar entidades específicas
     * Ejemplo: "Domestica 3 lobos"
     */
    TAME_ENTITY,

    /**
     * Reproducir entidades específicas
     * Ejemplo: "Cría 10 vacas"
     */
    BREED_ENTITY,

    /**
     * Fundir items en un horno
     * Ejemplo: "Funde 20 minerales de hierro"
     */
    SMELT_ITEM,

    /**
     * Consumir items (comer/beber)
     * Ejemplo: "Come 10 bistecs"
     */
    CONSUME_ITEM,

    /**
     * Preparar pociones en soporte de pociones
     * Ejemplo: "Prepara 3 pociones de curación"
     */
    BREW_POTION,

    /**
     * Hacer daño a entidades específicas
     * Ejemplo: "Haz 500 de daño a zombies"
     */
    DAMAGE_ENTITY,

    /**
     * Minar bloques con una herramienta específica
     * Ejemplo: "Mina 50 bloques con un pico de diamante"
     */
    BREAK_WITH_TOOL,

    /**
     * Encantar items en mesa de encantamientos
     * Ejemplo: "Encanta 5 items"
     */
    ENCHANT_ITEM,

    /**
     * Visitar una estructura generada del mundo
     * Ejemplo: "Encuentra una aldea"
     */
    VISIT_STRUCTURE,

    /**
     * Conseguir un logro (advancement) de Minecraft
     * Ejemplo: "Consigue el logro 'Adquirir equipo'"
     */
    UNLOCK_ADVANCEMENT,

    /**
     * Objetivo personalizado (lógica definida por el creador)
     * Ejemplo: "Completa el minijuego parkour"
     */
    CUSTOM
}
