# Lasse Client

A Fabric mod for Hypixel SkyBlock on Minecraft 1.21.11. It adds a handful of quality-of-life
features you can turn on and off from an in-game menu. Each on-screen display can be dragged
wherever you want it, and features that only matter in certain places (like the Garden) only show
up when you're actually there.

Everything runs on your own game. It changes what you see and lets you set up your own shortcuts —
it doesn't touch how you connect to the server.

## Features

### Visual

- **Mob Highlighter** — Draws an outline (and an optional line pointing to it) around mobs whose
  name matches a list you set up. Handy for spotting specific mobs in a crowd. You pick the color,
  outline thickness, how far away it works, and whether you can see it through walls.
- **Pest ESP** — Highlights Garden pests so you can find them fast. Only active on the Garden.
- **Mineshaft Utils** — Helpers for Glacite Mineshafts. Highlights corpses through walls (and can
  hide ones you've already opened or share their location with your party), and finds fossils by
  marking the spots worth digging.
- **Lilypad Helper** — Warns you right before a Garden lily-pad bomb goes off with an on-screen
  alert, a sound, and a red marker on the bomb. Can also highlight every lily pad if you want.
- **No Visual Effects** — Hides the blindness, nausea, and darkness screen effects so your view
  stays clear. The effect itself isn't removed — you just don't have to look at it.

### Notifications

- **Chat Notifications** — Watches your chat and flashes a message on screen with a sound when
  something you care about shows up. You decide what to watch for, what it says, what color it is,
  and which sound plays.
- **Spawn Notification** — Pops up an alert and plays a sound when a rare mob spawns or a chat line
  you've set up appears, and highlights the spawn so you can see where it is.

### Utilities

- **Chat Commands** — Automatically runs a command for you when a chat message matches one of your
  rules — for example, warping somewhere the moment a certain message appears. Each rule has a
  short cooldown so it won't spam.

## Controls

| Action | Default key |
| --- | --- |
| Open the settings menu | Right Shift |
| Move your on-screen displays | Right Control |

You can change both keys in **Options → Controls**. If you have Mod Menu installed, you can also
open the settings from there.

## Installation

1. Install [Fabric](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Put these in your `mods` folder:
   - **Lasse Client** — grab the latest jar from the [Releases](../../releases) page.
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
   - [Mod Menu](https://modrinth.com/mod/modmenu) (optional, but handy)
3. Launch the game and press Right Shift to open the settings.

## License

See [LICENSE.txt](LICENSE.txt).
