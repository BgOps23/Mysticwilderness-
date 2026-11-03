# Mystic Wilderness — Foundation Slice (Prompt 1)

A cozy 2D top-down open-world survival RPG with base building, built with native Android, Kotlin, libGDX (Android backend), LibKTX, Fleks ECS, and kotlinx.serialization.

---

## 1. Architecture Overview

```text
app/src/main/
├── assets/
│   └── tiles.json               <-- All tile metadata (id, name, textureRegion, solid, walkable)
├── java/com/example/
│   ├── MainActivity.kt          <-- Android shell: sensorLandscape, Cutout shortEdges, Immersive sticky
│   └── mysticwilderness/
│       ├── core/
│       │   ├── GameConfig.kt    <-- Constants: 16px tile, 16x16 chunk, 60Hz step, 4 tiles/s speed
│       │   ├── MysticWildernessGame.kt <-- KtxGame root managing assets & screen lifecycle
│       │   └── screens/
│       │       └── GameScreen.kt<-- Decoupled 60Hz accumulator loop, alpha interpolation, Y-sorted rendering
│       ├── data/
│       │   ├── TileDef.kt       <-- Serializable TileDef & fast numeric TileRegistry
│       │   └── IAtlasProvider.kt<-- Atlas interface & PlaceholderAtlasProvider (16x16 nearest-neighbor)
│       ├── ecs/
│       │   ├── components/      <-- Position, Velocity, Collider (~10x6 at feet), Facing, Sprite, PlayerTag
│       │   └── systems/         <-- InputSystem, MovementSystem, CollisionSystem, FacingSystem
│       ├── world/
│       │   ├── TileLayer.kt     <-- GROUND, FLOOR, WALL, OBJECT, ROOF
│       │   ├── Chunk.kt         <-- 16x16 primitive ShortArray per layer, needsRedraw, modifiedSinceSave
│       │   ├── GameMap.kt       <-- 96x96 dynamic map data, procedural generator, unified isTileSolid()
│       │   └── WorldRenderer.kt <-- 3-pass draw order, frustum culling (+1 chunk margin), sort-anchor base row
│       ├── input/
│       │   ├── InputState.kt    <-- Reusable mutable snapshot exposed via read-only IInputState
│       │   └── TouchInputProcessor.kt <-- Multi-touch, floating joystick (deadzone ~12%, max 56dp), action button
│       └── ui/
│           └── HudRenderer.kt   <-- Floating joystick, 64dp action button, toggleable debug telemetry overlay
```

---

## 2. System Execution Order

### Fixed Timestep Simulation (60 Hz / 16.66ms per tick)
Executed strictly inside the accumulator loop `while (accumulator >= 1/60f)`:
1. **`InputSystem`**: Reads the immutable `IInputState.movementVector` snapshot, applies analog magnitude up to 4 tiles/second (64 pixels/second), and sets `Velocity`.
2. **`MovementSystem`**: Stores previous coordinates `prevX = x`, `prevY = y` for render interpolation.
3. **`CollisionSystem`**:
   - Queries `GameMap.isTileSolid(tileX, tileY)` (checks all layers via `TileRegistry`).
   - Resolves **X-axis** AABB penetration first against tile edges and zeroes $v_x$ on contact.
   - Resolves **Y-axis** AABB penetration second against tile edges and zeroes $v_y$ on contact.
   - Clamps strictly inside map bounds $[0, mapWidthPx]$ and $[0, mapHeightPx]$.
   - *Result*: Smooth sliding along tree lines and walls with zero sticking.
4. **`FacingSystem`**: Updates `FacingComponent.direction` (`UP`, `DOWN`, `LEFT`, `RIGHT`) based on dominant velocity axis.

### Variable-Frame Render Loop (with Alpha Interpolation)
1. **Delta Clamping & Resume Reset**: Frame delta is clamped to $0.25\text{ s}$ (`min(delta, 0.25f)`). On app resume/show, `accumulator = 0f` to prevent time leaps.
2. **Interpolation Alpha**: Computes $\alpha = \text{accumulator} / \text{fixedDt} \in [0, 1)$.
   - Player render position: $\text{interpX} = \text{prevX} + (x - \text{prevX}) \cdot \alpha$.
