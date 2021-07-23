/*
 * Copyright 2015 MovingBlocks
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

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.behaviors.system.NightTrackerSystem;
import org.terasology.engine.entitySystem.entity.EntityBuilder;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.event.ReceiveEvent;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.entitySystem.prefab.PrefabManager;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.behavior.core.Action;
import org.terasology.engine.logic.delay.DelayManager;
import org.terasology.engine.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.logic.players.LocalPlayer;
import org.terasology.engine.network.events.ConnectedEvent;
import org.terasology.engine.registry.In;
import org.terasology.engine.utilities.random.FastRandom;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.BlockUri;
import org.terasology.gestalt.assets.ResourceUrn;

import java.util.Collection;
import java.util.HashMap;

@RegisterSystem(RegisterMode.AUTHORITY)
public class SpawnerSystem extends BaseComponentSystem {

    public static final String ACTION_ID_PREFIX = "Spawning:";
    public static final BlockUri AIR_ID = new BlockUri(new ResourceUrn("engine:air"));

    private static final String DAY_SPAWN = "DAY";
    private static final String NIGHT_SPAWN = "NIGHT";
    private static final int MAX_HEIGHT_OFFSET = 30;


    private static final Logger logger = LoggerFactory.getLogger(SpawnerSystem.class);

    @In
    private NightTrackerSystem nightTrackerSystem;

    @In
    private WorldProvider worldProvider;

    @In
    private EntityManager entityManager;

    @In
    private PrefabManager prefabManager;

    @In
    private DelayManager delayManager;

    @In
    private LocalPlayer localPlayer;

    private final HashMap<String, Prefab> prefabMap = new HashMap<String, Prefab>();

    @Override
    public void initialise() {
        cacheTypes();
    }

    public void cacheTypes() {
        Collection<Prefab> spawnablePrefabs = prefabManager.listPrefabs(SpawnableComponent.class);
        for (Prefab prefab : spawnablePrefabs) {
            prefabMap.put(ACTION_ID_PREFIX + prefab.getUrn().toString(), prefab);
        }
    }

    /**
     * Adds periodic action for spawning once a player is connected
     */
    @ReceiveEvent
    public void onConnected(ConnectedEvent event, EntityRef entity) {
        for (String prefabKey : prefabMap.keySet()) {
            SpawnableComponent spawnableComponent = prefabMap.get(prefabKey).getComponent(SpawnableComponent.class);
            delayManager.addPeriodicAction(entity, ACTION_ID_PREFIX + prefabMap.get(prefabKey).getUrn().toString(),
                    spawnableComponent.spawnGapTime,
                    spawnableComponent.spawnGapTime);
        }
    }

    /**
     * Spawns an animal based on factors contained in {@link SpawnableComponent}
     */
    @ReceiveEvent
    public void onPeriodicActionTriggered(PeriodicActionTriggeredEvent event, EntityRef entity) {
        if (event.getActionId().startsWith(ACTION_ID_PREFIX)) {
            logger.info("In Spawning");

            EntityBuilder entityBuilder = entityManager.newBuilder(prefabMap.get(event.getActionId()));
            SpawnableComponent spawnableComponent = entityBuilder.getComponent(SpawnableComponent.class);

            // Feature 1 : Period of day
            if (spawnableComponent.period.trim().equalsIgnoreCase(DAY_SPAWN) && nightTrackerSystem.isNight()) {
                logger.info("DAY fail");
                return;
            }
            if (spawnableComponent.period.trim().equalsIgnoreCase(NIGHT_SPAWN) && (!nightTrackerSystem.isNight())) {
                logger.info("NIGHT fail");
                return;
            }

            LocationComponent locationComponent = entityBuilder.getComponent(LocationComponent.class);
            Vector3f playerPosition = localPlayer.getPosition(new Vector3f());
            float v = new FastRandom().nextFloat(0, 1);
            playerPosition.x += v * spawnableComponent.radiusFromPlayer;
            playerPosition.z += Math.sqrt(1 - Math.pow(v, 2)) * spawnableComponent.radiusFromPlayer;

            // Look for a spawn position either above or below the chosen spot.
            Vector3f spawnPosition = getGroundHeight(playerPosition);
            if (spawnPosition == null) {
                logger.info("Failed to find an open position to spawn");
                return;
            } else {
                locationComponent.setWorldPosition(spawnPosition);
                entityBuilder.saveComponent(locationComponent);
                entityBuilder.build();
                logger.info("Found a valid spawn position : ", spawnPosition);
            }

        }
    }

    /**
     * Finds ground height if within MAX_HEIGHT_OFFSET blocks of a given a starting position
     *
     * @param startPosition Position relative to which ground height is searched
     */
    private Vector3f getGroundHeight(Vector3f startPosition) {
        int offset = 1;

        if (worldProvider.getBlock(new Vector3f(startPosition.x, startPosition.y, startPosition.z)).getURI().equals(
                AIR_ID)) {
            while (offset < MAX_HEIGHT_OFFSET) {
                if (!worldProvider.getBlock(new Vector3f(startPosition.x, startPosition.y - offset, startPosition.z)).getURI().equals(
                        AIR_ID)) {
                    offset *= -1;
                    break;
                }
                offset++;
            }
        } else {
            while (offset < MAX_HEIGHT_OFFSET) {
                if (worldProvider.getBlock(new Vector3f(startPosition.x, startPosition.y + offset, startPosition.z)).getURI().equals(AIR_ID)) {
                    break;
                }
                offset++;
            }
        }
        if (offset != MAX_HEIGHT_OFFSET) {
            startPosition.y = startPosition.y + offset + 1;
            return startPosition;
        }

        return null;
    }
}
