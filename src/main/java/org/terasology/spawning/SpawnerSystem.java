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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.BeforeRemoveComponent;
import org.terasology.entitySystem.entity.lifecycleEvents.OnAddedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.common.DisplayNameComponent;
import org.terasology.logic.delay.DelayManager;
import org.terasology.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.math.geom.Vector3f;
import org.terasology.registry.In;
import org.terasology.logic.inventory.InventoryComponent;
//import org.terasology.logic.inventory.SlotBasedInventoryManager;
import org.terasology.logic.location.LocationComponent;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.utilities.random.FastRandom;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.family.BlockFamily;

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

    /** Name for the scheduler to use for periodic spawning */
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
    // TODO: Huh, why was this null when using DelayedActionSystem? Can see @Share doing something, but what's the reasoning? Better way to warn? Findbugs?

    //@In
    //private SlotBasedInventoryManager invMan;

    private final FastRandom random = new FastRandom();

    private long tick;
    private long classLastTick;

    /**
     * Cache containing Spawnable prefabs mapped to their spawnable "tags" - each tag may reference multiple prefabs
     * and each prefab may have multiple tags
     * */
    private SetMultimap<String, Prefab> typeLists = HashMultimap.create();

    @Override
    public void initialise() {
        cacheTypes();
    }

    /**
     * Looks through all loaded prefabs and determines which are spawnable, then stores them in a local SetMultimap
     * This method should be called (or adders/removers?) whenever available spawnable prefabs change, if ever
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
     * @param event the PeriodicActionTriggeredEvent from the scheduler to react to.
     * @param spawner the spawner entity about to be processed.
     */
    @ReceiveEvent(components = {SpawnerComponent.class})
    public void onSpawn(PeriodicActionTriggeredEvent event, EntityRef spawner) {
        // TODO: Put all the fancy spawning code here. Unless there should be more distinct spawning scenarios
        // Example: RangedSpawning, IngredientSpawning, PlayerSpawning, etc
        logger.info("Spawner {} is ticking", spawner);
    }

    /**
     * Responsible for tick update - see if we should attempt to spawn something
     *
     * @param delta time step since last update
     */
    // TODO: Switch away from UpdateSubScriberSystem to the DelaySystem and handle tick "throttle" there. Then simplify
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
                    // TODO Make sure we don't spawn too much stuff. Not very robust yet and doesn't tie mobs to their spawner of origin right
                    //int maxMobs = entityManager.getCountOfEntitiesWith(SpawnerComponent.class) * spawnerComp.maxMobsPerSpawner;
                    //int currentMobs = entityManager.getCountOfEntitiesWith(SimpleAIComponent.class) + entityManager.getCountOfEntitiesWith(HierarchicalAIComponent.class);
                    int currentMobs = entityManager.getCountOfEntitiesWith(IsSpawnedComponent.class);

                    logger.info("Mob count: {}/{}", currentMobs, maxMobs);

                    // TODO Probably need something better to base this threshold on eventually
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

/*
                // Dead players that are also Spawners shouldn't spawn stuff ... if that makes sense
                TODO: Commented out until we can detect players in multiplayer better
                if (entity.hasComponent(LocalPlayerComponent.class)) {
                    LocalPlayerComponent lpc = entity.getComponent(LocalPlayerComponent.class);
                    if (lpc.isDead) {
                        continue;
                    }
                }
*/
                // Spawn origin
                Vector3f originPos = entity.getComponent(LocationComponent.class).getWorldPosition();
