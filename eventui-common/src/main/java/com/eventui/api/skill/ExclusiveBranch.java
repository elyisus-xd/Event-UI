package com.eventui.api.skill;

import java.util.List;

/**
 * Representa una rama dentro de un grupo exclusivo.
 * Una rama contiene una lista de nodos que pertenecen a ella.
 */
public interface ExclusiveBranch {

    /** @return ID único de la rama dentro del grupo (ej: "warrior") */
    String getId();

    /** @return Nombre visible de la rama (ej: "Warrior") */
    String getName();

    /** @return Lista INMUTABLE de IDs de nodos que pertenecen a esta rama */
    List<String> getNodeIds();
}
