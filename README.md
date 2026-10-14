# Dessert Client 🍩

**Dessert Client — DonutSMP Edition** is a client-side utility mod for **Minecraft Java 1.21.11** built with Fabric. It uses a sweet pink, cream, and chocolate visual theme.

## Current prototype — v0.3.4b
- Press **Right Shift** in-game to open the Dessert Client screen.
- Pink-themed interface with a sidebar (Dashboard, SUS Chunk Scanner, Money Tracker, Auction House, Waypoints, Player, World, Render, Utilities, Configs), rounded panels, and a donut logo. Only the scanner page has content so far; the others show a "coming soon" card.
- Scanner page: status pill, scanner status card, suspicion scale, switch-style toggles (scanner, chunk highlighting, through-wall highlighting), and a recent-chunks table.
- **Spawner ESP:** separate UI page with an on/off toggle, adjustable 2–16 chunk render radius, and cycling outline colors. Outlines use the secondary block-outline render layer for through-wall visibility. It checks block entities only in already-loaded chunks and draws a capped number of nearby outlines.
- Right-hand info box (FPS, ping, server, XYZ, nether/overworld coordinates, facing, biome, speed) and a keybinds box, shown when the window is wide enough. TPS shows `--` because the client cannot measure it. Only "Open GUI" reflects a real keybind; the other keybinds shown are placeholders.
- Smooth suspicion color scale: red, orange, yellow, and green.
- Spawner ESP is client-side and never requests chunk loads. Scanning is incremental and bounded to a small number of loaded chunks per scan tick. Results clear when the module is disabled, the world changes, or chunks unload.
- The SUS scanner remains a separate prototype; its full live analysis is still under development.

## Build
Requirements: JDK 21 and Gradle.

```sh
gradle build
```

The mod JAR will be in `build/libs/`. Install Fabric Loader for Minecraft 1.21.11 and put the JAR in your `mods` folder. Fabric API is required; Mod Menu integration is included.

## Notes
The client cannot inspect blocks in chunks the server has not sent. Spawner ESP therefore only detects mob spawners present in currently loaded client chunks; it is not a guarantee that every spawner in the area has been found.

## Branding
- Name: **Dessert Client**
- Theme: strawberry pink, cream, chocolate brown
- Edition: DonutSMP
