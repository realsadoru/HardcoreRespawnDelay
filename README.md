# HardcoreRespawnDelay

A Paper plugin that replaces the permadeath behavior of Minecraft's Hardcore mode with a **timed respawn delay**.

When a player dies, they see the normal "You Died!" screen and click **Respawn** as usual. Instead of returning to the world immediately, they're placed into **spectator mode** with a countdown shown on screen (a large title in the center + a persistent action bar timer). Once the timer runs out (5 minutes by default), the player is automatically returned to survival mode at the world spawn.

No permaban, no kicking the player from the server — just a meaningful death penalty.

## Table of Contents

- [Why not just use vanilla Hardcore mode?](#why-not-just-use-vanilla-hardcore-mode)
- [Requirements](#requirements)
- [Installation](#installation)
- [Building from source](#building-from-source)
- [Configuration](#configuration)
- [Commands](#commands)
- [Permissions](#permissions)
- [How it works](#how-it-works)
- [License](#license)

## Why not just use vanilla Hardcore mode?

Vanilla Hardcore mode is handled at the game-client/server-core level, not through events that plugins can hook into in time. When `hardcore=true` is set in `server.properties`, a player's death **immediately and permanently bans them from the server** (locked into spectator mode) before any plugin has a chance to intervene.

**So instead:**
- Keep your server on `gamemode=survival` and `hardcore=false` in `server.properties`
- Let this plugin enforce the "one life, then a real consequence" rule — the same spirit as Hardcore mode, minus the permanent ban

## Requirements

- [Paper](https://papermc.io/) (or a Paper fork, e.g. Purpur) **26.2** or newer
- Java 25+ (Paper 26.x server jars are compiled for JDK 25)

## Installation

1. Download the built `.jar` (see [Building from source](#building-from-source) if you don't have one yet)
2. Drop it into your server's `plugins/` folder
3. Restart the server
4. A default `plugins/HardcoreRespawnDelay/config.yml` will be generated on first run
5. Edit the config to your liking, then run `/hrd reload`

## Building from source

### Option 1: Locally, with Maven + JDK 25 installed

```bash
git clone <this-repo-url>
cd hardcore-respawn-delay
mvn clean package
```

The built jar will be at `target/HardcoreRespawnDelay-1.0.0.jar`.

### Option 2: GitHub Actions (no local Maven/JDK needed)

This repo already includes a workflow at `.github/workflows/build.yml`. After pushing to GitHub:

1. Go to the **Actions** tab of your repository
2. Open the latest workflow run
3. Download the `HardcoreRespawnDelay` artifact — it contains the built `.jar`

### Option 3: Any cloud IDE with internet access

Any environment with JDK 25 and access to `repo.papermc.io` (e.g. Gitpod, Replit) works — just run `mvn clean package` as above.

## Configuration

Default `config.yml`:

```yaml
enabled: true
respawn-delay-seconds: 300
use-spectator-mode: true
show-title-timer: true
title-refresh-interval-seconds: 1
show-actionbar-timer: true
play-sound-on-respawn-ready: true
sound-on-respawn-ready: "ENTITY_PLAYER_LEVELUP"
broadcast-death: true
broadcast-return: true
```

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Master on/off switch for the whole mechanic |
| `respawn-delay-seconds` | `300` | Wait time in seconds (300 = 5 minutes) |
| `use-spectator-mode` | `true` | Whether the waiting player is put into spectator mode |
| `show-title-timer` | `true` | Show a large countdown title in the center of the screen |
| `show-actionbar-timer` | `true` | Show a persistent countdown in the action bar |
| `play-sound-on-respawn-ready` | `true` | Play a sound when the wait is over |
| `broadcast-death` / `broadcast-return` | `true` | Server-wide chat announcements |

All strings under `messages:` in the config are fully customizable, support `&`-based color codes (e.g. `&c` for red, `&l` for bold), and support the `{player}` and `{time}` placeholders.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/hrd reload` | `hrd.admin` | Reloads `config.yml` |
| `/hrd revive <player>` | `hrd.admin` | Immediately ends a player's wait and returns them to the game |
| `/hrd time <player> <seconds>` | `hrd.admin` | Manually sets a player's remaining wait time |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `hrd.admin` | `op` | Access to `/hrd` commands |
| `hrd.bypass` | `op` | Players with this permission respawn instantly, skipping the delay entirely |

> **Note:** since `op` grants `hrd.bypass` by default, admins testing the world will skip the delay automatically. If you want stricter control over who bypasses the delay, manage the permission explicitly with a permissions plugin (e.g. LuckPerms) instead of relying on OP status.

## How it works

1. A player dies — **nothing about the death itself is modified** (normal item drop, normal "You Died!" screen, normal death message)
2. The player clicks **Respawn**, which fires Bukkit's `PlayerRespawnEvent`
3. The plugin intercepts this moment and immediately switches the player to spectator mode, starting a countdown (title updated every second)
4. Once the timer reaches zero: the player is switched back to survival, teleported to the world spawn, shown a confirmation message, and optionally plays a sound

### Why the "Respawn" button itself can't be disabled or labeled with a timer

The "You Died!" screen is rendered entirely client-side (part of the vanilla game code) — the server has no API to modify its appearance or disable the button directly. The server only finds out once the player actually clicks it (`PlayerRespawnEvent`), which is the exact moment this plugin takes over.

## License

MIT — do whatever you'd like with it.
