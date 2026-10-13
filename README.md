# Dessert Client 🍩

**Dessert Client — DonutSMP Edition** is a client-side utility mod for **Minecraft Java 1.21.11** built with Fabric. It uses a sweet pink, cream, and chocolate visual theme.

## Current prototype
- Press **Right Shift** in-game to open the Dessert Client screen.
- Pink-themed interface with a sidebar (Dashboard, SUS Chunk Scanner, Money Tracker, Auction House, Waypoints, Player, World, Render, Utilities, Configs), rounded panels, and a donut logo. Only the scanner page has content so far; the others show a "coming soon" card.
- Scanner page: status pill, scanner status card, suspicion scale, switch-style toggles (scanner, chunk highlighting, through-wall highlighting), and a recent-chunks table. The table row is a labelled sample until scanning exists.
- Right-hand info box (FPS, ping, server, XYZ, nether/overworld coordinates, facing, biome, speed) and a keybinds box, shown when the window is wide enough. TPS shows `--` because the client cannot measure it. Only "Open GUI" reflects a real keybind; the other keybinds shown are placeholders.
- Smooth suspicion color scale: red, orange, yellow, and green.
- No cheat features. The scanner panel is a UI prototype and does not yet perform live chunk analysis.

## Build
Requirements: JDK 21 and Gradle.

```sh
gradle build
```

The mod JAR will be in `build/libs/`. Install Fabric Loader for Minecraft 1.21.11 and put the JAR in your `mods` folder. Fabric API is required; Mod Menu integration is included.

## Next steps
Implement safe, client-available chunk analysis, persistent local SUS chunk records, and render-based chunk outlines. The client cannot inspect unloaded blocks that the server has not sent.

## Branding
- Name: **Dessert Client**
- Theme: strawberry pink, cream, chocolate brown
- Edition: DonutSMP
