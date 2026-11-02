# Dessert Client 0.4.0

Fabric utility client for Minecraft Java 1.21.11. Requires Java 21, Fabric Loader 0.18.1+ and Fabric API. Mod Menu is optional.

Press **Right Shift** (rebindable in Minecraft Controls) to open the control center. Escape closes it without pausing your game.

- **Overview:** scanner, spawner and waypoint counts with quick actions.
- **Modules:** search and toggle SUS Chunk Scanner, Spawner ESP and Freecam.
- **Findings:** paginated chunk clues, heuristic scores and copyable block coordinates. Scores are clues, not proof of a base.
- **Waypoints:** name and save the current location, copy coordinates or delete bookmarks. Bookmarks are local and separated by server/singleplayer save and dimension. Maximum 200 total.
- **Tools:** copy XYZ, convert Overworld/Nether X/Z using the 8:1 ratio, or disable all modules.
- **Settings:** notification threshold, ESP range/color, amethyst filter, camera speed, chunk highlights and through-wall rendering. Numeric settings cycle when clicked. Highlights and through-wall preferences persist; notifications retain their existing configuration. ESP range/color, camera speed and amethyst filter also persist. Module activation resets each launch.

Optional shortcuts for scanner, ESP, Freecam, saving a waypoint and disabling all modules are available in Minecraft Controls. They start unbound so they do not conflict with other mods.

Freecam: WASD moves, Space rises, Left Shift descends; disable it to return. It stops on death, disconnect or world change. Scanners inspect already-loaded chunks only.

Waypoints: `config/dessert-waypoints.json`. Render preferences: `config/dessert-render.properties`. Notifications: `config/dessert-client.properties`.

## Build

Use Java 21 and Gradle 9.2: `gradle clean build`. Install the remapped JAR from `build/libs` (not the sources JAR). GitHub Actions builds pull requests and the overhaul branch; only main publishes releases.
