package com.eventui.api.skill;

import java.util.List;

/**
 * Representa un grupo exclusivo de ramas en un skill tree.
 * El jugador puede seleccionar un máximo de ramas de este grupo.
 */
public interface ExclusiveGroup {

    
    String getId();

    
    String getName();

    
    String getDescription();

    
    int getMaxSelections();

    
    List<ExclusiveBranch> getBranches();
}
