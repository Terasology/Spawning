/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.spawning;

import org.terasology.entitySystem.Component;

import com.google.common.collect.Lists;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.world.block.ForceBlockActive;

import java.util.List;

/**
 * Component that enables an entity to be a spawner of something.
 * It forces any block it attaches to "active" so it'll maintain a BlockComponent for location purposes.
 * However other entities can be valid spawners as well, such as creatures that have a LocationComponent.
 *
 * @author Rasmus 'Cervator' Praestholm <cervator@gmail.com>
 */
@ForceBlockActive
public class SpawnerComponent implements Component {

    /** Types of Spawnables this Spawner can spawn */
    public List<String> types = Lists.newArrayList();
    
    public long lastTick;

    /** Duration in ms between spawning attempts for this Spawner */
    public int period = 5000;

    public int maxMobsPerSpawner = 16;
    
    public boolean rangedSpawning;

    public int range = 20;
    public int minDistance;
    
    public boolean needsPlayer;

    public int playerNeedRange = 10000;
    
}
