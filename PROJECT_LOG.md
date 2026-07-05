## [2026-03-10] - Rebuild And Deploy Guard Villagers Jar
### What Was Implemented
- Rebuilt the mod from clean HEAD using .\gradlew.bat build on Java 21.
- Produced a fresh build/libs/guard-villagers-1.0.0.jar artifact.
- Replaced the installed-game jar at %APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar with the rebuilt artifact.
- Verified the built artifact and deployed copy match after deployment.

### Files Modified
- `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` - updated with the freshly built artifact.
- PROJECT_LOG.md - recorded the rebuild and deployment task.

### Assumptions Made (flag these for review)
- Deployment target is `%APPDATA%\.minecraft\mods\` (the actual Minecraft instance), not a repo-local copy.
- build/libs/guard-villagers-1.0.0.jar remains the correct release artifact for deployment.

### Known Issues / Deferred
- No gameplay or in-game smoke validation was run as part of this task.
- The Gradle build emitted an existing deprecation note for src/client/java/com/guardvillagers/client/GuardVillagersClient.java but completed successfully.

### Suggested Next Steps
- Launch the game and verify the newly deployed guard AI/pathfinding/visual changes behave as expected.
- If desired, investigate the deprecated API usage noted during the build to keep future upgrades simpler.
## [2026-03-06] â€” Guard Villagers Command/Reputation/Rendering/UI Cleanup
### What Was Implemented
- Removed `/guards behavior ...`, `/guards behaviour ...`, and `/guards hierarchy` command literals.
- Added op-only `/guards reputation <value>` and `/guards reputation <player> <value>` with strict `0.00..1.00` validation (max 2 decimals).
- Gated `/guards debug` and `/guards reputation` behind operator-level permission checks so non-ops do not see/execute them.
- Reworked reputation to normalized `0.00..1.00` semantics with legacy `[-200..200]` linear migration at decode time.
- Disabled gossip influence in guard trust/hostility computation and removed reputation-based hire cost scaling.
- Preserved legacy event impact proportions via `legacyDelta / 400.0`.
- Added runtime decay of `0.01` every `36s` (`720` ticks) toward `0.00`.
- Added guard-kill reset so players killed by guards immediately get reputation `0.00`.
- Added creative-op damage-time exemption for guard harm penalties and guard retaliation targeting.
- Switched guard default texture resolution to vanilla villager texture via centralized skin resolver.
- Added armor feature rendering support and biped-compatible model path so armor/held/offhand rendering works through standard feature layers.
- Added skin profile placeholder storage on `GuardEntity` for future custom skin extensibility.
- Changed new-data group defaults to zero premade groups (server + client stores).
- Made new natural/purchased guards unassigned by default and allowed unassigned group index (`-1`).
- Updated group assignment command/UI flow to support unassigning (`groupRow 0` -> internal `-1`).
- Updated groups-pane wheel behavior to hovered-pane, snapped stepping with smooth interpolation.
- Added 140ms smooth palette wheel scroll animation (wheel-only; swatch click remains immediate).
- Added ~80% opacity 1px zone outlines for clearer chunk zone boundaries.
- Updated hire lore text to exact `Shifh-click to buy max.` and retained max-affordable shift-click purchase behavior.

### Files Modified
- `src/main/java/com/guardvillagers/GuardVillagersMod.java` â€” command tree cleanup/gating, reputation command, unassigned group assignment handling, decay tick hookup.
- `src/main/java/com/guardvillagers/GuardReputationManager.java` â€” normalized reputation logic, scaling, no gossip/price influence, decay, reset/set helpers.
- `src/main/java/com/guardvillagers/data/GuardReputationState.java` â€” persisted format migration support and normalized storage APIs.
- `src/main/java/com/guardvillagers/entity/GuardEntity.java` â€” unassigned defaults, creative-op damage exemption, kill reset, skin profile placeholders, group index read/write updates.
- `src/main/java/com/guardvillagers/data/GuardTacticsState.java` â€” zero premade groups for new data.
- `src/main/java/com/guardvillagers/tactics/GuardTacticsInventory.java` â€” removed forced default group creation.
- `src/client/java/com/guardvillagers/client/ClientTacticsDataStore.java` â€” zero premade groups for new client world data.
- `src/client/java/com/guardvillagers/client/GuardTacticsScreen.java` â€” snapped+smoothing pane scroll, palette wheel animation, zero-default group handling.
- `src/client/java/com/guardvillagers/client/ChunkMapWidget.java` â€” zone outline rendering pass.
- `src/client/java/com/guardvillagers/client/GuardEntityModel.java` â€” switched to biped-compatible model base for feature rendering.
- `src/client/java/com/guardvillagers/client/GuardEntityRenderer.java` â€” skin resolver usage and armor/held feature setup.
- `src/client/java/com/guardvillagers/client/GuardSkinResolver.java` â€” centralized default skin resolution + placeholder path for future custom skins.
- `src/main/java/com/guardvillagers/shop/GuardShopInventory.java` â€” exact hire lore text update.

### Assumptions Made (flag these for review)
- Used `PermissionLevel.GAMEMASTERS` as the equivalent of `requires(... permission level 2)` in the current permission API.
- Treated missing/new reputation as neutral `0.50` (legacy `0` equivalent) so legacy behavior maps linearly.
- Used `"Unassigned"` as the default label when guards are not in any group.
- Implemented default villager skin on the current guard biped model path (not the vanilla villager-entity model class).

### Known Issues / Deferred
- In-game/manual validation steps from the full test matrix are not executed yet in this change set.
- Existing `logs/mcp_server.log` was already modified before implementation and was not changed intentionally as part of this task.

### Suggested Next Steps
- Run the provided test plan in-game (permissions, command validation, decay timing, creative-op exemption, rendering checks, groups/UI behavior).
- If desired, add automated command parsing tests for reputation input edge cases and assignment `0` (unassigned) coverage.

## [2026-03-07] - Codex 5.3: Shop/Economy Fixes + Debug System Overhaul

### What Was Done

#### Section A - Shop & Economy
- Creative mode bypass: all shop purchases show "Cost: Free" and succeed without emerald blocks.
- Creative mode bulk purchase now caps at 64 guards when shift-clicking.
- Armor upgrade pricing updated: starts at 4, doubles per level, caps at 64 emerald blocks.
- Weapon upgrade pricing updated: starts at 4, quadruples per level, caps at 64 emerald blocks.
- Hiring price reduced to base 4 + 2 per hire level.
- Reintroduced reputation-based hire cost scaling (0.75x to 1.5x modifier) in `GuardVillagersMod.getAdjustedGuardCost`.
- Fixed lore typo: "Shifh-click" -> "Shift-click".
- Weapon display now shows both sword and bow progression in upgrade cards.
- Shop info book simplified to armor odds, sword level, bow level, healing status, and shield status only.

#### Section B - Debug System
- Added `GuardDebugState` (`PersistentState`) for per-player debug toggle and range persistence.
- Added `GuardDebugManager` utility for state access and effective-range calculation.
- Reworked `/guards debug [range]` command:
- OP-gated via existing operator permission check.
- `/guards debug` toggles on/off.
- `/guards debug <range>` enables (if needed) and updates range.
- Range is capped to half of player view distance in blocks.
- Added S2C payloads:
- `GuardDebugSyncPayload` for local debug enabled/range state.
- `GuardDebugDataPayload` for batched per-guard path nodes/current index/target ID.
- Added server-side periodic debug sync every 5 ticks with per-player scoping and hash-based path change detection.
- Added client-side state holders:
- `ClientDebugState` for local toggle/range.
- `ClientGuardDebugData` for per-guard path/target cache.
- Replaced debug renderer stub with a full `GuardDebugRenderer` wired to `WorldRenderEvents.AFTER_ENTITIES`:
- Head labels: HP, Lvl, XP, Role, Behavior, Owner, Group, Zone.
- Green 32-block detection ring.
- Blue path block highlights + red current-position block.
- Yellow line from guard eye to closest point on target AABB.
- Range-based visibility filtering and local-player-only rendering.
- Updated `GuardEntity` with a path/target debug snapshot accessor for packet sync.
- Updated `GuardVillagersClient` to register packet handlers and clear debug caches on disconnect/stop.

### Decisions Made
- Debug persistence is server-side `PersistentState`, which also covers integrated singleplayer worlds.
- Debug data sync is throttled to every 5 ticks and only sends changed guard snapshots per player.
- Max synced path length is capped at 64 nodes.
- Client guard-in-range list is cached and refreshed every 10 ticks.
- Reputation pricing uses linear scaling from 1.5x (rep 0.0) to 0.75x (rep 1.0), clamped.

### Current State
- Section A and Section B code changes are implemented and compiling.
- Full project build passes after Section A and again after Section B.
- Rendering uses vanilla/Fabric rendering APIs only.

### Next Steps / Open Items
- Execute in-game manual checklist validation for all Section A and Section B scenarios.
- Run runtime/performance checks with high guard counts to tune debug rendering cost if needed.

## [2026-03-07] — Codex 5.3 Hotfix: Crash Fix + Model/Armor Geometry

### What Was Done
- **Crash fix:** Replaced `RenderLayers.lines()` GL line rendering with camera-facing billboard quads
  - Deleted broken `lineVertex()` helper that was missing the `lineWidth` vertex element
  - New `renderLineSegment()` method renders lines as thin quads using `RenderLayers.debugFilledBox()`
  - Detection circle and target line both use the new quad-based approach
  - Guarantees consistent line width across all GPU drivers (GL_LINES width > 1.0 is unreliable)
- **Arm fix:** Changed guard arm height from 8px to 12px — hands are now fully visible
- **Armor fix:** Created custom `GUARD_ARMOR_LAYER` with villager-matching proportions
  - Head: 10px tall (matches villager), uses standard armor UV for correct texture mapping
  - Body: 6px deep (matches villager)
  - Arms: 12px tall (matches fixed guard arms)
  - Dilation: 1.0F standard armor offset
  - Texture size: 64×32 (standard armor texture format)
- **Renderer fix:** Switched `GuardEntityRenderer` from `EntityModelLayers.PLAYER_EQUIPMENT` to `GUARD_ARMOR_LAYER`
- **Registration:** Added `GUARD_ARMOR_LAYER` to `EntityModelLayerRegistry` in client initializer

### Decisions Made
- Billboard quads chosen over fixing `lineWidth` vertex element because GL line width > 1.0px is not guaranteed by OpenGL spec and is ignored by many NVIDIA/AMD drivers
- Armor model uses standard player/armor UV layout (64×32) so vanilla armor textures render correctly, with villager cuboid dimensions for geometry matching
- Minor armor texture stretching on the 10px head (vs standard 8px) is accepted — pixel-perfect would require custom armor textures (deferred)

### Files Modified
- `src/client/java/com/guardvillagers/client/GuardDebugRenderer.java` — billboard quad line rendering
- `src/client/java/com/guardvillagers/client/GuardEntityModel.java` — arm height fix, armor layer
- `src/client/java/com/guardvillagers/client/GuardEntityRenderer.java` — custom armor layer usage
- `src/client/java/com/guardvillagers/client/GuardVillagersClient.java` — armor layer registration

### Next Steps
- In-game verification of all checklist items
- Consider custom armor textures for pixel-perfect villager-proportioned armor (future)
- Verify held item (sword/bow/shield) positioning hasn't shifted with arm length change

## [2026-03-10] — Vanilla Plains Villager Guard Renderer
### What Was Implemented
- Replaced the guard’s partial custom villager geometry with a full vanilla-aligned side-arm villager model based on the zombie villager proportions, including nose, hat rim, robe jacket, and vanilla villager UV layout.
- Switched the guard renderer to `BipedEntityRenderer` so held-item, head-item, and general humanoid render state handling stays on the standard vanilla path.
- Added vanilla zombie-villager armor model layers for the guard so helmets, chestplates, leggings, and boots render on villager-shaped proportions instead of the previous mismatched layer setup.
- Added the vanilla villager clothing overlay feature and fixed the guard’s visual data to a plains villager with profession `NONE`, so the guard now renders as a fixed plains villager without editing the vanilla texture assets.
- Kept explicit bow-draw and shield-block arm poses while preserving vanilla swing animation feedback for melee weapons.
- Verified the client compile and full Gradle build both complete successfully.

### Files Modified
- `src/client/java/com/guardvillagers/client/GuardEntityModel.java` — replaced the guard model geometry with a vanilla-aligned side-arm villager layout and added model layer IDs used by the renderer/features.
- `src/client/java/com/guardvillagers/client/GuardEntityRenderer.java` — moved the renderer to the vanilla biped pipeline, added armor + plains villager clothing features, and fixed render state data for the villager overlay.
- `src/client/java/com/guardvillagers/client/GuardVillagersClient.java` — registered the additional model layers required by the updated renderer setup.
- `PROJECT_LOG.md` — recorded the implementation and verification status.

### Assumptions Made (flag these for review)
- A fixed plains villager appearance means base `villager.png` plus the vanilla `villager/type/plains.png` overlay, with profession set to `NONE` and level `1`.
- Guards remain adult-only, so the extra baby model registrations are present for renderer/feature compatibility rather than active gameplay use.
- Vanilla zombie-villager armor geometry is the correct fit for a side-arm villager body and is preferable to the standard player armor geometry for visual alignment.

### Known Issues / Deferred
- I did not complete an in-game visual pass from the terminal, so “no visual bugs” is implemented to the strongest extent available from source parity plus a successful build, but still needs a live render check in Minecraft.
- The legacy custom texture file `assets/guardvillagers/textures/entity/guard_villager.png` remains in the project but is not part of the active guard render path.

### Suggested Next Steps
- Spawn a guard with sword, bow, shield, and each armor slot filled, then verify idle, swing, bow draw, and shield block poses in-game.
- If the plains overlay or armor still shows any clipping in motion, capture one screenshot per case and adjust only the affected cuboid dilation or feature layer, not the vanilla texture data.

## [2026-03-10] — Build And Install Mod Jar
### What Was Implemented
- Rebuilt the mod jar from the current source with Gradle.
- Replaced the installed mod at `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` with the freshly built artifact from `build/libs/guard-villagers-1.0.0.jar`.
- Verified the installed jar matches the built jar by SHA-256 hash.

### Files Modified
- `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` — replaced with the current build output.
- `PROJECT_LOG.md` — recorded the build and install step.

### Assumptions Made (flag these for review)
- The correct runtime artifact for the local Minecraft instance is `build/libs/guard-villagers-1.0.0.jar` rather than the sources jar.

### Known Issues / Deferred
- No in-game launch was performed as part of this step.

### Suggested Next Steps
- Launch Minecraft and verify the installed mod loads the updated renderer changes.

## [2026-03-10] — Corrected Active Minecraft Mods Path
### What Was Implemented
- Checked the actual Minecraft launch logs and confirmed the game session was using `%APPDATA%\\.minecraft`, not the workspace-local `.minecraft` directory.
- Replaced `%APPDATA%\\.minecraft\\mods\\guard-villagers-1.0.0.jar` with the current built artifact.
- Verified the active installed jar now matches the build output by SHA-256 hash.

### Files Modified
- `%APPDATA%\\.minecraft\\mods\\guard-villagers-1.0.0.jar` — replaced the older active runtime jar with the current build.
- `PROJECT_LOG.md` — recorded the active instance path correction.

### Assumptions Made (flag these for review)
- The screenshots and latest launch were from the default `%APPDATA%\\.minecraft` instance shown in `latest.log`.

### Known Issues / Deferred
- Minecraft still needs a full restart after the jar replacement to load the updated code.

### Suggested Next Steps
- Relaunch Minecraft and check the guard again in the same world/instance.

## [2026-03-10] — Villager Arm UV And Clothing Layer Fix
### What Was Implemented
- Split each guard arm into an 8-pixel upper segment plus a 4-pixel lower segment so the model only samples opaque pixels from the unchanged vanilla villager arm atlas.
- Removed the long robe cuboid from the base guard model and moved the plains villager lower robe to the dedicated clothing layer model instead.
- Changed the clothing layer model to use a separate `jacket` child with vanilla villager-style `0.5F` dilation so the plains robe renders as an outer garment rather than a near-flush body extension.
- Re-registered the clothing model layers to use the new clothing-specific textured model data.
- Rebuilt the mod and replaced both the workspace-local and active `%APPDATA%` jars with the updated artifact.
- Verified `build/libs/guard-villagers-1.0.0.jar` and `%APPDATA%\\.minecraft\\mods\\guard-villagers-1.0.0.jar` match by SHA-256 hash: `5A24100EC897B44A1EC4E4B90921C95AA469C4E752CF7B729A54A4705B9317B2`.

### Files Modified
- `src/client/java/com/guardvillagers/client/GuardEntityModel.java` — split arm geometry to avoid transparent villager UV rows and separated the clothing robe from the base model.
- `src/client/java/com/guardvillagers/client/GuardVillagersClient.java` — pointed the clothing model layer registrations at the clothing-specific textured model data.
- `%APPDATA%\\.minecraft\\mods\\guard-villagers-1.0.0.jar` — replaced with the rebuilt artifact containing the arm/clothing fix.
- `.minecraft\\mods\\guard-villagers-1.0.0.jar` — replaced with the same rebuilt artifact for workspace-local testing parity.
- `PROJECT_LOG.md` — recorded the root cause, implementation, and deployment details.

### Assumptions Made (flag these for review)
- The missing hand sides were caused by the vanilla villager arm UV block only containing valid pixels for an 8-pixel-tall arm section plus top/bottom faces, which required segmented arm geometry rather than texture edits.
- The robe clipping was caused by using zombie-villager-style near-flush robe geometry for the villager clothing overlay, and the vanilla villager clothing path should instead use the separate jacket child with `0.5F` dilation.

### Known Issues / Deferred
- I still did not complete a live in-game visual verification after this specific patch, so the source-level fix and successful build/install are done, but runtime confirmation is still required.
- Baby model registrations still reuse the adult geometry path; this was left unchanged because the reported issue is on adult guards and guards are expected to remain adult in normal play.

### Suggested Next Steps
- Relaunch Minecraft completely and verify two cases first: an unarmored guard standing idle, and a guard wearing armor while holding a weapon.
- If any remaining artifact appears, capture one close screenshot from the front and one from the side so the exact failing cuboid face can be isolated without changing the vanilla textures.

## [2026-03-10] — Resume Notes For Follow Recovery And Tactical Map Overhaul
### What Was Implemented
- Audited the existing follow, shop spawn, groups UI, and tactical map code paths against the requested implementation plan.
- Confirmed the current worktree was clean before documentation, so no partial implementation from the aborted session needs to be unwound.
- Created `contine_next_session.md` as a detailed handoff file with confirmed defect locations, implementation order, target files, and verification steps for the next session.

### Files Modified
- `contine_next_session.md` — added a detailed resume brief for the follow recovery, server-synced groups roster, exact spawn-column, and tactical map performance work.
- `PROJECT_LOG.md` — recorded the current status and the remaining implementation work.

### Assumptions Made (flag these for review)
- The user’s instruction to keep the guard visual “perfect” means renderer, model, and texture behavior are out of scope for the next implementation pass.
- The tactical zone changes remain visual-only and chunk-based; no server-side gameplay wiring is part of the deferred work.
- “Server-owned roster” means guards currently loaded and known to the server, without force-loading chunks.

### Known Issues / Deferred
- The requested implementation has not been started yet; this session stopped at analysis and handoff documentation only.
- Plain `/guards follow` still only targets unzoned guards.
- Shop purchases still spawn using the current lateral offset ring rather than the player’s exact `X/Z`.
- The Groups screen still depends on client-loaded `GuardEntity` instances and can therefore miss owned guards.
- The tactical map still uses the current expensive per-frame terrain rasterization path and has not received the planned 64x64 cached-texture rewrite.

### Suggested Next Steps
- Start implementation in this order: `GuardVillagersMod`, `GuardEntity`, `FormationFollowOwnerGoal`, `GuardNavigation`/`SquadRouteCache`, new roster payload, client roster consumption, then `ChunkTerrainCache` and `ChunkMapWidget`.
- Build after each subsystem milestone with `.\gradlew.bat compileJava compileClientJava`, then finish with `.\gradlew.bat build`.
- Use `contine_next_session.md` as the source of truth for the exact remaining work, acceptance criteria, and file-level hotspots.

## [2026-03-10] — Follow Recovery, Synced Guard Roster, And Tactical Map Rewrite
### What Was Implemented
- Reworked plain `/guards follow` to target all owned guards, while keeping group-specific follow limited to the named group.
- Added a persisted follow-override state on guards, cleared it on stay/home assignment flows, suppressed zone tethering while active, and blocked higher-priority behavior goals from stealing follow control.
- Replaced the owner follow goal with conditional catch-up behavior that only activates when a guard is materially behind or stalled relative to the owner, with a transient catch-up speed modifier and 2-tick repaths.
- Changed navigation stall recovery from stop-only to stop + targeted squad-route invalidation + immediate repath, and added nearby dry-exit probing when guards stall in or against flowing water.
- Removed lateral hire offsets so purchased guards now spawn on the player's exact `X/Z` column and either find a valid `Y` on that column or fail cleanly.
- Added a server-authenticated roster sync payload with authoritative group names plus per-guard summaries, and sent it when the tactics/groups screen opens and after group add/rename/assignment changes.
- Added a transient client roster store, updated client group-name syncing, and migrated the groups screen/drag flow from client entity scans to synced guard summaries keyed by guard UUID.
- Rebuilt the tactical map terrain pipeline to use 64x64 chunk sampling, cached chunk textures for zoomed-in rendering, average-color LOD for zoomed-out rendering, a viewport-bounded discovered-chunk index, and stable per-frame chunk edge coordinates.
- Verified `.\gradlew.bat compileJava compileClientJava` and `.\gradlew.bat build` both complete successfully after the implementation.

### Files Modified
- `src/main/java/com/guardvillagers/GuardVillagersMod.java` — follow command behavior, exact-column purchase spawn, roster payload registration/sending.
- `src/main/java/com/guardvillagers/entity/GuardEntity.java` — persisted follow override, transient catch-up speed modifier, behavior/tether gating changes.
- `src/main/java/com/guardvillagers/entity/goal/FormationFollowOwnerGoal.java` — conditional catch-up activation/exit logic and 2-tick repaths.
- `src/main/java/com/guardvillagers/item/GuardWhistleItem.java` — explicit whistle home assignment now clears forced follow.
- `src/main/java/com/guardvillagers/navigation/GuardNavigation.java` — stall recovery, dry-exit probing, immediate repath behavior.
- `src/main/java/com/guardvillagers/navigation/SquadRouteCache.java` — targeted squad-route invalidation helper.
- `src/main/java/com/guardvillagers/network/GuardRosterSyncPayload.java` — new server-to-client roster/group sync payload.
- `src/client/java/com/guardvillagers/client/ClientGuardRosterStore.java` — transient synced roster store for the active world context.
- `src/client/java/com/guardvillagers/client/ClientTacticsDataStore.java` — authoritative group-name replacement and discovered-chunk viewport index.
- `src/client/java/com/guardvillagers/client/GuardVillagersClient.java` — roster payload receiver registration and transient-store cleanup.
- `src/client/java/com/guardvillagers/client/GuardDragHandler.java` — drag preview now uses synced guard summaries instead of live entities.
- `src/client/java/com/guardvillagers/client/GuardTacticsScreen.java` — groups UI migrated to synced summaries and UUID-based assignment saves.
- `src/client/java/com/guardvillagers/client/ChunkTerrainCache.java` — 64x64 terrain sampling and cached texture generation path.
- `src/client/java/com/guardvillagers/client/ChunkMapWidget.java` — viewport-bounded rendering, average-color LOD, cached texture draw path, stable chunk edges.
- `contine_next_session.md` — refreshed handoff status after the implementation pass.
- `PROJECT_LOG.md` — recorded the completed work, assumptions, and verification state.

### Assumptions Made (flag these for review)
- The catch-up speed modifier uses a `+35%` temporary movement-speed bonus because the requested behavior specified a transient speed mode but did not define the exact multiplier.
- The zoomed-in terrain-texture threshold uses a chunk footprint of roughly `18px` before switching from average-color LOD to the cached 64x64 texture path.
- “Assignment save” payload refresh is satisfied by sending the roster payload after each `/guards groups assign ...` command invoked by the groups screen save flow.

### Known Issues / Deferred
- I did not run an in-game manual pass from the terminal, so the gameplay and renderer acceptance checklist in `contine_next_session.md` still needs live verification.
- `GuardVillagersClient.java` still reports an existing deprecated API usage note during compilation; the build succeeds and this task did not change that deprecation status intentionally.

### Suggested Next Steps
- Launch Minecraft and run the manual checklist for exact-column hiring, forced follow behavior, water/hill recovery, synced groups roster coverage, and tactical-map performance/visual stability.
- If the catch-up speed or texture-threshold tuning feels off in-game, adjust those constants only after measuring the affected scenario rather than broad refactoring.

## [2026-03-10] — README Usage Guide Rewrite
### What Was Implemented
- Rewrote `README.md` into a comprehensive user-facing guide covering installation, first use, guard ownership, controls, tactics, shop progression, reputation, commands, and admin/debug tooling.
- Removed stale documentation for older command surfaces and replaced it with the currently implemented `/guards` command set and current tactics/groups workflows.
- Organized the guide with clearer sections, tables, quick-start steps, and behavior notes so players can use the mod without reading source files.

### Files Modified
- `README.md` — replaced the old overview with a full usage guide based on current mod behavior.
- `PROJECT_LOG.md` — recorded the documentation rewrite and its review assumptions.

### Assumptions Made (flag these for review)
- The README should target end users and server operators rather than mod developers, so it prioritizes gameplay usage and command behavior over internal architecture.
- Documentation should only describe behavior confirmed in the current source, without promising unverified in-game outcomes beyond what the code currently implements.

### Known Issues / Deferred
- I did not run an in-game verification pass specifically for the README wording; the guide is source-based rather than playtested line by line.
- The README currently has no screenshots or GIFs; the formatting is comprehensive text-first documentation.

### Suggested Next Steps
- Review the guide in-game once and adjust any wording that feels inaccurate from a player perspective.
- Add a few screenshots later if you want the README to double as a presentation page for releases.

## [2026-03-10] — Build And Replace Installed Mod Jars
### What Was Implemented
- Ran `.\gradlew.bat build` against the current workspace and confirmed the runtime artifact is up to date.
- Replaced the `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` with the freshly built `build\libs\guard-villagers-1.0.0.jar`.
- Replaced the active `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` with the same built artifact.
- Verified all three jar files match by SHA-256 hash: `4B8A332B1F8AC319917BDDBFB5134B9CE98192FCA335D0244B68B48C2E643D74`.

### Files Modified
- `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` — replaced with the current build output.
- `PROJECT_LOG.md` — recorded the build, deployment, and hash verification.

### Assumptions Made (flag these for review)
- Deployment target is `%APPDATA%\.minecraft\mods\` (the actual Minecraft instance).

### Known Issues / Deferred
- No Minecraft launch or in-game smoke test was performed after copying the jar files.

### Suggested Next Steps
- Restart Minecraft if it is already running so it loads the replaced jar.
- Verify the current world loads the updated mod and spot-check the latest follow/tactics changes in-game.

## [2026-03-11] — Guard Death Message And Upgrade Snapshot Fix
### What Was Implemented
- Changed owned-guard death notifications to use the vanilla tameable-style death message path so the message format comes from Minecraft's normal death-source text.
- Kept the guard death notification owner-only by sending it directly to the resolved owner player instead of broadcasting it.
- Snapshotted armor, weapon, and support upgrade levels onto each guard when its loadout is assigned so later shop upgrades only affect newly hired guards.
- Updated guard healing, shield syncing, and shield damage reduction to use each guard's stored support snapshot instead of the owner's current live upgrade state.
- Persisted the stored loadout snapshot in guard save data and added a fallback for older saved guards that were created before the new fields existed.

### Files Modified
- `src/main/java/com/guardvillagers/entity/GuardEntity.java` — switched death-message generation to the tameable-style path and stored per-guard upgrade snapshots for future support/gear behavior.
- `PROJECT_LOG.md` — recorded the implementation details, migration assumption, and verification result.

### Assumptions Made (flag these for review)
- For guards already saved in the world before this change, missing loadout snapshot data is initialized from the owner's current shop upgrade state once on load so those legacy guards keep their present behavior and do not pick up later upgrades after that point.

### Known Issues / Deferred
- I only verified this with `.\gradlew.bat compileJava`; I did not run an in-game gameplay pass from the terminal.

### Suggested Next Steps
- Spawn or hire one guard, buy a shop upgrade, and confirm only guards hired after the upgrade receive the new loadout/support benefits.
- Kill an owned guard with a few different damage sources in-game to confirm the owner sees the expected vanilla-format death text and nobody else does.

## [2026-03-11] — Guard Debug Overlay Path And Target Fix
### What Was Implemented
- Changed the guard debug targeting line so it only renders when the guard currently has a target, and it now points to that target's eye position.
- Reworked path debug rendering to use the synced `currentPathIndex` so the overlay reflects the guard AI's active path progression in real time.
- Added color-coded flat translucent path markers: green for the current path node, blue for visible route nodes, and red for the final destination node.
- Limited trailing blue path markers so nodes disappear once they are more than 3 path nodes behind the current node.
- Replaced the old full-height destination box and all-green path overlay with thin carpet-like slabs that cover the full block footprint.

### Files Modified
- `src/client/java/com/guardvillagers/client/GuardDebugRenderer.java` — fixed target-line visibility and path-node rendering/coloring to match the intended debug visualization.
- `PROJECT_LOG.md` — recorded the renderer change, verification step, and rendering assumption.

### Assumptions Made (flag these for review)
- Interpreted the requested “outlined blocks similar to carpets” as very thin translucent full-footprint slabs so the debug markers stay flat against the ground while remaining clearly visible.

### Known Issues / Deferred
- I did not run an in-game visual pass, so the exact opacity/height may still need tuning after you look at it in motion.

### Suggested Next Steps
- Toggle guard debug in-game and verify the yellow target line only appears when a guard has an active target.
- Watch a moving guard path and confirm the green current-node marker, blue route markers, and red destination marker match the live pathfinder state.

## [2026-03-11] — Guard AI Coordinator Refactor
### What Was Implemented
- Centralized guard high-level AI decisions into a dedicated `GuardAiController` that now owns target arbitration, alert ingestion, combat state, rally state, and intent selection.
- Introduced explicit prioritized intents for water recovery, retreat, engagement, rally, owner following, behavior-mode execution, and idle fallback so only one high-level movement/combat intent is active at a time.
- Removed scattered target writes from `GuardEntity` tick/combat flows and routed owner alerts, damage alerts, allied alerts, fallback hostile scans, and raid context through the coordinator pipeline.
- Converted behavior goals into thin intent executors so perimeter, crowd-control, raid, rally, anchor, retreat, air-seeking, and return-to-land behavior no longer re-decide strategy independently.
- Kept bow and melee combat execution split at the goal layer while making target ownership and combat suspension deterministic at the coordinator layer.
- Updated owner-alert event hooks to feed coordinator-facing APIs instead of directly fighting over `setTarget()` from event callbacks.
- Built the mod jar after verification so the packaged artifact matches the AI refactor state.

### Files Modified
- `src/main/java/com/guardvillagers/entity/ai/GuardAiController.java` — new single authority for alerts, target arbitration, intent selection, rally handling, and combat-state transitions.
- `src/main/java/com/guardvillagers/entity/ai/GuardAiIntent.java` — defines the explicit high-level priority model.
- `src/main/java/com/guardvillagers/entity/ai/GuardBehaviorExecutor.java` — isolates which behavior-mode executor may run when behavior intents are active.
- `src/main/java/com/guardvillagers/entity/GuardEntity.java` — removed duplicated tactical state, delegated AI ownership to the controller, and cleaned damage/alert/fallback flows.
- `src/main/java/com/guardvillagers/GuardVillagersMod.java` — owner attack and owner damaged hooks now enqueue coordinator alerts.
- `src/main/java/com/guardvillagers/entity/goal/SeekAirGoal.java` — now runs only when the coordinator selects `SEEK_AIR`.
- `src/main/java/com/guardvillagers/entity/goal/ReturnToLandGoal.java` — now runs only when the coordinator selects `RETURN_TO_LAND`.
- `src/main/java/com/guardvillagers/entity/goal/TacticalRetreatGoal.java` — now executes retreat movement without owning target-clearing strategy.
- `src/main/java/com/guardvillagers/entity/goal/GuardBowAttackGoal.java` — now requires coordinator-owned engage intent before ranged execution.
- `src/main/java/com/guardvillagers/entity/goal/PerimeterPatrolGoal.java` — now acts as a perimeter executor rather than a strategy chooser.
- `src/main/java/com/guardvillagers/entity/goal/CrowdControlGoal.java` — now acts as a crowd-control executor rather than a target selector.
- `src/main/java/com/guardvillagers/entity/goal/RaidTacticsGoal.java` — now executes raid positioning selected by the coordinator.
- `src/main/java/com/guardvillagers/entity/goal/GuardRallyGoal.java` — new thin executor for coordinator-owned rally intent.
- `src/main/java/com/guardvillagers/entity/goal/GuardHomeAnchorGoal.java` — new thin executor for anchor/home-zone behavior.
- `src/main/java/com/guardvillagers/entity/goal/GuardIdleGoal.java` — new thin idle fallback executor.
- `PROJECT_LOG.md` — recorded the AI refactor, assumptions, and verification state.

### Assumptions Made (flag these for review)
- The old urgent-target and primary-target gameplay model should be preserved, but deterministic ordering is preferable to any prior random hostile fallback choice.
- Behavior-specific goals should be fully suspended whenever a higher-priority water-safety, retreat, rally, follow-owner, or combat intent is active, even if some previous edge cases allowed overlap.
- Existing save compatibility is preserved by keeping persistent guard/owner/loadout data untouched and limiting the refactor to transient AI coordination state.

### Known Issues / Deferred
- I did not run an in-game manual pass from the terminal, so gameplay validation for owner alerts, raid support, retreat, and water recovery still needs live confirmation.
- `src/client/java/com/guardvillagers/client/GuardVillagersClient.java` still reports an existing deprecated API note during client compile; this refactor did not change that warning.

### Suggested Next Steps
- Run an in-game AI smoke pass covering owner attack alerts, owner damaged alerts, follow-owner, perimeter, crowd-control, raid, retreat, water recovery, ranged vs melee combat, and zone tethering.
- If any combat feel adjustments are needed, tune coordinator constants and deterministic ranking rules rather than reintroducing target writes into individual goals.

## [2026-03-11] — Repo Local Mod Jar Deployment
### What Was Implemented
- Copied the freshly built `build\libs\guard-villagers-1.0.0.jar` into the `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar`.
- Verified the copied jar matches the build artifact by SHA-256 hash.

### Files Modified
- `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` — replaced with the current build output.
- `PROJECT_LOG.md` — recorded the deployment target and verification result.

### Assumptions Made (flag these for review)
- Deployment target is `%APPDATA%\.minecraft\mods\` (the actual Minecraft instance).

## [2026-03-11] — Debug Overlay Deployment Verification
### What Was Implemented
- Confirmed the guard debug renderer source already contains the pathfinding overlay fix and yellow target-line fix.
- Identified that `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` was stale compared to the current build output.
- Redeployed the current build jar to `%APPDATA%\.minecraft\mods`.
- Verified the build artifact and deployed jar match exactly by SHA-256 hash.

### Files Modified
- `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` — refreshed with the current build output.
- `PROJECT_LOG.md` — recorded the stale-jar diagnosis and redeployment verification.

### Assumptions Made (flag these for review)
- Deployment target is `%APPDATA%\.minecraft\mods\` (the actual Minecraft instance).

### Known Issues / Deferred
- I did not run an in-game visual pass from the terminal after redeploying the jar.

### Suggested Next Steps
- Restart Minecraft completely before retesting so the client loads the refreshed mod jar.
- If the overlay still looks wrong after restart, confirm which launcher profile and mods directory the game instance is using.

## [2026-03-11] — Guard AI De-Clumping And Death Message Fix
### What Was Implemented
- Fixed purchased-guard spawning so hired guards now use the resolved safe spawn position instead of the player’s raw `X/Z`, and added horizontal nearby-column spawn searching for existing guard spawn flows.
- Added deterministic guard movement slot resolution so follow-owner, crowd-control, home-anchor, rally, retreat, and raid-center movement stop collapsing entire groups onto one exact block.
- Updated guard navigation to distinguish static anchor routing from dynamic chase/follow routing, disable squad-route caching for dynamic movement, and add repath hysteresis plus local crowd-jam recovery.
- Relaxed combat stuck/lost-sight suppression when guards are jammed by allied guards so large groups do not snap off targets just because they are body-blocking each other.
- Changed owner death notifications to use the death `DamageSource` message and still respect `showDeathMessages`.
- Built the mod jar and redeployed it to both `%APPDATA%\.minecraft\mods` folder and `%APPDATA%\.minecraft\mods`.

### Files Modified
- `src/main/java/com/guardvillagers/GuardVillagersMod.java` — fixed purchased spawn placement and routed existing guard spawns through nearby horizontal spawn search.
- `src/main/java/com/guardvillagers/entity/GuardEntity.java` — added movement-slot helpers, initial anti-cram spread, distributed retreat targets, and detailed owner death messages.
- `src/main/java/com/guardvillagers/entity/ai/GuardAiController.java` — stopped crowd jams from being treated as target-loss/stuck failures.
- `src/main/java/com/guardvillagers/entity/ai/GuardMovementSlotResolver.java` — new shared slotting helper for distributed follow/anchor/chase targets.
- `src/main/java/com/guardvillagers/entity/goal/*` — rewired follow, crowd, anchor, rally, retreat, raid, and bow movement to use distributed targets instead of single-point collapse.
- `src/main/java/com/guardvillagers/navigation/GuardNavigation.java` — added dynamic-vs-static routing behavior, anti-thrash move gating, and crowd-jam recovery.
- `src/main/java/com/guardvillagers/navigation/SquadRouteCache.java` — tightened static-route cache validity and removed broad drift assumptions.
- `src/main/java/com/guardvillagers/village/VillageManagerHandler.java` — switched village spawn usage to the nearby spawn search helper.
- `PROJECT_LOG.md` — recorded the implementation, assumptions, and deployment verification.

### Assumptions Made (flag these for review)
- Dynamic combat/follow movement should prefer fresher per-guard paths over squad path cache reuse, even if that slightly increases path recomputation.
- Owner death messages should remain owner-only and use direct damage-source wording even if that differs from the previous generic `DamageTracker` output.
- A short first-tick spread correction for newly created guards is acceptable for command/shop/manual burst spawns because it only affects immediately spawned entities.

### Known Issues / Deferred
- I did not run an in-game behavior pass from the terminal, so live tuning for spacing radius, repath cadence, and crowd-recovery feel may still need adjustment.
- `src/client/java/com/guardvillagers/client/GuardVillagersClient.java` still reports the existing deprecated API note during client compile; this task did not change that warning.

### Suggested Next Steps
- Smoke-test large owned groups for spawn spread, follow-owner cohesion, combat pursuit, and village crowd-control behavior in a real world.
- If any clumping remains, tune slot spacing and crowd-recovery distance before changing target arbitration again.

## [2026-03-11] — Guard Jar Rebuild And Client Launch
### What Was Implemented
- Rebuilt the mod jar with the current guard AI and death-message changes.
- Replaced the `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` and `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` with the rebuilt artifact.
- Verified the build artifact and both deployed jars match by SHA-256 hash.
- Launched the Fabric client via `gradlew runClient`.

### Files Modified
- `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` — refreshed with the current build output.
- `PROJECT_LOG.md` — recorded the rebuild, deployment, and client launch.

### Assumptions Made (flag these for review)
- Treated the workspace `.minecraft\mods` folder as the intended primary test instance and refreshed `%APPDATA%` as a secondary safeguard.

### Known Issues / Deferred
- I launched the client process but did not interact with the game or verify world behavior from here.

### Suggested Next Steps
- Use the launched client to validate 64-guard spawn spread, follow-owner movement, target pursuit, and detailed death-message output in-game.

## [2026-03-11] — Follow Slot Grounding Fix
### What Was Implemented
- Replaced the shared ground-slot helper’s heightmap-top lookup with local spawn-safe grounding so slot targets stay near the intended vertical level instead of snapping to unrelated surface tops.
- Updated follow-owner slots to target grounded reachable positions around the owner, and updated combat-approach slots to ground against the target’s local level before pathing.
- Rebuilt the jar and redeployed it to `%APPDATA%\.minecraft\mods`.

### Files Modified
- `src/main/java/com/guardvillagers/entity/ai/GuardMovementSlotResolver.java` — changed ground slot resolution to use local nearby spawn validation.
- `src/main/java/com/guardvillagers/entity/GuardEntity.java` — follow and combat slot helpers now return grounded reachable positions.
- `%APPDATA%\.minecraft\mods\guard-villagers-1.0.0.jar` — refreshed with the rebuilt jar containing the follow-slot fix.
- `PROJECT_LOG.md` — recorded the follow-slot grounding correction.

### Assumptions Made (flag these for review)
- The cliff-follow failure was primarily caused by unreachable slot targets near the player rather than by a remaining pure navigation-mesh bug.

### Known Issues / Deferred
- The currently running Minecraft client still has the old jar loaded until it is restarted.

### Suggested Next Steps
- Restart Minecraft completely and re-run the 64-guard `/guards follow` cliff test against the new jar.

## [2026-03-11] — Guard AI Audit
### What Was Implemented
- Audited the current guard AI control flow across `GuardEntity`, `GuardAiController`, `GuardNavigation`, movement slot resolution, behavior goals, command entrypoints, and village spawn/anchor logic.
- Produced a prioritized issue register focused on stranded follow behavior, crowd deadlocks, vertical grounding errors, behavior-layer routing bypasses, and stale state transitions.
- Classified findings as confirmed code issues, confirmed runtime issues, or suspected risks so future fixes can target root causes instead of symptoms.

### Files Modified
- `PROJECT_LOG.md` — recorded the AI audit findings and next-step recommendations.

### Assumptions Made (flag these for review)
- Treated the user-reported 64-guard `/guards follow` cliff failure, village clumping, and oscillation as confirmed runtime symptoms even though I did not drive the Minecraft client interactively from the terminal during this audit.
- Treated the existing in-repo AI changes as the current baseline under review rather than reverting to any earlier architecture.

### Known Issues / Deferred
- This task did not include code changes or runtime instrumentation additions, so the audit stops at root-cause identification and fix direction.
- A few lower-confidence risks still need live reproduction to rank them correctly against the confirmed pathing blockers.

### Suggested Next Steps
- Fix follow-slot scaling and grouping first so large owned groups stop sharing one global ring around the owner.
- Replace remaining heightmap-top and raw spawn-grounding shortcuts in crowd recovery and patrol movement with path-safe local grounding.
- Align all behavior goals on the same navigation contract and retest with 1, 8, 32, and 64 guards across cliffs, ramps, caves, village centers, and water transitions.

## [2026-03-11] — Guard AI Follow And Pathing Fix Pass
### What Was Implemented
- Split movement slotting away from the broad “same owner equals same group” rule by introducing movement-group partitioning for owned guards, so large owned blobs no longer share one massive slot ring.
- Added catch-up follow slot resolution that trails from the owner-facing side when guards are far away or on a very different elevation, which is intended to help large groups acquire the same climb path instead of orbiting unreachable side slots.
- Allowed `followOverride` guards to ignore home-zone target restrictions so `/guards follow` can keep combat and pursuit active away from the saved home anchor.
- Changed crowd-jam detection and crowd-recovery grounding to use movement groups plus nearby safe grounding instead of heightmap-top probing.
- Moved perimeter patrol back onto the guard navigation helpers and grounded patrol points through the shared movement-slot resolver instead of raw vanilla navigation.
- Cleared stale combat state when zoning a guard with the whistle so home/zone transitions do not inherit old chase state.
- Updated guard spawn vertical search to prefer the nearest safe vertical slot instead of biasing downward to the lowest valid block.
- Rebuilt the mod and refreshed both the repo-local and `%APPDATA%` installed jars.

### Files Modified
- `src/main/java/com/guardvillagers/entity/ai/GuardMovementSlotResolver.java` — added movement-group partitioning, cohort-based slot spreading, and follow catch-up slot resolution.
- `src/main/java/com/guardvillagers/entity/GuardEntity.java` — added movement-group identity, follow catch-up selection, and follow-override zone bypass.
- `src/main/java/com/guardvillagers/navigation/GuardNavigation.java` — changed crowd detection/recovery to use movement groups and safe grounded recovery targets.
- `src/main/java/com/guardvillagers/entity/goal/PerimeterPatrolGoal.java` — routed patrol movement through grounded guard navigation instead of raw heightmap-top movement.
- `src/main/java/com/guardvillagers/item/GuardWhistleItem.java` — cleared combat state on zone/home whistle commands.
- `src/main/java/com/guardvillagers/GuardVillagersMod.java` — changed guard spawn vertical search to nearest-safe preference.
- `%APPDATA%/.minecraft/mods/guard-villagers-1.0.0.jar` — refreshed with the rebuilt jar.
- `PROJECT_LOG.md` — recorded this fix pass and verification results.

### Assumptions Made (flag these for review)
- Partitioning unassigned owned guards by `groupColumn` is an acceptable intermediate way to stop one-owner swarms from sharing a single slot cohort without changing save format or tactics UI semantics.
- For large catch-up cases, approaching from the owner-facing side is preferable to using fully symmetric ring slots around the owner.
- Treating one very close allied blocker as sufficient crowding is acceptable because the recovery only triggers after a stall window rather than on every tick.

### Known Issues / Deferred
- I did not run an in-game validation pass after the rebuild, so the remaining question is behavioral feel rather than compile/build correctness.
- Water/air-seeking and ranged-goal continuation risks from the audit were not changed in this pass because they were not primary blockers for the reported 64-guard follow failure.
- `src/client/java/com/guardvillagers/client/GuardVillagersClient.java` still emits the existing deprecated API note during client compile.

### Suggested Next Steps
- Fully restart Minecraft and retest the 64-guard `/guards follow` cliff scenario first.
- If large groups still strand on narrow climbs, the next fix should be path-aware queueing on the owner approach vector rather than broader ring/cohort tuning.
- Re-run the village-house clustering scenarios to confirm perimeter patrol and crowd recovery are no longer sending guards to bad vertical surfaces.

## [2026-03-11] — Security Review Audit
### What Was Implemented
- Executed a repo-grounded security and exploit audit across commands, item/entity interaction, screen handlers, persistent-state parsing, periodic server tick work, client file I/O, and S2C payload handling.
- Produced a prioritized findings report with concrete exploit paths, exact code anchors, and minimal remediation guidance.
- Explicitly closed out non-findings for client-compromise surfaces such as remote download, process execution, native loading, and custom C2S packet handling.

### Files Modified
- `guard-villagers-security-review.md` — added the security review report with prioritized findings and fix directions.
- `PROJECT_LOG.md` — recorded the audit deliverable and remaining follow-up.

### Assumptions Made (flag these for review)
- Ranked severity using a public-server-first threat model while still checking client-side safety surfaces.
- Treated this task as a static code audit only; I did not drive a live Minecraft client/server exploit reproduction loop during this pass.

### Known Issues / Deferred
- No remediations were applied in this task; the repo still contains the findings documented in the report.
- Runtime exploit validation, load testing, and negative-permission testing still need an interactive game session if the user wants proof-of-exploit evidence beyond static code review.

### Suggested Next Steps
- Patch `/guards debug` to require operator permission and re-audit the debug sync visibility rules.
- Remove or contain the full-world ownership scan fallback before exposing the mod to untrusted public players.
- Add hard limits and cleanup rules for tactics groups and persistent player/village state, then retest with high-churn public-server scenarios.

