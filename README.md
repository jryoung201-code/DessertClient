# Singleplayer PvP Bot — Carpet Edition v0.2.0

A **Fabric 1.21.11** mod that runs only in singleplayer and spawns one real Carpet fake-player opponent, rather than a mob NPC.

## Requirements

- Minecraft Java 1.21.11
- Java 21
- Fabric Loader
- Fabric API for 1.21.11
- **Carpet Mod 1.4.194** for 1.21.11 (required)

Install the PvP Bot JAR and Carpet JAR into your `.minecraft/mods` directory.

## Commands

- `/pvpbot sword`
- `/pvpbot mace`
- `/pvpbot smp`
- `/pvpbot elytra_mace`
- `/pvpbot spear_mace`
- `/pvpbot kit` — give yourself the selected duel kit
- `/pvpbot difficulty easy|normal|hard`
- `/pvpbot reset` — restart the previous duel
- `/pvpbot stop` — disconnect the bot
- `/pvpbot status` — duel status and score

## How it works

Uses `carpet.patches.EntityPlayerMPFake.createFake`, `ServerPlayerInterface.getActionPack()`, and `EntityPlayerActionPack` for movement, aiming, jumping, and attacking.
The opponent is a real server-side player entity controlled locally.
Carpet's spawn is asynchronous, so the bot may take a moment to appear.

## Limitations

- Practice is intended for a flat, open arena, not obstacle-aware pathfinding.
- Elytra mace uses an upward launch approximation with an equipped elytra, **not** fully automated rocket flight.
- The bot has simple combat strategy; this is not a highly skilled PvP neural agent.
- The mod is not designed for dedicated servers or multiplayer.

This branch deliberately leaves the DessertClient main branch untouched.
