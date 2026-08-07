# AreaAutoMiner

This client-side Minecraft Fabric mod allows you to automatically break all blocks inside a manually selected cuboid region.
It follows the vanilla Minecraft movement rules strictly: your player will walk to a valid reachable position, adjust their view angle to face the target block, and break it, with all operations fully compliant with the game's original reach limit and mining speed.
This mod is designed exclusively for use in single-player worlds and your own private multiplayer servers.
It is not recommended to use it on public or third-party servers. Before attempting any use outside of your own worlds, you must obtain explicit written permission from the server administrator in advance. We do not encourage any behavior that violates server rules, and any potential account ban or anti-cheat penalties caused by improper use are the full responsibility of the user.


[中文文档](README_CN.md)

## Features

- **In-game area selection** — hold any sword, right-click a block to set point 1, sneak + right-click to set point 2.
- **Visual region preview** — a green outline box renders the selected cuboid (visible up to 256 blocks away).
- **Two mining modes** — `FROM_TOP_DOWN` (top → bottom) and `FROM_BOTTOM_UP` (bottom → top).
- **Human-like behavior** — smooth view rotation with sinusoidal jitter, natural swing animations, simulated key presses, and a strict 4-block reach limit.
- **Smart movement** — vanilla A* pathfinding (`net.minecraft.entity.ai.pathing`) plans a full route that auto-avoids obstacles/cliffs, plus lava & void avoidance, stuck detection, and walking retries.
- **Survival-aware mining** — proper `attackBlock` + `updateBlockBreakingProgress` progression, tool durability checks, plus creative-mode instant break.
- **Rollback detection** — periodically re-scans the area to re-mine any blocks rolled back by the server.
- **Safety stops** — automatically halts on player death, game pause, or disconnect.
- **Fully configurable** — every timing, distance, and retry parameter can be tuned in-game via Mod Menu + Cloth Config.

## Usage

| Action | Key / Input |
| --- | --- |
| Select point 1 | Hold a sword + right-click a block |
| Select point 2 | Hold a sword + sneak + right-click a block |
| Start / stop mining | Press <kbd>K</kbd> |

After both points are selected, press <kbd>K</kbd>. The mod will iterate through every block in the cuboid, walk to each one, face it, and break it. Press <kbd>K</kbd> again to stop.

> The sword is only used as the selection tool — it does not need to be held while mining. Equip your actual pickaxe (or any tool) before starting.

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | >= 0.19.3 |
| Fabric API | * (latest for 1.21.11) |
| Java | >= 21 |
| Mod Menu | >= 12.0.0 (suggested) |
| Cloth Config API | bundled |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.21.11.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and drop it into your `mods` folder.
3. (Suggested) Install [Mod Menu](https://modrinth.com/mod/modmenu) to access the config screen.
4. Drop the AreaAutoMiner `.jar` into your `mods` folder.
5. Launch the game.

## Configuration

Open **Mods → AreaAutoMiner → Config** (requires Mod Menu + Cloth Config), or edit `config/areaautominer.json` directly.

### Timing

| Option | Default | Description |
| --- | --- | --- |
| Facing wait ticks | 15 | Wait time for a standard turn (ticks) |
| Short facing wait ticks | 4 | Wait time for short-distance turns |
| Move wait ticks | 3 | Stabilization wait after moving |
| Max walk ticks | 200 | Max time allowed to reach a block |
| Max stuck ticks | 20 | Ticks without movement before considered stuck |
| Max break ticks | 400 | Max time to break a single block |

### Distance

| Option | Default | Description |
| --- | --- | --- |
| Max reach squared | 16.0 | Max horizontal reach squared (4 blocks) |
| Arrive threshold | 1.2 | Distance at which the player is considered arrived |
| Max vertical distance | 4.0 | Max vertical distance to a target block |
| Path follow range | 32 | Max vanilla A* pathfinding distance, also the chunk cache radius (blocks). Too small → far targets fail to path; too large → performance hit |

### Retry

| Option | Default | Description |
| --- | --- | --- |
| Max walk retries | 2 | Retries before skipping an unreachable block |
| Max facing retries | 2 | Retries before force-starting the break |
| Max air skip per tick | 5 | Air blocks skipped per tick |

### Debug

| Option | Default | Description |
| --- | --- | --- |
| Debug mode | false | Enable debug log output |
| Mining mode | FROM_TOP_DOWN | `FROM_TOP_DOWN` / `FROM_BOTTOM_UP` |
| Rollback detection | true | Re-scan for server-rolled-back blocks |
| Max rollback retries | 3 | Max re-mine attempts on rollback |
| Rollback check interval | 20 | Ticks between rollback scans (20 = 1s) |

## How It Works

The miner runs as a per-tick state machine driven by [MiningController](src/main/java/xyz/hengke/areaautominer/controller/MiningController.java):

```
IDLE → FINDING_BLOCK → WALKING_TO_BLOCK → FACING_BLOCK → BREAKING → (loop) → IDLE
```

1. **FINDING_BLOCK** — [BlockFinder](src/main/java/xyz/hengke/areaautominer/finder/BlockFinder.java) skips air, checks reach / vertical distance / line of sight, and either starts walking or jumps straight to facing.
2. **WALKING_TO_BLOCK** — [PathfindingHelper](src/main/java/xyz/hengke/areaautominer/helper/PathfindingHelper.java) calls vanilla A* pathfinding (`PathNodeNavigator`) to plan a full route to the target block, and [MovementHelper](src/main/java/xyz/hengke/areaautominer/helper/MovementHelper.java) walks the player along the `Path` nodes with simulated key presses, skipping blocks that are too dangerous to reach.
3. **FACING_BLOCK** — [CameraHelper](src/main/java/xyz/hengke/areaautominer/helper/CameraHelper.java) smoothly rotates the view toward the target with dynamic jitter that fades as it converges.
4. **BREAKING** — [BreakingHelper](src/main/java/xyz/hengke/areaautominer/helper/BreakingHelper.java) breaks the block (creative instant-break, or survival `attackBlock` + `updateBlockBreakingProgress`), then advances to the next block once it becomes air.

A shared [MiningContext](src/main/java/xyz/hengke/areaautominer/context/MiningContext.java) holds all runtime state, and [AreaIterator](src/main/java/xyz/hengke/areaautominer/helper/AreaIterator.java) walks the cuboid according to the selected mining mode.

## Project Structure

```
src/main/java/xyz/hengke/areaautominer/
├── AreaAutoMiner.java            # Common entrypoint
├── AreaAutoMinerClient.java      # Client entrypoint (input, render, tick)
├── config/                       # Config model, Cloth Config screen, Mod Menu integration
├── context/                      # MiningContext — shared runtime state
├── controller/                   # MiningController — state machine driver
├── finder/                       # BlockFinder — next-block selection
├── helper/                       # Camera, Movement, Breaking, Input, Area iterator, Spatial utils
├── listener/                     # MiningListener — event callbacks
├── model/                        # MinerMod & MiningState enums
├── render/                       # RegionRenderer — area outline
└── service/                      # Notification & completion services
```

## Technical Notes

- **Client-side only** — no server-side component; safe to use on vanilla servers.
- **Access widener** — widens `KeyBinding.setPressed` so movement can be simulated with the game's native physics instead of direct coordinate teleportation.
- **No mixins** — the mod relies purely on Fabric events and the access widener.

## Building

```bash
./gradlew build
```

The remapped mod jar will be in `build/libs/`. Requires JDK 21.

## License

Source code is licensed under the **MIT License**. See [LICENSE](LICENSE).