3. **Camera Follow**: Camera smoothly follows $(\text{interpX} + 8, \text{interpY} + 8)$ using frame-rate independent decay ($1 - e^{-10 \cdot \Delta t}$), clamps to map edges, and snaps to integer pixels.
4. **Pass 1 — Ground & Floor**: Frustum-culled visible chunks (+1 chunk margin) draw `GROUND` and `FLOOR` layers.
5. **Pass 2 — Walls / Objects & Y-Sorted Entities**:
   - Object/Wall sort anchor is the base row ($y \cdot 16\text{ px}$).
   - Entity sort anchor is the foot baseline ($\text{interpY} + \text{collider.offsetY}$).
   - Rows are rendered back-to-front from `maxTileY` down to `minTileY`, seamlessly interleaving objects, tree canopies, and the player entity with zero GC allocation.
6. **Pass 3 — Roof Layer**: Renders `ROOF` layer covering entities beneath.
7. **Pass 4 — UI & Debug Overlay**: Switches projection to `ScreenViewport`:
   - Floating joystick (base + knob) drawn when active.
   - Action button ($\ge 64\text{ dp}$) drawn with pressed feedback.
   - Debug overlay (FPS, frame time, player tile coordinates, visible chunks vs total, entity count, JVM heap MB).

---

## 3. Integer Pixel Scaling & Resulting Tile Counts

Formula applied on screen resize:
$$\text{scale} = \max\left(1, \text{round}\left(\frac{\text{screenWidthPx}}{352}\right)\right)$$
$$\text{viewportWidth} = \frac{\text{screenWidthPx}}{\text{scale}}, \quad \text{viewportHeight} = \frac{\text{screenHeightPx}}{\text{scale}}$$

### Resulting Tile Counts across Devices:
- **Baseline Target (352 px wide)**: $\text{scale} = 1 \implies 352 / 16 = \mathbf{22.0\text{ tiles}}$ horizontally.
- **720p Landscape (1280 × 720 px)**:
  - $\text{scale} = \text{round}(1280 / 352) = \mathbf{4}$
  - Viewport: $320 \times 180\text{ px} \implies \mathbf{20.0 \times 11.25\text{ tiles}}$
- **1080p Standard (1920 × 1080 px)**:
  - $\text{scale} = \text{round}(1920 / 352) = \mathbf{5}$
  - Viewport: $384 \times 216\text{ px} \implies \mathbf{24.0 \times 13.5\text{ tiles}}$
- **Ultrawide Mobile (2400 × 1080 px, 20:9)**:
  - $\text{scale} = \text{round}(2400 / 352) = \mathbf{7}$
  - Viewport: $342.85 \times 154.28\text{ px} \implies \mathbf{21.4 \times 9.6\text{ tiles}}$

---

## 4. How to Add a New Tile Type via JSON

All tile definitions live in `app/src/main/assets/tiles.json`. To add a new tile (e.g., `"mossy_brick"`):

1. Add an entry to `tiles.json`:
```json
{
  "id": "mossy_brick",
  "name": "Mossy Stone Brick",
  "textureRegion": "tile_mossy_brick",
  "solid": true,
  "walkable": false
}
```
2. In `PlaceholderAtlasProvider.kt` (or when providing a PNG TextureAtlas), add the matching texture region name:
```kotlin
register("tile_mossy_brick", myPixmap)
```
3. Game logic never hardcodes tile IDs; query it dynamically:
```kotlin
val mossyId = TileRegistry.getId("mossy_brick")
gameMap.setTile(TileLayer.WALL, tileX, tileY, mossyId)
```

---

## 5. Items & Inventory System (Prompt 2)

### Architecture & Design
- **`assets/items.json`**: Data-driven item definitions loaded at startup and validated into `ItemRegistry`.
- **`InventoryLogic`**: Pure Kotlin model (20 slots; slots 0–4 are hotbar, slots 5–19 are backpack). Zero libGDX dependencies.
- **`InventoryChanged` Event Bus**: Operations (`add`, `remove`, `move`, `split`, `sort`) emit a single notification upon completion.
- **Dropped World Items**: Managed as Fleks ECS entities (`DroppedItemTag`, `Position`, `ItemStackComponent`, `PickupDelay`, `DroppedItemMetadata`). Includes a 1.0s drop delay, 1-tile magnet range, 200-entity safety cap (oldest despawns first), and solid-tile drop fallback to player position.
- **Partial Pickup**: If inventory space is limited, the world item stack reduces to the remainder and remains in the world without item loss or duplication.
- **Simulation Pause**: Fixed-step accumulator freezes while the modal is open, and resets to `0f` upon closing to eliminate catch-up bursts.
- **Drag-and-Drop Interaction**: Slot rearrangement strictly uses drag-and-drop. Tapping a slot purely selects it to display details in the side panel.