/*
                TODO: Commented out pending new way of iterating through players, may need a new PlayerComponent attached to player entities
                // Check for spawning that depends on a player position (like being within a certain range)
                if (spawnerComp.needsPlayer) {
                    // TODO: shouldn't use local player, need some way to find nearest player
                    LocalPlayer localPlayer = CoreRegistry.get(LocalPlayer.class);
                    if (localPlayer != null) {
                        Vector3f dist = new Vector3f(originPos);
                        dist.sub(localPlayer.getPosition());
                        double distanceToPlayer = dist.lengthSquared();
                        if (distanceToPlayer > spawnerComp.playerNeedRange) {
                            logger.info("Spawner {} too far from player {}<{}", entity.getId(), distanceToPlayer, spawnerComp.playerNeedRange);
                            continue;
                        }
                    }
                }
*/
                //TODO check for bigger creatures and creatures with special needs like biome

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
                        if (worldProvider.getBlock(new Vector3f(spawnPos.x , spawnPos.y + offset, spawnPos.z)).isPenetrable()
                                && validateSpawnPos(new Vector3f(spawnPos.x , spawnPos.y + offset, spawnPos.z), 1, 1, 1)) {
                            break;
                        } else if (worldProvider.getBlock(new Vector3f(spawnPos.x , spawnPos.y - offset, spawnPos.z)).isPenetrable()
                                && validateSpawnPos(new Vector3f(spawnPos.x , spawnPos.y - offset, spawnPos.z), 1, 1, 1)) {
                            offset *= -1;
                            break;
                        }

                        offset++;
                    }

                    if (offset == 30) {
                        logger.info("Failed to find an open position to spawn at, sad");
                        return;
                    } else {
                        spawnPos = new Vector3f(spawnPos.x , spawnPos.y + offset, spawnPos.z);
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

                // See if the chosen Spawnable has an item it must consume on spawning and if the Spawner can provide it
                // TODO: Find way for a Spawner to ignore this if it doesn't care about item consumption (even if Spawnable asks)
                String neededItem = chosenPrefab.getComponent(SpawnableComponent.class).itemToConsume;
                if (neededItem != null) {
                    logger.info("This spawnable has an item demand on spawning: {} - Does its spawner have an inventory?", neededItem);
                    if (entity.hasComponent(InventoryComponent.class)) {
                        logger.info("Yes - it has an inventory - entity: {}", entity);

                        BlockFamily neededFamily = blockMan.getBlockFamily(neededItem);
                        logger.info("Needed block family: {}", neededFamily);
                        // TODO: Improve from current evaluation of the first slot only (ideal for single-slot invs)
                        // TODO: Also needs to be updated to match recent engine changes, but not really super important ...
                        //EntityRef firstSlot = invMan.getItemInSlot(entity, 0);
                        //logger.info("First slot {}", firstSlot);

                        DisplayNameComponent displayName = null; //firstSlot.getComponent(DisplayNameComponent.class);
                        if (displayName != null) {
                            logger.info("Got its DisplayName: {}", displayName.name);
                            if (neededFamily.getDisplayName().equals(displayName.name)) {
                                logger.info("Found the item needed to spawn stuff! Decrementing by 1 then spawning");

                                //EntityRef result = invMan.removeItem(entity, firstSlot, 1);
                                //logger.info("Result from decrementing: {}", result);
                            } else {
                                logger.info("But that item didn't match what the spawn needed to consume. No spawn!");
                                continue;
                            }
                        } else {
                            continue;
                        }

                        logger.info("Successfully decremented an existing item stack - accepting item-based spawning");
                    } else {
                        logger.info("Nope - no inventory to source material from, cannot spawn that :-(");
                        continue;
                    }
                }

                // Finally create the Spawnable. Assign parentage so we can tie Spawnables to their Spawner if needed
                EntityRef newSpawnableRef = entityManager.create(chosenPrefab, spawnPos);

                logger.info("Spawning a prefab with a SKELETAL mesh: {}", chosenPrefab);
                //CharacterMovementComponent movecomp = entity.getComponent(CharacterMovementComponent.class);
                //movecomp.height = 0.31f;
                //entity.saveComponent(movecomp);

                // Temp hack - make portal spawned fancy mobs bounce around like idiots too just so they do something
                // TODO: Change around so the Spawnable defines this
                //newSpawnableRef.saveComponent(simpleAIComponent);

                IsSpawnedComponent isSpawnedComponent = new IsSpawnedComponent();
                newSpawnableRef.addComponent(isSpawnedComponent);

                SpawnableComponent newSpawnable = newSpawnableRef.getComponent(SpawnableComponent.class);
                newSpawnable.parent = entity;

                // TODO: Use some sort of parent/inheritance thing with gelcubes -> specialized gelcubes
                // TODO: Introduce proper probability-based spawning

            }

        } finally {
            PerformanceMonitor.endActivity();
        }
    }

    /**
     * Validates a position as open enough to fit a Spawnable (or something else?) of the given dimensions
     *
     * @param pos position to start from
     * @param spawnableHeight height of what we want to fit into the space
     * @param spawnableDepth depth of what we want to fit into the space
     * @param spawnableWidth width of what we want to fit into the space
     * @return true if the spawn position will fit the Spawnable
     */
    private boolean validateSpawnPos(Vector3f pos, int spawnableHeight, int spawnableDepth, int spawnableWidth) {
        // TODO: Fill in with clean code or even switch to a generic utility method.
        // TODO: Could enhance this further with more suitability like ground below, water/non-water, etc. Just pass the whole prefab in
        return true;
    }

}