# Dessert Client 0.7.1

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
- Optional keybinds for each module, saving a waypoint and disabling all modules; configure in Minecraft Controls. Freecam defaults to F6; other optional shortcuts start unbound.
- Saved rendering, ESP and camera preferences. Module activation resets each launch.
- Freecam: **F6** toggles it. Uses your configured movement / jump / sneak keys; sprint gives a 3x boost. Mouse look controls the camera, movement keys do not control your body, and attack/use/mining are blocked while detached. Camera movement pauses in menus or when unfocused. Stops on death, world change, mounting or another camera taking control. Restores your prior view. Server corrections and physics still apply to your player.

Scanner settings: `config/dessert-scanner.properties`. Waypoints: `config/dessert-waypoints.json`. Rendering: `config/dessert-render.properties`.

## Build

Java 21 and Gradle 9.2: `gradle clean build`. This also runs the dependency-free scoring regression checks. Install the remapped JAR from `build/libs`, not the sources JAR. GitHub Actions builds the overhaul branch and pull requests; only main publishes releases.

## Highlight fixes and custom notifications (0.5.1)

Chunk and spawner outlines now use dedicated render buffers that are explicitly drawn. Geometry is camera-relative to preserve precision at distant coordinates. Existing Highlights, Through Walls and Amethyst-only filters still apply. An above-threshold finding is needed for normal chunk highlights.

Open **Alerts → Test outline (10s)** to draw a mint test cube three blocks east of your player; it works independently of the scanner and highlight switches. Look east to find it. This tests the render path without requiring a suspicious chunk.

Open **Alerts → Customize notifications**. Edit the title/message, choose on-screen cards / chat / both, cycle accent colors, enable sound, set card duration (2–20s) and alert cooldown (10–600s), and press Save. Preview shows the current overlay settings. Supported literal placeholders: `{points}`, `{x}`, `{y}`, `{z}`, `{chunkX}`, `{chunkZ}`. Settings persist in `config/dessert-notifications.properties`. Scanner notification enable/threshold settings remain under Settings. Up to three cards appear in the lower-right corner. F1 hides the HUD. No packet/world-activity inference is added.

## Notification overlay (0.6.1)

The on-screen notification overlay uses rounded cards, an accent badge, slide-in animation and a lifetime bar. Up to three cards stack in your chosen corner without covering the hotbar. Choose top-right (default), top-left, bottom-right or bottom-left under **Alerts → Customize notifications → Appearance**. Text, sound, duration, chat/card mode and cooldown remain configurable and saved.

**Preview** opens a full-screen sample using the actual overlay renderer, including from the title screen. The preview works independently of scanner thresholds and world state. Use its Corner button to compare positions; return and press Save to persist your choice. Chat-only mode does not show cards during normal gameplay; choose on-screen cards or both to see the overlay. F1 hides it during gameplay.

## Desert UI (0.7.0)

The control center now opens on Modules with a warm chocolate palette, orange glow, a drawn sunset/dunes/cactus header and donut branding. Module cards have independent toggles and configuration controls. Selecting a card shows its illustration, description and live state in a right-hand panel on wider layouts. At smaller GUI sizes, the detail panel is hidden and modules/navigation are paginated. Keyboard Tab, Enter and Space work with the native controls.

All three real modules remain: SUS Chunk Finder, Spawner ESP and Freecam. The removed module stays removed. Existing findings, amethyst observations, waypoints, tools, settings and alerts remain accessible.

**HUD** controls the live FPS/ping, coordinates, biome and module list. **Theme** offers Desert sunset (default), Strawberry frosting and Mint chocolate. Choices persist in `config/dessert-ui.properties`. Notification cards match the selected theme by default; disable Match client theme in alert appearance settings to choose a custom notification accent. The right detail panel appears only when at least 480 logical GUI pixels of content width are available.

## Highlight fix (0.7.1)

World outlines draw at the end of the main world pass. Spawner ESP refreshes nearby chunks every five ticks, while keeping a bounded background sweep for more distant loaded chunks. Removed or replaced spawners are cleared on refresh.
