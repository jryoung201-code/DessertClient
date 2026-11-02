# Dessert Client 0.5.0

Minecraft Java 1.21.11 / Fabric Loader 0.18.1+ / Fabric API / Java 21. Mod Menu is optional. Replace the old client JAR; press **Right Shift** to open the control center.

## Rebuilt SUS Chunk Finder

The finder inspects only chunks already in your client cache. Work is spread across ticks (up to 2,048 positions and an approximately 2 ms budget per tick), empty sections are skipped, and loaded chunks become eligible for rescan after 30 seconds. A large scan area takes longer to revisit. Changing worlds or disconnecting clears results. Chunk unloads remove cached evidence.

Results use **evidence points**, not percentages or base probabilities. Default flag threshold: **3 points**. Default notification threshold: **6 points**. Alerts respect both thresholds and have a five-minute cooldown per chunk. Adjust thresholds under Settings in 0.5-point steps. Existing 0.4 notification settings are kept in their old file; 0.5 uses a separate configuration with new defaults.

| Evidence | Points / cap |
| --- | --- |
| Chest / trapped chest | 0.86 each |
| Barrel | 0.64 each |
| Hopper / dispenser | 0.12 each |
| Dropper | 0.08 each |
| All storage combined | Maximum 12 |
| Redstone machinery | 0.30 each, maximum 5 |
| Workstations | 0.25 each, maximum 3 |
| Selected glass / finished stone blocks | 0.04 each, maximum 2 |
| Sideways deepslate | 0.01 each, maximum 0.5 |
| Small enclosed rectangular room | +1 once per chunk |
| Spawner | 0.25 each, maximum 1 (may occur naturally) |
| At least three evidence categories | +1 |
| At least eight mossy cobblestone / cobweb / infested stone-brick blocks | Halve total as possible natural structure context |

The room check detects only fully enclosed 3x3 spaces two blocks high, with a solid floor, ceiling and perimeter, wholly inside a chunk. It does not detect every player-built room. Natural structure context is a heuristic and can also occur in player builds. Single chests, spawners, cobblestone and natural geodes do not flag a chunk on their own at default settings.

**Findings → Details** shows the full point breakdown, XYZ, distance and snapshot age. Copy coordinates, refresh the list or request a fresh scan. Amber / orange / red outlines represent increasing points and enclose detected evidence. Detection does not prove who placed blocks or whether anyone is present.

## Amethyst

A separate Amethyst page lists chunk-local groups with at least three budding-amethyst/bud/cluster blocks, approximate bounding-box centers and observed growth-stage changes since the prior scan. Purple boxes mark amethyst bounds; mint boxes indicate observed growth changes. The Amethyst-only setting hides other finding outlines. Natural amethyst adds **zero base points**.

A first snapshot establishes a baseline. Full reloads reset it. Changes are sampled during rescans and can miss events between samples. Growth does not prove another player is nearby: your own presence and server ticking rules can cause it. No server seed is required. This release does not claim to triangulate another player's location.

## Other features

- Searchable toggles for SUS Chunk Finder, Spawner ESP and Freecam.
- Local named waypoints, separated by server/save and dimension (200 total).
- Copy XYZ, Overworld/Nether portal coordinate conversion, disable-all action.
- Optional unbound keybinds for each module, saving a waypoint and disabling all modules; configure in Minecraft Controls.
- Saved rendering, ESP and camera preferences. Module activation resets each launch.
- Freecam: WASD / Space / Left Shift; stops on death or world change.

Scanner settings: `config/dessert-scanner.properties`. Waypoints: `config/dessert-waypoints.json`. Rendering: `config/dessert-render.properties`.

## Build

Java 21 and Gradle 9.2: `gradle clean build`. This also runs the dependency-free scoring regression checks. Install the remapped JAR from `build/libs`, not the sources JAR. GitHub Actions builds the overhaul branch and pull requests; only main publishes releases.
