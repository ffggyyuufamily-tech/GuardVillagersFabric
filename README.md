# Guard Villagers

Guard Villagers is a Fabric mod for Minecraft `1.21.11` (Java 21) that turns passive villagers into an active village-defence system. Villages grow their own guard garrison based on resident density, players can hire and train personal guards with emerald blocks, and every guard can be issued orders — follow, stay, patrol a zone, fall into a formation, defend the perimeter, or concentrate on crowd control — from an in-game tactics screen.

## Summary

- **Two kinds of guards.** Natural village guards spawn around populated villages and patrol in response to local threats; personal guards are hired from villagers that trust you, are bound to your UUID, and follow you across dimensions.
- **Roles and loadouts.** Each guard is one of two combat roles (`Swordsman`, `Bowman`), each with its own AI goal stack. Gear is account-level — upgrades persist for all of a player's future guards instead of being applied to individual entities.
- **Behaviours and formations.** Four behaviours (`Defensive`, `Offensive`, `Perimeter`, `Crowd Control`) and named formations control how the guard prioritises targets, how it moves relative to the owner, and how it shares intent with the rest of its squad.
- **Tactics screen.** A top-down, discovered-chunk map lets you paint chunk zones, bind colours to groups, and view a synced roster with live health, XP, and distance readouts. Groups can be named, coloured, and used to issue squad-level commands (assign, rename, deploy, recall).
- **Perimeter and patrol logic.** Villages expose their door/POI density to the guard AI so patrol goals, recovery-stall fallback, and crowd-unsticking all respect real village bounds rather than fixed hard-coded boxes.
- **Reputation system.** Per-player village reputation gates whether unowned guards will defend you, whether you can claim them, and whether trades feed the guard pool. Operators can inspect and set reputation values via `/guards reputation`.
- **Operator debug tooling.** `/guards debug` (operator-only) turns on a live overlay of pathfinding nodes, combat targets, and intent decisions for every visible guard, syncing to the client only for the player who enabled it.

## Design highlights

- **Central AI coordinator.** Each guard runs a thin `GuardAiController` that computes a `Decision` (main target, urgent target, intent, cooldowns) once per tick; the goals read that decision instead of re-deriving it, which keeps goal logic deterministic and cheap to tune.
- **Per-squad route cache.** Path computations are shared across a squad heading to the same quantised target for a bounded TTL (2s) and soft-capped size, so a 10-guard squad only triggers one pathfind, not ten.
- **Concurrent-safe statics.** The cross-world static caches (`OWNER_USED_NAMES`, `DEBUG_PATH_HASH_CACHE`, `SquadRouteCache.SQUAD_ROUTES`) use `ConcurrentHashMap` so per-dimension tick threads and NBT load hooks can mutate them without the `HashMap` corruption seen in earlier revisions.
- **Bounded command surface.** `/guards groups rename/assign` clamp their row index to `MAX_GROUP_ROW` (64) and `GuardTacticsState.ensureGroupCount` mirrors the cap, so an unprivileged player can't force a multi-billion-entry group list.
- **Save/load round-trip.** The guard's formation type is now read back from NBT on load (previously it was always reset to `FOLLOW`), so player-configured formations survive restarts. Tactics group-colour codec range covers the full supported palette (0..10) instead of silently dropping colour IDs above 4.

## Contents

