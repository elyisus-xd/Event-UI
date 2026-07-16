package com.eventui.api.skill;

import java.util.List;

/**
 * Representa un grupo exclusivo de ramas en un skill tree.
 * El jugador puede seleccionar un máximo de ramas de este grupo.
 */
public interface ExclusiveGroup {

    /** @return ID único del grupo (ej: "class_choice") */
    String getId();

    /** @return Nombre visible del grupo (ej: "Combat Class") */
    String getName();

    /** @return Descripción del grupo (opcional) */
    String getDescription();

    /** @return Máximo número de ramas que el jugador puede seleccionar de este grupo */
    int getMaxSelections();

    /** @return Lista INMUTABLE de ramas disponibles en este grupo */
    List<ExclusiveBranch> getBranches();
}
