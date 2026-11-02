# Mystic Wilderness — Items & Inventory (Prompt 2)

A comprehensive data-driven item system, pure-Kotlin 20-slot inventory logic, world item drop/pickup ECS mechanics, 5-slot bottom-center hotbar, and mobile-friendly side-by-side inventory UI for *Mystic Wilderness*.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions confirmed during Phase 1 clarification are incorporated into this specification:
> - **Landscape Modal Layout**: Side-by-side presentation — 5x4 grid on the left and full item detail card on the right with Sort, Use/Equip, Split, Drop One, and Drop All buttons.
> - **Dual Slot Interactions**: Full support for both touch drag-and-drop (with ghost icon and drag threshold) and tap-to-select with tap-to-move.
> - **Item Dropping**: Dedicated "Drop 1" and "Drop All" buttons on the detail card that spawn dropped items 1 tile in front of the player with a 1-second pickup cooldown.
> - **Simulation Pause**: While the inventory panel is open, the fixed-timestep game simulation pauses and joystick/action button touch inputs are blocked.

---

## 1. Overview & Core Concept

This phase introduces item and inventory management:
- **`assets/items.json`**: Data-driven item definitions (resources, tools, consumables, placeables) with validation on startup.
- **`InventoryLogic`**: Pure-Kotlin engine-independent core model (20 slots, slots 0–4 = hotbar). Handles stack filling, splitting, merging, swapping, sorting by category/name, and save/load serialization with listener events.
- **World Items (ECS)**: Dropped items represented as ECS entities with bobbing sprite visuals, magnet attraction within 1 tile, auto-pickup, full-inventory alerts, and a 200-entity safety cap.
- **Mobile UI**: Fixed 5-slot hotbar with selected slot highlight and stack counts. Backpack button opens the 20-slot modal with touch-friendly $\ge 48\text{ dp}$ slots.
- **Debug Tool**: "Give Starter Items" button in the debug overlay populating tools, building blocks, consumables, and overflow stacks.

---

## 2. Technical Architecture & System Interaction

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ItemRegistry (items.json)                       │
│  Validates: duplicate IDs, missing icons, maxStack >= 1                │
│  Exposes: compact numeric indices & ItemDef lookups                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                    InventoryLogic (Pure Kotlin)                        │
│  20 Slots (Array<ItemStack?>) ─ Slots 0-4: Hotbar                      │
│  Methods: add, remove, has, countOf, move, split, sort, clear          │
│  Event Bus: addListener(InventoryListener) -> onInventoryChanged()     │
└─────────┬────────────────────────────────────────────────────┬─────────┘
          │                                                    │
┌─────────▼──────────────────────────┐  ┌──────────────────────▼─────────┐
│     ECS Component & Systems        │  │     UI & Interaction Layer     │
│ - InventoryComponent (on Player)   │  │ - HotbarRenderer:              │
│ - ItemDropSystem:                  │  │   Always visible bottom-center │
│   - Magnet attraction (< 16 px)    │  │ - InventoryModal:              │
│   - Auto-pickup -> Inventory.add() │  │   5x4 Grid + Detail Card       │
│   - 1.0s drop cooldown timer       │  │ - DragDropManager:             │
│   - 200-item world cap             │  │   Ghost icon & drag threshold  │
└────────────────────────────────────┘  └────────────────────────────────┘
```

---

## 3. Data Schema (`assets/items.json`)

Each item adheres to the forward-compatible JSON schema:
```json
{
  "id": "stone_axe",
  "name": "Stone Axe",
  "description": "A crude axe fashioned from river stone and sturdy wood.",
  "category": "tool",
  "maxStack": 1,
  "baseValue": 15,
  "iconRegion": "item_stone_axe",
  "tags": ["tool", "chopping"],
  "tool": {
    "type": "axe",
    "tier": 1,
    "power": 1.5
  }
}
```

### 10 Placeholder Items
1. `wood`: Resource (maxStack 99, tag: resource)
2. `stone`: Resource (maxStack 99, tag: resource)
3. `plant_fiber`: Resource (maxStack 99, tag: resource)
4. `berries`: Consumable (maxStack 30, tag: food, effect: health +10)
5. `rabbit_meat`: Consumable (maxStack 20, tag: food, effect: health +25)
6. `stone_axe`: Tool (maxStack 1, type: axe, tier: 1)
7. `stone_pickaxe`: Tool (maxStack 1, type: pickaxe, tier: 1)
8. `campfire`: Placeable (maxStack 5, structureId: campfire)
9. `wooden_wall`: Placeable (maxStack 50, structureId: wooden_wall)
10. `bed`: Placeable (maxStack 1, structureId: bed)

---

## 4. Proposed New & Changed Files

- **`data/`**:
  - `ItemDef.kt`: Serializable data classes (`ItemDef`, `ToolData`, `ConsumableData`, `PlaceableData`, `ItemCategory`).
  - `ItemRegistry.kt`: Loads `items.json`, validates uniqueness and invariants, maps string IDs to compact numeric IDs.
  - `assets/items.json`: Definitions for the 10 placeholder items.
- **`inventory/`**:
  - `ItemStack.kt`: `(val item: ItemDef, var count: Int)`.
  - `InventoryLogic.kt`: Pure-Kotlin 20-slot inventory logic (`add`, `remove`, `has`, `move`, `split`, `sort`, `toSaveData`, `fromSaveData`).
  - `InventoryListener.kt`: Functional interface `fun onInventoryChanged(inventory: InventoryLogic)`.
- **`ecs/`**:
  - `components/InventoryComponent.kt`: Wraps `InventoryLogic` and `selectedHotbarIndex`.
  - `components/WorldItemComponents.kt`: `PickupComponent(var pickupDelay: Float, val stack: ItemStack)`.
  - `systems/ItemPickupSystem.kt`: Handles magnet physics towards player and inventory insertion.
- **`ui/`**:
  - `HotbarRenderer.kt`: Bottom-center 5-slot HUD with selection border and stack counters.
  - `InventoryModal.kt`: 5x4 side-by-side grid, item detail card, drag-and-drop ghost, and action buttons.
- **`test/`**:
  - `InventoryLogicTest.kt`: Unit tests verifying maxStack filling, remainder, swapping vs merging, splitting odd counts, category/name sorting, and save/load round-tripping with unknown item IDs.

---

## 5. System Execution & Event Dispatch Order

1. **Simulation Tick (Fixed 60 Hz)**:
   - If `InventoryModal.isOpen == true`, ECS simulation update is paused (rendering continues).
   - If unpaused:
     - `InputSystem` $\rightarrow$ `MovementSystem` $\rightarrow$ `CollisionSystem` $\rightarrow$ `FacingSystem` $\rightarrow$ `ItemPickupSystem`.
     - `ItemPickupSystem`: Decrements `pickupDelay`, checks player distance. If within magnet range ($16\text{ px}$), moves toward player. If touching player, attempts `inventory.add(stack)`. If inventory is full, triggers floating "Inventory full" alert.
2. **`InventoryChanged` Event Order**:
   - Any mutation in `InventoryLogic` immediately notifies all registered `InventoryListener` subscribers.
   - `InventoryModal` and `HotbarRenderer` mark dirty flags on event callback and rebuild slot views only on change, ensuring zero per-frame allocation.
