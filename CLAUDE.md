# CLAUDE.md — Lasse-Client Project Reference

> **Maintenance rule:** Whenever you add, remove, or significantly change a feature, module, config entry, dependency, or architectural pattern, update the relevant section(s) of this file before closing the task.

---

## Project Overview

**Lasse-Client** is a client-side Minecraft Fabric mod targeting **Hypixel SkyBlock** (MC 1.21.11). It adds ESP overlays, HUD elements, chat automation, and quality-of-life features gated by the official Hypixel Mod API location. Written in **Kotlin + Java**, built with Gradle + Fabric Loom.

---

## Build & Run

```bash
# Build JAR to build/libs/
./gradlew build

# Launch Minecraft dev client (hot-swap supported)
./gradlew runClient

# Clean build artifacts
./gradlew clean
```

Requires **JDK 21**. Gradle JVM args: `-Xmx1G` (gradle.properties).

### CI/CD

| Workflow | Trigger | Action |
|----------|---------|--------|
| `.github/workflows/build.yml` | PR or non-main push | Builds and uploads JAR artifact |
| `.github/workflows/release.yml` | Push to `main` | Builds, extracts version, creates GitHub Release with JAR |

---

## Directory Structure

```
src/
├── main/
│   ├── kotlin/de/lasse/lasseclient/
│   │   └── Lasseclient.kt              # Server-side init (empty — client-only mod)
│   └── resources/
│       ├── fabric.mod.json             # Mod metadata, entry points, dep declarations
│       ├── lasseclient.mixins.json     # Main-side mixins (currently empty)
│       └── assets/lasseclient/
│           ├── lang/en_us.json         # All UI/config label strings
│           └── font/                   # Bundled Poppins font
└── client/
    ├── java/de/lasse/lasseclient/
    │   ├── config/                     # Config POJOs and enums (Java)
    │   └── mixin/client/               # Bytecode-injection mixins (Java)
    └── kotlin/de/lasse/lasseclient/client/
        ├── LasseclientClient.kt        # CLIENT ENTRY POINT — all init here
        ├── LasseclientDataGenerator.kt
        ├── config/RcConfig.kt          # Resourceful Config registration wrapper
        ├── event/                      # Lightweight EventBus + event types
        ├── gui/                        # Config screen, HUD editor, Theme, SoundUtil
        ├── hud/                        # HudManager + HudElement base
        ├── hypixel/HypixelLocation.kt  # SkyBlock location tracking
        ├── module/                     # Module base class, ModuleManager, Category enum
        ├── modules/                    # All feature modules
        │   ├── command/                # Chat command automation
        │   ├── debug/                  # Dev/debug utilities
        │   ├── notification/           # Chat & spawn notifications
        │   └── visual/                 # ESP / highlighting modules
        └── render/RenderUtil.kt        # World-space ESP render layers
```

---

## Initialization Sequence

`LasseclientClient.onInitializeClient()` runs in order:

1. `RenderUtil.initialize()` — registers custom no-depth-test render pipelines
2. Hypixel Mod API listener — subscribes to `ClientboundLocationPacket`
3. Resourceful Config init & load (`RcConfig`)
4. Instantiate all modules → register with `ModuleManager`
5. Bind each module's `enabled` flag to its config `Observable<Boolean>`
6. `HudManager.initialize()` — registers HUD render layer
7. Register keybinds (Right Shift → config screen, Right Control → HUD editor)
8. Register `ClientTickEvents.END_CLIENT_TICK` for keybind polling

---

## Module System

### Base Class: `Module.kt`

```kotlin
abstract class Module(
    val name: String,
    val description: String,
    val category: Category
) {
    var enabled: Boolean = false
    open fun onEnable() {}
    open fun onDisable() {}
}
```

Modules bind to config via `Observable<Boolean>` — when the config toggle changes, `onEnable()` / `onDisable()` fire automatically.

### Categories (`Category.kt`)

| Enum | Purpose |
|------|---------|
| `VISUAL` | ESP / entity highlighting |
| `NOTIFICATIONS` | Chat and spawn alerts |
| `UTILITIES` | Chat commands, automation |
| `DUNGEONS` | Dungeon-specific helpers |
| `FISHING` | Fishing-related features |
| `KUUDRA` | Kuudra boss features |

### ModuleManager

Central registry — holds all `Module` instances. Iterate with `ModuleManager.modules`.

---

## All Modules

### Visual

