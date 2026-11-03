# Implementation Plan - Phase 3: Resource Nodes & Gathering

Add data-driven resource nodes placed in the world, an interaction/targeting system, tool-based gathering with feedback, and respawning.

## Proposed Changes

### 1. Game Clock & Time Tracking
- **`com.realdeelstudios.mysticwilderness.core.GameClock.kt`**:
  - Tracks simulation time in seconds (`timeInSeconds: Double`), advanced only by fixed-step simulation ticks (`FIXED_DELTA_TIME = 1/60s`) so it automatically pauses when the inventory modal is open.
  - 1 game day = 720 seconds (12 real minutes).
  - Exposes `currentDay: Int` (1-based day count) and `timeOfDaySeconds: Float` (0..720s).
  - `advanceByDays(days: Int)`: advances clock by `days * 720.0` for debug testing.
  - Serialization helpers: `toSaveData()` and `fromSaveData(data)`.

### 2. Node Data & Registry
- **`app/src/main/assets/nodes.json`**:
  - Data-driven JSON schema defining 6 resource nodes:
    1. `tree`: 100 HP, `axe` tier 1, blocks movement, drops 3-5 `wood`, `stay_depleted_and_regrow` (leaves `node_tree_stump`, collision disabled), regrows in 2-3 game days (1440-2160s).
    2. `boulder`: 150 HP, `pickaxe` tier 1, blocks movement, drops 3-5 `stone`, `remove_and_respawn`, respawns in 3-4 game days (2160-2880s).
    3. `berry_bush`: 10 HP, hand damage 5, blocks movement: false, drops 2-4 `berries`, `stay_depleted_and_regrow` (leaves `node_berry_bush_depleted`), regrows in 1-2 game days (720-1440s).
    4. `branch_pile`: 10 HP, hand damage 10, blocks movement: false, drops 1-2 `wood`, `remove_and_respawn`, respawns in 1-2 game days (720-1440s).
    5. `loose_stone`: 10 HP, hand damage 10, blocks movement: false, drops 1 `stone`, `remove_and_respawn`, respawns in 1-2 game days (720-1440s).
    6. `grass_tuft`: 5 HP, hand damage 5, blocks movement: false, drops 1-2 `plant_fiber`, `remove_and_respawn`, respawns in 1 game day (720s).
- **`com.realdeelstudios.mysticwilderness.data.NodeDef.kt` & `NodeRegistry.kt`**:
  - Strongly-typed models: `NodeDef`, `NodeDropDef`, `DepletionBehavior`.
  - Validation rules on load:
    - Unique IDs (duplicate IDs trigger error naming the node).
    - Drop `itemId` exists in `ItemRegistry`.
    - `minCount <= maxCount` and `chance in 0.0..1.0`.
    - Valid sprite regions.
- **`IAtlasProvider.kt`**:
  - Procedural pixel-art placeholder textures for: `node_tree`, `node_tree_stump`, `node_boulder`, `node_berry_bush`, `node_berry_bush_depleted`, `node_branch_pile`, `node_loose_stone`, `node_grass_tuft`, plus target reticle and health bar slices.

### 3. Tool & Damage Rules (Pure Kotlin)
- **`com.realdeelstudios.mysticwilderness.gathering.GatherLogic.kt`**:
  - Zero libGDX dependencies; handles all gathering calculations cleanly:
    - Tool power and tier matching vs. `handDamage`.
    - Specific feedback messages ("Needs an axe", "Needs a better pickaxe").
    - Drop calculation with seeded `Random`.
    - Depletion logic and remainder drop handling.
- **`com.realdeelstudios.mysticwilderness.gathering.GatherEvent.kt`**:
  - Event payload (`nodeId: String`, `itemId: String`, `count: Int`, `toolType: String?`) dispatched when drops are gathered.

### 4. Spatial Index & Targeting
- **`com.realdeelstudios.mysticwilderness.world.NodeSpatialIndex.kt`**:
  - Chunk/grid-based spatial partitioning (16x16 tile chunk buckets matching map chunks).
  - Allocation-free radial queries: finds candidate nodes in 1-4 neighboring chunks without allocating collections per query.
  - Blocking tile query: provides O(1) `isTileBlocked(tileX, tileY)` to extend `GameMap.isTileSolid` seamlessly.
- **Targeting Cone**:
  - 120-degree cone (cos(theta) >= 0.5) in the player's facing direction, max distance 1.25 tiles (20 pixels).
  - Cached selection: recomputed only when the player moves or turns.

### 5. Fleks ECS Integration & Systems
- **Components (`ResourceNodeComponent.kt`)**:
  - `ResourceNodeComponent(nodeDefId, currentHealth, maxHealth, state, respawnAtGameSeconds, hitShakeTimer)`.
  - Node entities have `Position`, `SpriteComponent`, `ResourceNodeComponent`, and optional `Collider`.
- **Systems & Execution Order**:
  1. `InputSystem` - reads movement and action button state.
  2. `MovementSystem` - stores previous positions for render interpolation.
  3. `CollisionSystem` - evaluates collisions using unified `GameMap.isTileSolid` (terrain + blocking resource nodes).
  4. `FacingSystem` - updates player facing direction.
  5. `GatherSystem` - manages 0.5s swing cooldown, checks action button, applies damage via `GatherLogic`, rolls drops, updates node state, and triggers HUD alerts.
  6. `ItemPickupSystem` - dropped item magnet movement and inventory pickup.
  7. `RespawnSystem` - throttled tick (once per second) checking `GameClock`: restores depleted nodes whose respawn timer expired, checking player bounding box overlap to postpone respawn by 10s if the player would be trapped.

### 6. Map Generation & Rendering
- **`GameMap.kt`**:
  - Seeded generation placing ~150-250 nodes:
    - Forest region: dense trees + branch piles.
    - Meadow region: berry bushes + grass tufts.
    - Cavern / Rocky region: boulders + loose stones.
  - Integrates `NodeSpatialIndex.isTileBlocked` into `isTileSolid`.
- **`GameScreen.kt` & `WorldRenderer.kt`**:
  - Node depth-sorting in Pass 2 via `sortAnchor` alongside walls and player.
  - Frustum culling: renders only nodes within visible chunks.
  - Hit shake: 0.15s shake offset applied during render.
  - Health bar rendered above damaged node during hit feedback.
  - Debug "+1 Day" button in HUD to test clock advancement and respawns.

### 7. Unit Tests
- **`GatherLogicTest.kt`**:
  - Correct damage calculation: matching tool, higher tier, lower tier, no tool, wrong tool with hands allowed/disallowed.
  - Drop rolling bounds and chance verification with seeded RNG.
  - Depletion state transitions.
- **`GameClockTest.kt`**:
  - Day rollover, time of day calculations, advanceByDays, pause behavior.
- **`NodeRespawnTest.kt`**:
  - Respawn timing verification and postpone-if-overlapping player logic.
- **`NodeRegistryTest.kt`**:
  - Validation failures for duplicate IDs, invalid item references, min > max, invalid chance.

## Verification Plan
1. Run local JVM tests via `gradle :app:testDebugUnitTest`.
2. Compile applet via `compile_applet`.
3. Test in streaming emulator:
   - Bare hands gather branch piles, loose stone, grass tufts, and berries.
   - Trees/boulders show "Needs an axe" / "Needs a pickaxe".
   - Using debug starter tools chops trees to stumps and cracks boulders to stone.
   - Pressing "+1 Day" triggers respawn of stumps and bushes.
   - Player collides with trees/boulders but can walk through stumps and walk behind canopies.
