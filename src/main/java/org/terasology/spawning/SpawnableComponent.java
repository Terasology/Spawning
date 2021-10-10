// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.spawning;

import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.gestalt.entitysystem.component.Component;

import java.util.Collections;
import java.util.Set;

/**
 * Component that enables an entity to be spawned by something.
 */
public class SpawnableComponent implements Component<SpawnableComponent> {

    /** What category is this spawnable. TODO: Change to a set of String "tags" instead ("goblin", "spearman" ... ) */
    public String type = "undefined";
    public Set<String> tags = Collections.emptySet();

    /** Weight for how common the spawnable is, from 0-255 with 0 meaning unspawnable and 255 being the most common */
    public byte probability = 1;

    /** Optional: If spawner is attached to an inventory and this is non-null require that item present and decrement */
    public String itemToConsume;

    /** What made this Spawnable? */
    public EntityRef parent =  EntityRef.NULL;

    @Override
    public void copyFrom(SpawnableComponent other) {
        this.type = other.type;
        this.probability = other.probability;
        this.itemToConsume = other.itemToConsume;
        this.parent = other.parent;

        this.tags.clear();
        this.tags.addAll(other.tags);

    }

    //TODO add darkness level and biome when map generation has reached better level
}
