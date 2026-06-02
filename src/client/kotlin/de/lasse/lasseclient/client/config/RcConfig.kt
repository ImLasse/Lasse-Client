package de.lasse.lasseclient.client.config

import com.teamresourceful.resourcefulconfig.api.loader.Configurator
import de.lasse.lasseclient.client.gui.config.LasseConfigScreen
import de.lasse.lasseclient.config.LasseConfig
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screen.Screen

/**
 * Owns the Resourceful Config [Configurator] for Lasse Client.
 *
 * Resourceful Config handles all persistence and the in-game/Mod Menu screen; this object just
 * registers [LasseConfig], exposes a save hook for state changed outside the screen (the HUD
 * editor moves elements by writing the hidden `hud*` fields), and builds Resourceful Config's
 * own default-theme screen for the open-GUI keybind.
 */
@Environment(EnvType.CLIENT)
object RcConfig {

    val configurator = Configurator("lasseclient")

    /** Register + load the config. Must run before modules bind their `enabled` state. */
    fun init() {
        configurator.register(LasseConfig::class.java)
    }

    /** Persist the config to disk. Used after programmatic edits (e.g. HUD repositioning). */
    fun save() {
        runCatching { configurator.saveConfig(LasseConfig::class.java) }
    }

    /** Our config screen: RC's themed widgets in a pinned-sidebar / swap-in-place shell. */
    fun screen(parent: Screen?): Screen = LasseConfigScreen(parent)
}
