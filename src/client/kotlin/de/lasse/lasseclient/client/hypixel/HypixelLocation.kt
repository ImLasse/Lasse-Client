package de.lasse.lasseclient.client.hypixel

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket

/**
 * Tracks where the player currently is on Hypixel via the official Hypixel Mod API.
 *
 * The server sends a [ClientboundLocationPacket] as an event whenever the player changes
 * area (e.g. warping between SkyBlock islands). We subscribe to that event once at startup
 * and remember the latest fields. Features that should only run on a given island gate on the
 * convenience flags ([onGarden], [onMineshaft]); the debug readout uses [describe].
 *
 * The Mod API handler is invoked from the network thread, so the cached fields are `@Volatile`.
 * Registration is safe to do during mod init — the official implementation flushes our
 * subscriptions to the server when it receives the `ClientboundHelloPacket`.
 */
@Environment(EnvType.CLIENT)
object HypixelLocation {

    /** Latest `mode` from the location packet, or `null` when unknown / not on Hypixel. */
    @Volatile
    var mode: String? = null
        private set

    /** Latest `map` (e.g. the specific dungeon/mineshaft map name), or `null` when unknown. */
    @Volatile
    var map: String? = null
        private set

    /** Latest server name (each instance/lobby is its own server, so this changes on warp). */
    @Volatile
    var serverName: String? = null
        private set

    /** Latest lobby name, or `null` when not in a lobby. */
    @Volatile
    var lobbyName: String? = null
        private set

    /** Latest server type name (e.g. `SKYBLOCK`), or `null` when unknown. */
    @Volatile
    var serverType: String? = null
        private set

    /** True while the player is on the SkyBlock Garden. */
    val onGarden: Boolean
        get() = mode.equals("garden", ignoreCase = true)

    /** True while the player is inside a Glacite Mineshaft (the location `mode` Hypixel sends there). */
    val onMineshaft: Boolean
        get() = mode.equals("mineshaft", ignoreCase = true)

    /** Human-readable one-liner of the current location, for debug output. */
    fun describe(): String =
        "server=${serverName ?: "?"} type=${serverType ?: "?"} " +
            "lobby=${lobbyName ?: "-"} mode=${mode ?: "-"} map=${map ?: "-"}"

    fun init() {
        val api = HypixelModAPI.getInstance()
        api.subscribeToEventPacket(ClientboundLocationPacket::class.java)
        api.createHandler(ClientboundLocationPacket::class.java) { packet ->
            serverName = packet.serverName
            serverType = packet.serverType.map { it.name }.orElse(null)
            lobbyName = packet.lobbyName.orElse(null)
            mode = packet.mode.orElse(null)
            map = packet.map.orElse(null)
        }

        // The location is only meaningful while connected; clear it on disconnect so a
        // gated feature can't stay "on the Garden" after leaving the server.
        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ ->
            serverName = null
            serverType = null
            lobbyName = null
            mode = null
            map = null
        })
    }
}