### `items.json` Specification
```json
[
  {
    "id": "wood",
    "name": "Wood",
    "description": "Sturdy timber chopped from wild trees. Essential crafting resource.",
    "category": "resource",
    "maxStack": 99,
    "baseValue": 2,
    "iconRegion": "item_wood",
    "tags": ["material", "wood", "flammable"]
  },
  {
    "id": "stone_axe",
    "name": "Stone Axe",
    "description": "A crude axe fashioned from river stone and wood. Chops trees effectively.",
    "category": "tool",
    "maxStack": 1,
    "baseValue": 15,
    "iconRegion": "item_stone_axe",
    "tags": ["tool", "harvest"],
    "tool": {
      "type": "axe",
      "tier": 1,
      "power": 1.2
    }
  },
  {
    "id": "berries",
    "name": "Wild Berries",
    "description": "Sweet, plump berries gathered from wilderness bushes.",
    "category": "consumable",
    "maxStack": 50,
    "baseValue": 3,
    "iconRegion": "item_berries",
    "tags": ["food", "foraged"],
    "consumable": {
      "effects": [
        { "stat": "health", "amount": 10.0 },
        { "stat": "energy", "amount": 15.0 }
      ]
    }
  },
  {
    "id": "campfire",
    "name": "Campfire",
    "description": "Provides warm light, cooking capability, and keeps predators away.",
    "category": "placeable",
    "maxStack": 5,
    "baseValue": 20,
    "iconRegion": "item_campfire",
    "tags": ["structure", "cooking", "light"],
    "placeable": {
      "structureId": "campfire_structure"
    }
  }
]
```

### How to Add a New Item
1. Open `app/src/main/assets/items.json` and add a new JSON object adhering to the schema above:
   - Required fields: `id` (unique lowercase string), `name`, `category` (`tool`, `resource`, `consumable`, `placeable`, `misc`), `maxStack` ($\ge 1$), `iconRegion`.
   - Optional fields: `description`, `baseValue`, `tags`, `tool`, `consumable`, `placeable`.
2. Register the corresponding icon texture or pixmap in `IAtlasProvider` / `PlaceholderAtlasProvider`:
   ```kotlin
   register("item_my_new_item", pixmap)
   ```
3. Look up definitions in game logic using `ItemRegistry`:
   ```kotlin
   val itemDef = ItemRegistry.get("my_new_item")
   playerInventory.add(itemDef, 10)
   ```

### `InventoryLogic` API Summary
- `add(item: ItemDef, count: Int): Int`: Fills existing matching stacks first, then empty slots. Returns remainder that did not fit.
- `remove(item: ItemDef, count: Int): Boolean`: Removes count across one or more stacks; returns false and leaves inventory untouched if insufficient items exist.
- `has(item: ItemDef, count: Int): Boolean` & `countOf(item: ItemDef): Int`: Query quantities.
- `move(from: Int, to: Int)`: Swaps if different items; merges up to `maxStack` if matching items (remainder stays in source).
- `split(slot: Int): Boolean`: Moves half the stack to the first empty slot (odd count ceiling stays, floor moves).
- `sort()`: Re-chunks and sorts **strictly backpack slots 5–19** by category ordinal, then name, then count. Hotbar slots 0–4 are untouched.
- `toSaveData()` / `fromSaveData()`: Pure data representation using stable string IDs. Skips and logs unknown items gracefully.
- `addListener(listener: InventoryListener)`: Receive callbacks when inventory contents change.

---

## 6. Phase 3: Resource Nodes & Gathering

### `nodes.json` Schema & Full Example
All resource nodes are data-driven via `app/src/main/assets/nodes.json`.

```json
[
  {
    "id": "tree",
    "name": "Pine Tree",
    "spriteRegion": "node_tree",
    "depletedSpriteRegion": "node_tree_stump",
    "maxHealth": 100,
    "requiredToolType": "axe",
    "requiredTier": 1,
    "handDamage": 0,
    "blocksMovement": true,
    "colliderSize": {
      "width": 10.0,
      "height": 8.0,
      "offsetX": 3.0,
      "offsetY": 0.0
    },
    "sortAnchor": 0.0,
    "drops": [
      {
        "itemId": "wood",
        "minCount": 3,
        "maxCount": 5,
        "chance": 1.0
      }
    ],
    "depletionBehavior": "stay_depleted_and_regrow",
    "respawnGameSeconds": {
      "min": 1440.0,
      "max": 2160.0
    },
    "tags": ["tree", "wood", "foliage"]
  }
]
```