| Class | File | Description |
|-------|------|-------------|
| `NametagEspModule` | `visual/NametagEspModule.kt` | Abstract base — scans entity nametags, renders ESP box/tracer with range & opacity controls |
| `MobHighlighter` | `visual/MobHighlighter.kt` | Highlights mobs whose nametag matches a user-configured list |
| `PestESP` | `visual/PestESP.kt` | Highlights Garden pests; gated to SkyBlock Garden only |
| `MineshaftUtils` | `visual/MineshaftUtils.kt` | Corpse finder (party chat announce) + fossil finder (quartz block clustering); gated to Mineshaft |
| `LilypadHelper` | `visual/LilypadHelper.kt` | Lily-pad bomb countdown alerts + optional highlighting; Garden only |
| `NoVisualEffects` | `visual/NoVisualEffects.kt` | Suppresses blindness/nausea/darkness visual effects (client-side only) |

### Notifications

| Class | File | Description |
|-------|------|-------------|
| `ChatNotification` | `notification/ChatNotification.kt` | Watches chat for substring matches → flash HUD message + play sound |
| `SpawnNotification` | `notification/SpawnNotification.kt` | Alerts when rare/configured mobs spawn with highlight + sound |

### Command (Utilities)

| Class | File | Description |
|-------|------|-------------|
| `ChatCommand` | `command/ChatCommand.kt` | Auto-runs commands when incoming chat matches rules; per-rule cooldown |

### Debug

| Class | File | Description |
|-------|------|-------------|
| `DisplayEntityDebug` | `debug/DisplayEntityDebug.kt` | Scans and displays lily-pad/display entities with live scale readout |
| `DebugActions` | `debug/DebugActions.kt` | One-off debug actions (e.g., print current location) |

---

## Configuration System

### Stack

`LasseConfig.java` (Resourceful Config) → `RcConfig.kt` (registration/screen) → config screen via keybind

### `LasseConfig.java` Structure

```
LasseConfig
├── Visual          (@ConfigObject)
│   ├── MobHighlighter settings
│   ├── PestESP settings
│   ├── MineshaftUtils settings
│   ├── LilypadHelper settings
│   └── NoVisualEffects settings
├── Notifications   (@ConfigObject)
│   ├── ChatNotification rules (List<ChatNotificationRule>)
│   └── SpawnNotification settings
├── Utilities       (@ConfigObject)
│   └── ChatCommand rules (List<ChatCommandRule>)
└── Debug           (@ConfigObject)
    └── debug flags
```

All fields are `public static` for direct access from module code.

### Config Data Types

| Class | Location | Fields |
|-------|----------|--------|
| `ChatNotificationRule` | `config/ChatNotificationRule.java` | `match: String`, `message: String`, `color: Int`, `sound: NotificationSound` |
| `ChatCommandRule` | `config/ChatCommandRule.java` | `match: String`, `command: String`, `announce: Boolean` |
| `HighlightMode` | `config/HighlightMode.java` | Enum: `BOX`, `FILLED_BOX` |
| `NotificationSound` | `config/NotificationSound.java` | Enum of available sounds (PLING, BELL, DING, …) |

### HUD Positions

HUD element position and scale are stored as hidden `@ConfigEntry` fields (not shown in UI) on `LasseConfig`. Values are `Float` in range 0..1 (screen fraction). `HudElement` reads/writes these on drag.

### Adding a New Config Entry

1. Add `@ConfigEntry` annotated `public static` field to the correct `@ConfigObject` inner class in `LasseConfig.java`
2. Add translation key to `src/main/resources/assets/lasseclient/lang/en_us.json`
3. Access via `LasseConfig.CategoryName.fieldName` from module code
4. Update this CLAUDE.md

---

## HUD System

### `HudElement.kt`

Abstract base. Subclass must implement `render(context: DrawContext, delta: Float)`. Position is stored as 0..1 screen fraction, converted to pixels at render time. Draggable in the HUD editor screen (`HudEditScreen.kt`).

### `HudManager.kt`

Singleton registry. Call `HudManager.register(element)` during init. The manager draws all registered elements each frame via a Fabric HUD render callback.

---

## Rendering

### World-Space ESP (`render/RenderUtil.kt`)

- Creates a custom `RenderLayer` with no depth test (through-walls).
- Initialized once at mod startup.
- Call `RenderUtil.drawBox(...)` / `RenderUtil.drawFilledBox(...)` from within `WorldRenderEvents.END_MAIN` callbacks.

### 2D HUD Rendering

- Done via `DrawContext` inside `HudRenderCallback`.
- `Theme.kt` provides ARGB color constants for consistent styling.

### Mixin-Based Visual Suppression (`mixin/client/`)

