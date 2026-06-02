package de.lasse.lasseclient.client.modules.debug

import de.lasse.lasseclient.client.hypixel.HypixelLocation
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

/**
 * One-off debug actions wired to Resourceful Config buttons (see `LasseConfig.Debug`).
 *
 * These are invoked from the config screen's render thread when the user clicks a button, so they
 * only ever touch the client instance / chat HUD (safe on that thread).
 */
@Environment(EnvType.CLIENT)
object DebugActions {

    /**
     * Print the latest Hypixel location packet (mode/map/server) into chat. Useful for finding the
     * `mode` string of an island so a feature can be gated to it. The data is whatever the server
     * last pushed via [HypixelLocation]; warp to the island and click the button there.
     */
    @JvmStatic
    fun printLocation() {
        val chat = MinecraftClient.getInstance().inGameHud.chatHud
        chat.addMessage(Text.literal("§b[Lasse] §fLocation: §7${HypixelLocation.describe()}"))
    }
}
