# Guard Villagers Security Review

Date: 2026-03-11
Repository: `guard-villagers`

## Executive Summary

This audit did not find any repo-local path to remote code execution, remote file download, native library loading, process execution, or user-controlled reflection. The highest-risk issues are server-side: an authorization gap on `/guards debug`, a cold-index fallback that can force full-world entity scans from public actions, and multiple unbounded persistent-state growth paths that make save-bloat and operational DoS realistic on public servers.

## High Severity

### GV-001: `/guards debug` is not permission-gated, enabling non-ops to access debug data and trigger extra server work

- Impact: Any player can enable an operator-documented debug mode, receive path/target overlays for nearby natural guards, and force the server to do extra debug snapshot work every 5 ticks.
- Evidence:
  - `src/main/java/com/guardvillagers/GuardVillagersMod.java:390-395` registers the `debug` subcommand without `.requires(GuardVillagersMod::hasOperatorPermission)`.
  - `src/main/java/com/guardvillagers/GuardVillagersMod.java:1062-1108` pushes debug sync to every player who has enabled it and includes unowned guards via `entity.getOwnerUuid() == null || entity.isOwnedBy(playerId)`.
- Why this matters:
  - The README documents debug as operator-only, so the current behavior is a privilege regression.
  - The sync path serializes path nodes and target entity IDs, which is operationally sensitive even if it is not secret user data.
  - On a public server, unrestricted access makes the debug workload reachable by any player instead of a trusted admin only.
- Reproduction:
  1. Join as a non-operator.
  2. Run `/guards debug` or `/guards debug 64`.
  3. Observe that the command succeeds and nearby debug data begins syncing.
- Fix direction:
  - Add `.requires(GuardVillagersMod::hasOperatorPermission)` to the `debug` command branch.
  - Restrict debug sync to explicitly authorized players only.
  - Consider hiding unowned/natural guard debug data from non-admin viewers even after permission gating.

### GV-002: Public player actions can force cold-index full-world guard scans

- Impact: After restart, stale index state, or index misses, public commands and combat hooks can fall back to scanning every world for all owned guards, which is an avoidable DoS path.
- Evidence:
  - `src/main/java/com/guardvillagers/GuardOwnershipIndex.java:62-76` and `src/main/java/com/guardvillagers/GuardOwnershipIndex.java:79-93` fall back to `scanOwnedGuards(...)` when the in-memory index is empty.
  - `src/main/java/com/guardvillagers/GuardOwnershipIndex.java:139-148` scans each world with a `Box` spanning `-30_000_000` to `30_000_000`.
  - Public call sites include `syncOwnedGuardRoster(...)` in `src/main/java/com/guardvillagers/GuardVillagersMod.java:410-427`, `/guards count` in `src/main/java/com/guardvillagers/GuardVillagersMod.java:366-372`, and other player-triggered flows such as `/guards stay`, `/guards follow`, `/guards groups ...`, and attack-alert hooks.
- Why this matters:
  - The first call after a restart is enough to force the scan path.
  - The scan surface is reachable by ordinary players, not only admins.
  - The work scales with loaded worlds and total guard count, which makes abuse materially worse on larger servers.
- Reproduction:
  1. Restart the server so ownership indexes are cold.
  2. Have one or more players spam `/guards count`, `/guards tactics`, or `/guards follow`.
  3. Observe repeated fallback scanning before the index is fully repopulated.
- Fix direction:
  - Replace full-world fallback scans with a persisted owner-to-guard index or an eager warmup pass.
  - Throttle or cache public roster/count/tactics refreshes.
  - Keep combat alert paths off any fallback that scans all worlds.

## Medium Severity

### GV-003: Group creation is unbounded and diverges from the roster packet cap

- Impact: A player can grow tactics state without limit, while the roster sync only transmits the first 256 groups, creating silent server/client divergence plus persistent save bloat.
- Evidence:
  - `src/main/java/com/guardvillagers/GuardVillagersMod.java:618-628` exposes `/guards groups add` without any server-side cap.
  - `src/main/java/com/guardvillagers/data/GuardTacticsState.java:228-261` lets `groupNames` grow indefinitely.
  - `src/main/java/com/guardvillagers/network/GuardRosterSyncPayload.java:14-16` caps packet groups at `256`, and `src/main/java/com/guardvillagers/network/GuardRosterSyncPayload.java:33-44` truncates writes to that limit.
