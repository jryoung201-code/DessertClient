# DonutSMP Client

A client-side utility mod prototype for **Minecraft Java 1.21.11** using Fabric.

## Current prototype
- Press **Right Shift** in-game to open the blue-themed DonutSMP Client screen.
- Sidebar page buttons for Dashboard, SUS Chunk Scanner, Money Tracker, Auction House, and Waypoints.
- Scanner controls for enabling/disabling the scanner, chunk highlighting, and through-wall highlighting.
- Smooth suspicion color scale: red (1%), orange (50%), yellow (75%), green (100%).
- No cheat features. The initial scanner panel is a UI prototype and does not yet perform live chunk analysis.

## Build
Requirements: JDK 21 and Gradle.

```sh
gradle build
```

The mod jar will be in `build/libs/`. Install Fabric Loader for Minecraft 1.21.11 and put the jar in your `mods` folder. Fabric API is required.

## Next steps
Implement safe, client-available chunk analysis, persistent local SUS chunk records, and render-based chunk outlines. The client cannot inspect unloaded blocks that the server has not sent.
