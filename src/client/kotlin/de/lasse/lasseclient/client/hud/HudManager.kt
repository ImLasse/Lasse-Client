package de.lasse.lasseclient.client.hud

import de.lasse.lasseclient.client.gui.HudEditScreen
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement as FabricHudElement

/**
 * Registry + renderer for all [HudElement]s. Modules register their elements here; a single
 * Fabric HUD layer draws every visible element on top of the vanilla HUD. The HUD editor
 * ([HudEditScreen]) iterates the same list to let the user reposition/resize everything.
 */
@Environment(EnvType.CLIENT)
object HudManager {

    private val elementsInternal: MutableList<HudElement> = mutableListOf()
    val elements: List<HudElement> get() = elementsInternal

    fun register(element: HudElement) {
        elementsInternal += element
    }

    /** Call once from ClientModInitializer. Registers the in-game HUD draw layer. */
    fun init() {
        HudElementRegistry.addLast(
            Identifier.of("lasseclient", "hud_overlay"),
            FabricHudElement { context, _ ->
                val mc = MinecraftClient.getInstance()
                // The editor draws its own preview; don't double-render.
                if (mc.currentScreen is HudEditScreen) return@FabricHudElement
                val sw = mc.window.scaledWidth
                val sh = mc.window.scaledHeight
                for (e in elementsInternal) {
                    if (e.visibleInGame()) e.render(context, sw, sh, editing = false)
                }
            },
        )
    }
}
