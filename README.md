# Lasse Client

A Fabric **client-side** utility mod for Hypixel SkyBlock, built for Minecraft `1.21.11`.
Everything is toggleable from an in-game config screen, every overlay is drag-to-place, and
location-sensitive features are gated through the official Hypixel Mod API so they only run where
they belong.

> **Client-side & cosmetic by design.** Modules act on the local render/world snapshot. Nothing
> here reads, forges, or sends gameplay packets.

---

## ✨ Features

### 👁 Visual

| Module | What it does |
| --- | --- |
| **Mob Highlighter** | Box / tracer ESP for SkyBlock mobs matched against your own nametag list. Color, line width, fill opacity, range, tracers and through-walls all configurable. |
| **Pest ESP** | Highlights Garden pests by nametag. Auto-gated to the SkyBlock Garden via the Hypixel Mod API location event. |
| **Mineshaft Utils** | Glacite Mineshaft helpers — corpse finder (through-wall ESP, hide-opened, share-to-party) plus a fossil finder that scans for quartz clusters and waypoints them. |
| **Lilypad Helper** | Warns before a Garden lily-pad bomb detonates with a HUD flash, ping and red box, and can optionally highlight every lily pad. |
| **No Visual Effects** | Suppresses blindness, nausea and darkness purely at the render layer — the effect is kept and no packet is touched. |

### 🔔 Notifications

| Module | What it does |
| --- | --- |
| **Chat Notifications** | Flashes a HUD message and plays a sound when incoming chat matches your rules (per-rule message, color and sound). |
| **Spawn Notification** | HUD notification + ping on configured chat lines or rare mob spawns, with the spawn highlighted via ESP. |

### ⚙ Utilities

| Module | What it does |
| --- | --- |
| **Chat Commands** | Runs a command automatically when chat matches one of your rules, with per-rule cooldown and optional announce. |
| **Display Entity Debug** | Inspects lily-pad / display entities and reports their live interpolated scale — a tuning helper. |

---

## ⌨ Controls & Configuration

| Action | Default key |
| --- | --- |
| Open config screen | `Right Shift` |
| Edit HUD layout | `Right Control` |

Both keys are rebindable in **Options → Controls**. Configuration is powered by
[Resourceful Config](https://modrinth.com/mod/resourceful-config), so the same settings screen is
also reachable through **Mod Menu**. Per-rule lists (highlight names, chat rules, command rules)
each get their own dropdowns and color pickers.

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft `1.21.11`.
2. Drop these into your `mods/` folder:
   - **Lasse Client** (`lasseclient-*.jar` from [Releases](../../releases))
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
   - [Mod Menu](https://modrinth.com/mod/modmenu) *(optional, recommended)*
3. Resourceful Config and the Hypixel Mod API are **bundled** inside the jar — no separate download
   needed.

---

## 🛠 Building from source

The project targets **Java 21** and builds with **Gradle 9.4.0**.

```bash
gradle build
```

The release jar lands in `build/libs/lasseclient-<version>.jar`. Pushing to `main` builds and
publishes this jar automatically via GitHub Actions (see
[`.github/workflows/release.yml`](.github/workflows/release.yml)).

---

## 📄 License

See [LICENSE.txt](LICENSE.txt).