| Mixin | Effect |
|-------|--------|
| `StatusEffectFogModifierMixin` | Removes blindness/darkness fog |
| `LivingEntityMixin` | Removes nausea wobble + darkness lightmap dim for local player |
| `ClientPlayNetworkHandlerMixin` | Intercepts packets for EventBus dispatch |
| `EntityMixin` | Entity-level hooks |
| `EntityAccessor` | Exposes private entity fields via accessor |

---

## Event System

### `EventBus.kt`

Lightweight, thread-safe (copy-on-write list). Exceptions in listeners are caught and logged — one bad listener never kills dispatch.

### Events

| Event | Fired by | Fields |
|-------|----------|--------|
| `PacketReceivedEvent` | `ClientPlayNetworkHandlerMixin` | `packet: Packet<*>` |
| `NameChangeEvent` | `EntityMixin` | `entity: Entity`, `oldName: String`, `newName: String` |

To add a new event: create a data class in `event/`, fire it via `EventBus.post(YourEvent(...))` in the appropriate mixin or callback, subscribe with `EventBus.subscribe<YourEvent> { ... }`.

---

## Hypixel Location Gating

`HypixelLocation.kt` subscribes to `ClientboundLocationPacket` and exposes:

```kotlin
object HypixelLocation {
    val mode: String?       // SkyBlock island type (e.g. "garden", "mineshaft")
    val map: String?        // Map name within the mode
    val server: String?
    val lobbyName: String?

    val onGarden: Boolean
    val onMineshaft: Boolean
    fun describe(): String  // Human-readable location string
}
```

Always gate location-specific modules with `HypixelLocation.onXxx` checks inside `onEnable()` or event callbacks.

---

## Key Bindings (Default)

| Key | Action |
|-----|--------|
| Right Shift | Open config screen |
| Right Control | Open HUD editor |

Bindings are registered in `LasseclientClient.kt` via `KeyBindingHelper`.

---

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Minecraft | 1.21.11 | Game |
| Fabric Loader | 0.19.2 | Mod loader |
| Fabric API | 0.141.4+1.21.11 | Events, keybinds, HUD, rendering, networking |
| fabric-language-kotlin | 1.13.11+kotlin.2.3.21 | Kotlin runtime |
| Kotlin | 2.3.21 | Language |
| Resourceful Config | 3.11.3 (fabric) | Annotation-based config + in-game screen; bundled |
| Hypixel Mod API core | 1.0.1 | Packet definitions + protocol |
| Hypixel Mod API (Fabric) | 1.0.1+build.1+mc1.21 | Fabric implementation; bundled |
| DevAuth-fabric | 1.2.2 | Dev-only: authenticate to Hypixel from dev env |

Versions are pinned in `gradle.properties`.

---

## Theme Colors (`gui/Theme.kt`)

All UI uses constants from `Theme` — do not hardcode ARGB values inline:

```kotlin
Theme.BACKGROUND      // dark navy panel
Theme.ACCENT          // bright blue highlight
Theme.TEXT            // near-white
Theme.TEXT_SECONDARY  // muted grey
Theme.RED             // error / danger
Theme.GREEN           // success / active
```

---

## Adding a New Module — Checklist

1. Create `src/client/kotlin/.../modules/<category>/YourModule.kt`
2. Extend `Module(name, description, Category.XXXX)`
3. Register in `LasseclientClient.kt` (inside the modules list)
4. Add config fields to `LasseConfig.java` (in the matching `@ConfigObject`)
5. Add translation keys to `en_us.json`
6. If location-gated, check `HypixelLocation.onXxx` in `onEnable()`
7. If it renders ESP, use `RenderUtil` inside a `WorldRenderEvents.END_MAIN` listener
8. If it needs a new event, add to `event/` and fire from a mixin
9. **Update the Modules table in this CLAUDE.md**

---

## Adding a New HUD Element — Checklist

1. Create a class extending `HudElement`
2. Add position/scale config fields to `LasseConfig.java` (annotated `@ConfigEntry(hidden = true)`)
3. Register via `HudManager.register(YourHudElement())` in `LasseclientClient.kt`
4. **Update this CLAUDE.md**

---

## Mixin Guidelines

- Keep mixins in `src/client/java/de/lasse/lasseclient/mixin/client/`
- Register in `src/client/resources/lasseclient.client.mixins.json`
- Scope to local player where possible (`MinecraftClient.getInstance().player == entity`)
- Never use mixins to suppress server-side packets or affect game logic — visual-only
- Prefer `@Inject` over `@Overwrite`; use `CallbackInfo.cancel()` sparingly and document why

---

## No Test Suite

There are no unit or integration tests. Verification is done by running the dev client (`./gradlew runClient`) and testing on Hypixel SkyBlock manually.
