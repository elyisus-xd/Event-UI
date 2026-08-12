package com.eventui.api.skill;

import java.util.List;

/**
 * Representa una rama dentro de un grupo exclusivo.
 * Una rama contiene una lista de nodos que pertenecen a ella.
 */
public interface ExclusiveBranch {

    
    String getId();

    
    String getName();

    
    List<String> getNodeIds();
}