#### Fields Reference:
- `id`: Unique string identifier (validated against duplicates).
- `name`: Display name.
- `spriteRegion`: Texture region name in the atlas for healthy state.
- `depletedSpriteRegion`: (Optional) Texture region for depleted state (e.g. tree stump or bare berry bush).
- `maxHealth`: Total hit points.
- `requiredToolType`: Required tool type (`"axe"`, `"pickaxe"`), or `null` if bare hands are allowed.
- `requiredTier`: Minimum tool tier required (default 0).
- `handDamage`: Damage dealt per swing when bare hands or wrong tools are used; 0 if hands are not allowed.
- `blocksMovement`: If `true`, the node occupies a solid tile in the world and blocks pathing. Note: `colliderSize` is an optional sub-tile specification, but tile-blocking nodes participate directly in `GameMap.isTileSolid` via `NodeSpatialIndex`.
- `sortAnchor`: Y-offset in pixels for depth-sorting in the Y-sorted render pass.
- `drops`: List of drop specifications:
  - `itemId`: String ID of item in `items.json` (validated on load).
  - `minCount` & `maxCount`: Range of items dropped (must satisfy $min \le max$).
  - `chance`: Float between 0.0 and 1.0.
- `depletionBehavior`:
  - `"stay_depleted_and_regrow"`: Transitions to depleted sprite (e.g., tree stump, bare bush) with collision removed, regrows after respawn timer.
  - `"remove_and_respawn"`: Disappears from the map with collision removed, reappears when respawn timer triggers.
- `respawnGameSeconds`: Object with `{ "min": Float, "max": Float }` in game seconds.

### How to Add a New Node
1. Open `app/src/main/assets/nodes.json` and append a new node object adhering to the schema above.
2. Ensure any referenced `itemId` exists in `items.json`.
3. Register the `spriteRegion` (and optional `depletedSpriteRegion`) in `IAtlasProvider` / `PlaceholderAtlasProvider`.
4. Access via `NodeRegistry.get("my_node_id")`.

### `GatherLogic` Rules
- Evaluates active tool held in the selected hotbar slot.
- **Damage Formula**:
  - If `requiredToolType` matches active tool AND tool tier $\ge$ `requiredTier`: $\text{damage} = 10 \times \text{tool.power}$.
  - Else if `handDamage > 0`: $\text{damage} = \text{handDamage}$.
  - Else: $\text{damage} = 0$, throttled floating feedback message shown (e.g. `"Needs an axe"` or `"Needs a better axe"`).
- **Swing Interval**: 0.5s cooldown between consecutive swings.
- **Harvest Resolution**:
  - On depletion, drops are rolled and added to inventory.
  - If inventory is full, remaining items spill onto the ground as floating world items with magnet pickup physics.
  - Dispatches `GatherEvent(nodeId, itemId, count, toolType)` on `GatherEventBus`.

### `GameClock` API Summary
- 1 game day = 720 seconds (12 real minutes).
- Simulation-driven: advances only during fixed-step ticks when the inventory modal is closed.
- `currentDay`: 1-based day counter (`(totalGameSeconds / 720).toInt() + 1`).
- `timeOfDaySeconds`: Elapsed seconds in current day (0..720s).
- `advanceByDays(days)`: Advances clock forward (accessible via debug `+1 Day` button).
- `toSaveData()` / `fromSaveData()`: Clean serialization.

---

## 7. Setup & Running Steps

1. **Prerequisites**: Android SDK with API 36, Kotlin 2.2.10, Gradle.
2. **Build**: Run `gradle assembleDebug` or use AI Studio `compile_applet`.
3. **Run**: Launch on an Android device or streaming emulator. Orientation automatically locks to landscape with immersive sticky mode and notch/cutout expansion.
4. **Debug**: Toggle the `DBG: ON/OFF` button in the top-right corner to inspect FPS, tile coordinates, chunk counts, visible nodes, entity counts, heap usage, or press `+ Give Starter Items` to populate tools/resources, or `+ 1 Day (Advance Clock)` to test day rollover and node respawning.
