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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.entity.lifecycleEvents.BeforeRemoveComponent;
import org.terasology.engine.entitySystem.entity.lifecycleEvents.OnAddedComponent;
import org.terasology.engine.entitySystem.event.ReceiveEvent;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.entitySystem.prefab.PrefabManager;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.engine.logic.ai.SimpleAIComponent;
import org.terasology.engine.logic.delay.DelayManager;
import org.terasology.engine.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.monitoring.PerformanceMonitor;
import org.terasology.engine.registry.In;
import org.terasology.engine.utilities.random.FastRandom;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.BlockManager;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * System that handles spawning of stuff
 *
 * @author Rasmus 'Cervator' Praestholm <cervator@gmail.com>
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class SpawnerSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    /**
     * Name for the scheduler to use for periodic spawning
     */
    public static final String PERIODIC_SPAWNING = "PeriodicSpawning";

    private static final Logger logger = LoggerFactory.getLogger(SpawnerSystem.class);

    @In
    private EntityManager entityManager;

    @In
    private PrefabManager prefabManager;

    @In
    private BlockManager blockMan;

    @In
    private WorldProvider worldProvider;

    @In
    private DelayManager scheduler;

    private final FastRandom random = new FastRandom();

    private long tick;
    private long classLastTick;

    /**
     * Cache containing Spawnable prefabs mapped to their spawnable "tags" - each tag may reference multiple prefabs and
     * each prefab may have multiple tags
     */
    private SetMultimap<String, Prefab> typeLists = HashMultimap.create();

    @Override
    public void initialise() {
        cacheTypes();
    }

    /**
     * Looks through all loaded prefabs and determines which are spawnable, then stores them in a local SetMultimap This
     * method should be called (or adders/removers?) whenever available spawnable prefabs change, if ever
     */
    public void cacheTypes() {
        Collection<Prefab> spawnablePrefabs = prefabManager.listPrefabs(SpawnableComponent.class);
        logger.info("Grabbed all Spawnable entities - got: {}", spawnablePrefabs);
        for (Prefab prefab : spawnablePrefabs) {
            logger.info("Prepping a Spawnable prefab: {}", prefab);
            SpawnableComponent spawnableComponent = prefab.getComponent(SpawnableComponent.class);

            // Support multiple tags per prefab ("Goblin", "Spearman", "Goblin Spearman", "QuestMob123")
            for (String tag : spawnableComponent.tags) {
                logger.info("Adding tag: {} with prefab {}", tag, prefab);
                typeLists.put(tag, prefab);
            }
        }

        logger.info("Full typeLists: {}", typeLists);
    }

    @Override
    public void shutdown() {
    }

    /**
     * On entity creation or attachment of SpawnerComponent to an entity schedule events to spawn things periodically.
     * We also require the Spawner to have a Location to avoid situations like Spawner blocks in an inventory.
     *
     * @param event the OnAddedComponent event to react to.
     * @param spawner the spawner entity being created or modified.
     */
    @ReceiveEvent(components = {SpawnerComponent.class, LocationComponent.class})
    public void onNewSpawner(OnAddedComponent event, EntityRef spawner) {
        SpawnerComponent spawnerComponent = spawner.getComponent(SpawnerComponent.class);
        logger.info("In onNewSpawner with SpawnerComponent {}", spawnerComponent);

        // Schedule a periodic action for the Spawner to spawn stuff. Start after one period of time, then recur.
        scheduler.addPeriodicAction(spawner, PERIODIC_SPAWNING, spawnerComponent.period, spawnerComponent.period);
    }

    /**
     * On entity destruction or detachment of SpawnerComponent to an entity cancel the schedule for spawning
     *
     * @param event the BeforeRemoveComponent event to react to.
     * @param spawner the spawner entity being destroyed or modified.
     */
    @ReceiveEvent(components = {SpawnerComponent.class, LocationComponent.class})
    public void onRemovedSpawner(BeforeRemoveComponent event, EntityRef spawner) {
        logger.info("In onRemovedSpawner");
        logger.info("Has the right action? {}", scheduler.hasPeriodicAction(spawner, PERIODIC_SPAWNING));
        if (scheduler.hasPeriodicAction(spawner, PERIODIC_SPAWNING)) {
            scheduler.cancelPeriodicAction(spawner, PERIODIC_SPAWNING);
        }
    }

    /**
     * A Spawner has "ticked" for its duration between spawning attempts, see if anything should be spawned.
     *
     * @param event the PeriodicActionTriggeredEvent from the scheduler to react to.
     * @param spawner the spawner entity about to be processed.
     */
    @ReceiveEvent(components = {SpawnerComponent.class})
    public void onSpawn(PeriodicActionTriggeredEvent event, EntityRef spawner) {
        logger.info("Spawner {} is ticking", spawner);
    }

    /**
     * Responsible for tick update - see if we should attempt to spawn something
     *
     * @param delta time step since last update
     */
    public void update(float delta) {
        // Do a time check to see if we should even bother calculating stuff (really only needed every second or so)
        // Keep a ms counter handy, delta is in seconds
        tick += delta * 1000;

        if (tick - classLastTick < 1000) {
            return;
        }
        classLastTick = tick;

        PerformanceMonitor.startActivity("Spawn creatures");
        try {

            // Prep a list of the Spawners we know about and a total count for max mobs
            int maxMobs = 0;
            List<EntityRef> spawnerEntities = Lists.newArrayList();

            // Only care about Spawners that are also Locations (ignore one merely contained in an inventory)
            for (EntityRef spawner : entityManager.getEntitiesWith(SpawnerComponent.class, LocationComponent.class)) {
                spawnerEntities.add(spawner);
                maxMobs += spawner.getComponent(SpawnerComponent.class).maxMobsPerSpawner;
            }

            // Go through entities that are Spawners and check to see if something should spawn
            logger.info("Count of valid (also have a Location) Spawner entities: {}", spawnerEntities.size());
            for (EntityRef entity : spawnerEntities) {
                //logger.info("Found a spawner: {}", entity);
                SpawnerComponent spawnerComp = entity.getComponent(SpawnerComponent.class);

                if (spawnerComp.lastTick > tick) {
                    spawnerComp.lastTick = tick;
                }

                //logger.info("tick is " + tick + ", lastTick is " + spawnerComp.lastTick);
                if (tick - spawnerComp.lastTick < spawnerComp.period) {
                    return;
                }

                //logger.info("Going to do stuff");
                spawnerComp.lastTick = tick;

                if (spawnerComp.maxMobsPerSpawner > 0) {
                    int currentMobs = entityManager.getCountOfEntitiesWith(SimpleAIComponent.class);

                    logger.info("Mob count: {}/{}", currentMobs, maxMobs);

                    if (currentMobs >= maxMobs) {
                        logger.info("Too many mobs! Returning early");
                        return;
                    }
                }

                int spawnTypes = spawnerComp.types.size();
                if (spawnTypes == 0) {
                    logger.warn("Spawner has no types, sad - stopping this loop iteration early :-(");
                    continue;
                }

                // Spawn origin
                Vector3f originPos = entity.getComponent(LocationComponent.class).getWorldPosition(new Vector3f());

                // In case we're doing ranged spawning we might be changing the exact spot to spawn at (otherwise they're the same)
                Vector3f spawnPos = originPos;
                if (spawnerComp.rangedSpawning) {

                    // Add random range on the x and z planes, leave y (height) unchanged for now
                    spawnPos = new Vector3f(originPos.x + random.nextFloat() * spawnerComp.range, originPos.y, originPos.z + random.nextFloat() * spawnerComp.range);

                    // If a minimum distance is set make sure we're beyond it
                    if (spawnerComp.minDistance != 0) {
                        Vector3f dist = new Vector3f(spawnPos);
                        dist.sub(originPos);

                        if (spawnerComp.minDistance > dist.lengthSquared()) {
                            return;
                        }
                    }

                    // Look for an open spawn position either above or below the chosen spot.
                    int offset = 1;
                    while (offset < 30) {
                        if (worldProvider.getBlock(new Vector3f(spawnPos.x, spawnPos.y + offset, spawnPos.z)).isPenetrable()) {
                            break;
                        } else if (worldProvider.getBlock(new Vector3f(spawnPos.x, spawnPos.y - offset, spawnPos.z)).isPenetrable()) {
                            offset *= -1;
                            break;
                        }

                        offset++;
                    }

                    if (offset == 30) {
                        logger.info("Failed to find an open position to spawn at, sad");
                        return;
                    } else {
                        spawnPos = new Vector3f(spawnPos.x, spawnPos.y + offset, spawnPos.z);
                        logger.info("Found a valid spawn position that can fit the Spawnable! {}", spawnPos);
                    }
                }

                // Pick random type to spawn from the Spawner's list of types then test the cache for matching prefabs
                String chosenSpawnerType = spawnerComp.types.get(random.nextInt(spawnerComp.types.size()));
                Set randomType = typeLists.get(chosenSpawnerType);
                logger.info("Picked random type {} which returned {} prefabs", chosenSpawnerType, randomType.size());
                if (randomType.size() == 0) {
                    logger.warn("Type {} wasn't found, sad :-( Won't spawn anything this time", chosenSpawnerType);
                    return;
                }

                // Now actually pick one of the matching prefabs randomly and that's what we'll try to spawn
                int anotherRandomIndex = random.nextInt(randomType.size());
                Object[] randomPrefabs = randomType.toArray();
                Prefab chosenPrefab = (Prefab) randomPrefabs[anotherRandomIndex];
                logger.info("Picked index {} of types {} which is a {}, to spawn at {}", anotherRandomIndex, chosenSpawnerType, chosenPrefab, spawnPos);

                // Finally create the Spawnable. Assign parentage so we can tie Spawnables to their Spawner if needed
                EntityRef newSpawnableRef = entityManager.create(chosenPrefab, spawnPos);

                logger.info("Spawning a prefab with a SKELETAL mesh: {}", chosenPrefab);

                // Temp hack - make portal spawned fancy mobs bounce around like idiots too just so they do something
                SimpleAIComponent simpleAIComponent = new SimpleAIComponent();
                newSpawnableRef.addComponent(simpleAIComponent);

                SpawnableComponent newSpawnable = newSpawnableRef.getComponent(SpawnableComponent.class);
                newSpawnable.parent = entity;
            }

        } finally {
            PerformanceMonitor.endActivity();
        }
    }
}
