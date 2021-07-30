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

import org.terasology.engine.entitySystem.Component;

/**
 * This component can be attached to animal prefabs to allow them to be spawned. It also stores factors affecting the spawn.
 */
public class SpawnableComponent implements Component {
    public int radiusFromPlayer = 0;
    public int spawnGapTime = 30 * 1000; // 30s
    public PERIOD period = PERIOD.Any; // Either "ANY" or "DAY" or "NIGHT"
    public static enum PERIOD {
        Any,
        Day,
        Night
    }
}
