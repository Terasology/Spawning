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
import org.terasology.engine.entitySystem.entity.EntityBuilder;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.event.ReceiveEvent;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.entitySystem.prefab.PrefabManager;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.delay.DelayManager;
import org.terasology.engine.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.logic.players.LocalPlayer;
import org.terasology.engine.network.events.ConnectedEvent;
import org.terasology.engine.registry.In;
import org.terasology.engine.utilities.random.FastRandom;

import java.util.Collection;
import java.util.HashMap;

@RegisterSystem(RegisterMode.AUTHORITY)
public class SpawnerSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(SpawnerSystem.class);

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
            prefabMap.put("Spawning-" + prefab.getUrn().toString(), prefab);
        }
    }

    @ReceiveEvent
    public void onConnected(ConnectedEvent event, EntityRef entity) {
        for (String prefabKey : prefabMap.keySet()) {
            SpawnableComponent spawnableComponent = prefabMap.get(prefabKey).getComponent(SpawnableComponent.class);
            delayManager.addPeriodicAction(entity, "Spawning-" + prefabMap.get(prefabKey).getUrn().toString(),
                    spawnableComponent.spawnGapTime,
                    spawnableComponent.spawnGapTime);
        }
    }

    @ReceiveEvent
    public void onPeriodicActionTriggered(PeriodicActionTriggeredEvent event, EntityRef entity) {
        if (event.getActionId().startsWith("Spawning-")) { //TODO Action Id can be better
            logger.info("In Spawning");
            EntityBuilder entityBuilder = entityManager.newBuilder(prefabMap.get(event.getActionId()));
            SpawnableComponent spawnableComponent = entityBuilder.getComponent(SpawnableComponent.class);
            LocationComponent locationComponent = entityBuilder.getComponent(LocationComponent.class);
            Vector3f spawnPosition = localPlayer.getPosition(new Vector3f());
            float v = new FastRandom().nextFloat(0, 1);
            spawnPosition.x += v * spawnableComponent.radiusFromPlayer;
            spawnPosition.z += Math.sqrt(1 - Math.pow(v, 2)) * spawnableComponent.radiusFromPlayer;
            locationComponent.setWorldPosition(spawnPosition); //TODO set height
            entityBuilder.saveComponent(locationComponent);
            EntityRef enemyEntity = entityBuilder.build();
        }
    }
}
