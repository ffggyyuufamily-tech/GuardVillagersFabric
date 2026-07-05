# Guard Villagers Mod - Development Guidelines & History

## [2026-03-09] Guard AI, Pathfinding & Visuals Overhaul
**Prompt:** "Refining Guard AI... Completing the combat AI system, including dual-target management, scoring, and a "peel" mechanic... Implementing swimming AI (SeekAirGoal)... owner defense mechanisms... death notification system... Fixing visual aspects of the guard entity model... Optimizing pathfinding and navigation."

**Changes Made:**
1. **Combat AI Overhaul:**
   - Modified `GuardEntity.java` to replace `priorityTarget` with `mainTarget` and `urgentTarget` (weighted dual-target system). Target scoring logic (0-100) added for dynamic retargeting.
   - Removed `ActiveTargetGoal` for guards to stop them from proactively fighting each other.
   - Peel Mechanic: `max(1, min(10, floor(groupSize * 0.30)))` caps the number of guards peeling off the main target to fight an urgent threat.
2. **Owner Defense Mechanisms:**
   - Added `ServerLivingEntityEvents.AFTER_DAMAGE` hook in `GuardVillagersMod.java` to trigger owner defense properly across both owner-as-victim and owner-as-attacker events, bypassing idle gates.
3. **Swimming AI:**
   - Added `SeekAirGoal.java` to make guards surface when sinking (`getAir() < 60`). Introduced `guard.suspendCombat()` to cleanly pause and resume their active targeting resolver.
4. **Death Notifications:**
   - Overrode `onDeath` in `GuardEntity.java` to dispatch a wolf-style death message to the player owner.
5. **Visual Fixes:**
   - Adjusted `GuardEntityModel.java` geometry (arms, hat, nose) to match standard zombie/player 64x64 biped model dimensions.
   - Updated `GuardEntityRenderer.java`'s `getArmPose` to properly apply `BOW_AND_ARROW` or `BLOCK` states, while relying on vanilla `BipedEntityModel` to read `handSwingProgress` for melee swings.
   - Generated a proper 64x64 `guard_villager.png` default skin.
6. **Pathfinding Optimizations:**
   - Created `GuardNavigation.java` for stall detection (recovering when stalled < 0.25 blocks for 40 ticks).
   - Created `SquadRouteCache.java` which securely caches squad routes per quantized target block location, returning defensive `Path` copies to prevent concurrent index mutation.

**Lessons Learned & Future Considerations:**
- MC 1.21.11 `Path` objects are inherently mutable (`currentNodeIndex`). They must be defensively copied manually when caching/sharing paths across entities.
- Bipedal combat models dynamically use `handSwingProgress` automatically if arms are not manually zeroed in `setAngles()`. Attacking poses like `ATTACKING` are absent and unnecessary in 1.21.11.