- [What The Mod Adds](#what-the-mod-adds)
- [Requirements](#requirements)
- [Installation](#installation)
- [Getting Started](#getting-started)
- [Core Gameplay](#core-gameplay)
- [Guard Controls](#guard-controls)
- [Shop And Upgrades](#shop-and-upgrades)
- [Tactics And Groups](#tactics-and-groups)
- [Reputation And Trust](#reputation-and-trust)
- [Commands](#commands)
- [Creative And Admin Tools](#creative-and-admin-tools)
- [Building From Source](#building-from-source)

## What The Mod Adds

- Natural village guards that spawn and persist as defenders.
- Hireable personal guards purchased with emerald blocks.
- Claimable unowned guards if your village trust is high enough.
- Two main combat roles:
  - `Swordsman`
  - `Bowman`
- Four behaviors:
  - `Defensive`
  - `Offensive`
  - `Perimeter`
  - `Crowd Control`
- A whistle item for opening tactics, rallying leaders, and assigning patrol zones.
- A tactics screen with:
  - top-down discovered-chunk map
  - paintable chunk zones
  - group-color bindings
  - a synced roster of owned guards
- A shop with persistent account-style upgrades for future guards.
- Operator-only reputation and debug commands.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | `1.21.11` |
| Java | `21` |
| Fabric Loader | `0.18.2` or newer |
| Fabric API | `0.139.4+1.21.11` |

## Installation

### For Players

1. Install Minecraft `1.21.11`.
2. Install Fabric Loader for the same version.
3. Install Fabric API.
4. Place the built `guard-villagers-1.0.0.jar` into your `mods` folder.
5. Launch the game with the Fabric profile.

### Where The Jar Comes From

- Release or build outputs are written to `build/libs/`.
- The main runtime artifact is the remapped mod jar in that folder.

## Getting Started

### Quick Start

1. Join a world with the mod installed.
2. Open the guard shop with `/guards shop`.
3. Hire your first guard.
4. Use `/guards follow`, `/guards stay`, and `/guards tactics` to control them.
5. Use the whistle to set patrol zones or rally leaders.

### First Guard Options

You can get a guard in three main ways:

- Hire one from the shop using emerald blocks.
- Claim an unowned guard by right-clicking it with an emerald, if your reputation is trusted.
- Use the creative spawn egg if you are testing or building.

## Core Gameplay

### Natural Village Guards

- Villages are actively maintained with guards over time.
- Natural guards are meant to act as defenders for villages.
- They can still be claimed by a player if they are unowned and that player is trusted.

### Player-Owned Guards

Owned guards remember and persist:

- owner
- squad identity
- level and experience
- equipment
- assigned group
- patrol home / zone
- follow state

### Guard Levels

- Guards level up from combat and passive time.
- Level is capped at `10`.
- Higher level guards cost more to claim if they are already unowned world guards.

Unowned guard hire cost is:

- `4 + 2 * (level - 1)` emeralds

## Guard Controls

## Empty-Hand Interactions

Right-click your own guard with an empty hand:

- normal right-click: toggle `stay` / `follow`
- sneak + right-click: cycle behavior

Behavior cycle order:

1. `Perimeter`
2. `Crowd Control`
3. `Offensive`
4. `Defensive`

### Giving Equipment To Guards

Right-click your own guard while holding gear:

- Better swords replace weaker swords on swordsmen.
- Better bows replace weaker bows on bowmen.
- Better armor can be equipped.

Armor upgrade rules:

- You must be sneaking.
- You must be close to the guard.
- The offered piece must be better than the current one, or equal tier with better Protection enchantment.

### Follow And Stay

- `Stay` holds guards in place and clears forced follow.
- `Follow` resumes following.
- Plain `/guards follow` now affects all owned guards, even zoned ones.
- Group-specific `/guards follow <group name>` only affects guards in that group.

### Zones And Home Tethering

- Zoned guards can be assigned a home area and patrol radius.
- While forced follow is active, zone tethering is suppressed so they are not dragged back home mid-follow.
- Assigning or clearing home state removes forced follow and returns the guard to zone-based logic.

### Whistle Controls

The whistle is one of the main control items in the mod.

Controls:

- Right-click in air: open the tactics screen.
- Sneak + right-click in air: rally nearby squad leaders.
- Right-click your owned guard: assign its current position as home.
- Sneak + right-click your owned guard: increase patrol radius.

## Shop And Upgrades

Open the shop with `/guards shop`.

### Buying Guards

- Base hiring is done with emerald blocks.
- Shift-clicking the hire card buys as many as you can afford.
- In creative mode, hiring is free and bulk-buy caps at `64`.
- If the exact player `X/Z` column cannot support a spawn, the purchase fails cleanly instead of spawning offset to the side.

### Reputation-Based Cost

Shop hire price is modified by reputation:

- low reputation: up to `1.5x`
- high reputation: down to `0.75x`

### Upgrade Categories

The shop has three persistent upgrade tracks.

#### Armor Upgrades

- Max level: `8`
- Cost pattern: starts at `4`, doubles per level, capped at `64`
- Improves the armor quality distribution on future hired guards

#### Weapon Upgrades

- Max level: `5`
- Cost pattern: starts at `4`, scales aggressively, capped at `64`
- Improves swords for swordsmen and bow Power level for bowmen

Weapon progression:

- Sword: Stone -> Iron -> Diamond -> Diamond Sharpness III -> IV -> V
- Bow: Power I -> II -> III -> IV -> V

#### Support Upgrades

- Max level: `3`
- Stage 1: passive healing
- Stage 2: shield unlock
- Stage 3: stronger/faster healing

Support progression:

| Support Level | Effect |
| --- | --- |
| `0` | No support bonus |
| `1` | Healing enabled |
| `2` | Shield enabled |
| `3` | Advanced healing enabled |

Healing cadence:

- no support: `1 HP every 5s`
- support level 1 or 2: `2 HP every 2.5s`
- support level 3: `2 HP every 1s`

## Tactics And Groups

Open the tactics UI with:

- `/guards tactics`
- `/guards groups`
- right-clicking the whistle in air

### Tactical Map

The tactics view is a chunk-based control map.

You can:

- pan with middle mouse drag
- zoom with mouse wheel
- left-drag to select chunks
- right-click to paint the active zone color
- shift + right-click to clear zone color
- press `C` to clear selection

Important details:

- Only discovered chunks are paintable.
- Zones are visual tactical overlays.
- Group-to-color tooltips are shown for hovered chunks.

### Groups View

The groups view is driven by a server-synced owned-guard roster.

You can:

- create groups
- rename groups
- assign colors to groups
- drag guards between groups
- save assignments back to the server

Controls:

- left-click and drag guard cards to move them
- shift + right-click a group header to rename it
- click a color swatch to cycle group color
- right-click a swatch to clear it

### Group Notes

- New data starts with no premade groups.
- Unassigned guards are supported and use internal group index `-1`.
- The groups screen no longer depends on the guard entity being loaded on the client, so far-away loaded server guards can still appear in the roster.

## Reputation And Trust

Reputation is normalized from `0.00` to `1.00`.

General meaning:

- `0.50` and above: trusted enough to hire
- below `0.50`: trust is too low to hire

Reputation changes from:

- trading with villagers
- harming villagers
- harming guards
- killing hostiles
- defending against raids
- being killed by guards, which resets your reputation to `0.00`

Decay:

- reputation decays by `0.01`
- decay happens every `36` seconds

### Trust Outcomes

- Trusted players can hire guards.
- Distrusted players are blocked from hiring.
- Very low trust can cause guards to treat the player as hostile.

## Commands

All player-facing commands live under `/guards`.

### Player Commands

| Command | What It Does |
| --- | --- |
| `/guards shop` | Opens the Guard Shop UI. |
| `/guards tactics` | Opens the tactical map UI. |
| `/guards groups` | Opens the groups-focused UI. |
| `/guards stay` | Orders all owned guards to stay. |
| `/guards follow` | Orders all owned guards to follow. |
| `/guards follow <group name>` | Orders only that named group to follow. |
| `/guards zone <radius>` | Assigns the player's current location as home for nearby owned guards. Radius range: `8` to `128`. |
| `/guards groups add` | Adds a new group row. |
| `/guards groups rename <row> <name>` | Renames a group. |
| `/guards groups assign <guardUuid> <groupRow>` | Assigns a guard to a group. Use `0` to unassign. |
| `/guards count` | Shows how many guards you own. |

### Operator-Only Commands

| Command | What It Does |
| --- | --- |
| `/guards reputation <value>` | Sets your own reputation. |
| `/guards reputation <player> <value>` | Sets another player's reputation. |
| `/guards debug` | Toggles guard debug rendering. |
| `/guards debug <range>` | Enables debug and sets render range. |

Reputation command input rules:

- valid range: `0.00` to `1.00`
- up to two decimal places

Debug range notes:

- range is capped to half the player's view distance in blocks
- debug data includes pathing and target overlays

## Creative And Admin Tools

The mod includes:

- `Guard Spawn Egg`
- `Guard Whistle`
- a dedicated item group for quick access in creative

Debug mode is intended for operators and advanced troubleshooting. It can show:

- path nodes
- current path index
- target linkage
- behavior / owner / group labels

## Building From Source

### Windows

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat build
```

### macOS / Linux

```bash
./gradlew runClient
./gradlew runServer
./gradlew build
```

Build outputs are written to `build/libs/`.

## License

Licensed under `CC0-1.0`. See [LICENSE](LICENSE).
