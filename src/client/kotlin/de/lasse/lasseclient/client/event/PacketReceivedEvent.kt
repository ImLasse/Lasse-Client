package de.lasse.lasseclient.client.event

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.network.packet.Packet

/**
 * Fired (read-only) when an inbound S2C packet of interest reaches the client play network
 * handler. The mixin that fires this event does NOT cancel or modify the packet — it merely
 * observes it before the vanilla handler runs, so this is safe to use in environments with
 * server-side anti-cheat (e.g. Hypixel).
 *
 * NOT every packet fires this — only the ones our mixin explicitly hooks. See
 * `ClientPlayNetworkHandlerMixin` for the current set.
 */
@Environment(EnvType.CLIENT)
data class PacketReceivedEvent(val packet: Packet<*>)