- Why this matters:
  - Persistent growth is player-reachable from a normal command.
  - Once group count exceeds the packet cap, the server and client no longer have the same view of tactics data.
  - Repeated group creation also inflates the work done during roster sync and save serialization.
- Reproduction:
  1. Repeatedly run `/guards groups add`.
  2. Observe that server-side tactics state continues to grow past 256 groups.
  3. Open the tactics/groups UI and note that only the first 256 groups are present in synced payloads.
- Fix direction:
  - Enforce a single hard max group count on the server that matches the roster packet and UI.
  - Reject or no-op additional `groups add` calls beyond the cap.

### GV-004: Multiple persistent states create permanent entries on first touch and never evict inactive data

- Impact: Public servers can accumulate unbounded player- and village-keyed state from alt accounts, one-off visitors, and generated villages, leading to save-file growth and longer serialization times.
- Evidence:
  - `src/main/java/com/guardvillagers/data/GuardReputationState.java:52-59` persists a default reputation entry on first read via `ensureTracked(...)`.
  - `src/main/java/com/guardvillagers/data/GuardUpgradeState.java:43-47` and `src/main/java/com/guardvillagers/data/GuardDebugState.java:43-63` similarly create tracked entries keyed by UUID.
  - `src/main/java/com/guardvillagers/data/GuardVillageState.java:36-46` creates village records but provides no cleanup path for abandoned villages.
  - `src/main/java/com/guardvillagers/data/GuardReputationState.java:25`, `src/main/java/com/guardvillagers/data/GuardUpgradeState.java:17`, `src/main/java/com/guardvillagers/data/GuardDebugState.java:16`, `src/main/java/com/guardvillagers/data/GuardTacticsState.java:24`, and `src/main/java/com/guardvillagers/data/GuardVillageState.java:14` all use unbounded map codecs.
- Why this matters:
  - Public-server churn turns “defaults” into permanent serialized entries.
  - Village records accumulate as the world explores and changes.
  - The issue is availability-focused rather than confidentiality-focused, but it directly affects operational safety.
- Reproduction:
  1. Join with many fresh UUIDs or script repeated first-touch actions.
  2. Trigger reputation/tactics/debug/upgrades once per UUID.
  3. Inspect the saved state files and observe monotonic growth with no retirement path.
- Fix direction:
  - Do not persist default entries on read-only lookups.
  - Add cleanup/compaction rules for inactive players and stale village IDs.
  - Bound or periodically compact persistent maps before writeback.

## Low Severity

### GV-005: Client tactics storage is read as an unbounded local string

- Impact: A corrupted or intentionally oversized local config file can spike memory or stall startup, but this is local-file abuse, not a remote compromise path.
- Evidence:
  - `src/client/java/com/guardvillagers/client/ClientTacticsDataStore.java:262-349` loads the full file with `Files.readString(...)` and parses it in-memory with Gson.
- Why this matters:
  - It is not remotely reachable from the repo as written.
  - It is still worth bounding because the file grows with discovered chunks and world data.
- Fix direction:
  - Add a file-size guard before reading.
  - Reject or rotate obviously oversized state files.

## Closed-Out Surfaces

- No custom C2S Fabric payloads were found; the primary client-to-server trust boundaries are commands, screen handlers, item use, and entity interaction.
- No repo-local URL fetch, socket client, `HttpClient`, `ProcessBuilder`, `Runtime.exec`, `ObjectInputStream`, or native loader path was found in the mod sources.
- The only explicit `Class.forName(...)` usage is a fixed startup preload of known state classes in `src/main/java/com/guardvillagers/GuardVillagersMod.java:163-180`; it is not driven by user input.

## Recommended Next Steps

1. Fix GV-001 first by permission-gating `/guards debug` and re-checking who can receive debug sync.
2. Fix GV-002 next by removing the public full-world scan fallback or throttling every public entrypoint that can hit it.
3. Add hard caps and cleanup rules for group count and persistent UUID/village state.
4. After fixes land, retest with public-player abuse scenarios: non-op command access, cold-start command spam, repeated `groups add`, and fresh-UUID churn.
