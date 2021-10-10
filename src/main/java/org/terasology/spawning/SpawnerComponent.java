// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.spawning;

import com.google.common.collect.Lists;
import org.terasology.engine.world.block.ForceBlockActive;
import org.terasology.gestalt.entitysystem.component.Component;

import java.util.List;

/**
 * Component that enables an entity to be a spawner of something.
 * It forces any block it attaches to "active" so it'll maintain a BlockComponent for location purposes.
 * However other entities can be valid spawners as well, such as creatures that have a LocationComponent.
 */
@ForceBlockActive
public class SpawnerComponent implements Component<SpawnerComponent> {

    /** Types of Spawnables this Spawner can spawn */
    public List<String> types = Lists.newArrayList();

    public long lastTick;

    /** Duration in ms between spawning attempts for this Spawner */
    public int period = 5000;

    public int maxMobsPerSpawner = 16;

    public boolean rangedSpawning;

    public int range = 20;
    public int minDistance;

    @Override
    public void copyFrom(SpawnerComponent other) {
        this.types = Lists.newArrayList(other.types);
        this.lastTick = other.lastTick;
        this.period = other.period;
        this.maxMobsPerSpawner = other.maxMobsPerSpawner;
        this.rangedSpawning = other.rangedSpawning;
        this.range = other.range;
        this.minDistance = other.minDistance;
    }
}
