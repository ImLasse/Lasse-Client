package de.lasse.lasseclient.client.modules.visual

import de.lasse.lasseclient.client.hypixel.HypixelLocation
import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.config.HighlightMode
import de.lasse.lasseclient.config.LasseConfig.Visual.PestEsp as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/**
 * Highlights SkyBlock Garden pests. Same detection/render as [NametagEspModule]; the matched
 * names are fixed to the known pest set, and the ESP style comes from
 * [LasseConfig.Visual.PestEsp].
 */
@Environment(EnvType.CLIENT)
class PestESP : NametagEspModule(
    name = "Pest ESP",
    description = "Highlights SkyBlock Garden pests by nametag.",
    category = Category.VISUAL,
) {
    // Pests are silverfish (0.4 x 0.3 hitbox) with a much larger floating display model,
    // so inflate the box to roughly cover what's actually drawn on screen.
    override val minBoxWidth: Double = 0.9
    override val minBoxHeight: Double = 1.0

    override fun cfgMode(): HighlightMode = Cfg.mode
    override fun cfgColor(): Int = Cfg.color
    override fun cfgLineWidth(): Double = Cfg.lineWidth
    override fun cfgFillOpacity(): Int = Cfg.fillOpacity
    override fun cfgThroughWalls(): Boolean = Cfg.throughWalls
    override fun cfgRange(): Int = Cfg.range
    override fun cfgTracers(): Boolean = Cfg.tracers
    override fun cfgTracerColor(): Int = Cfg.tracerColor
    override fun cfgTracerWidth(): Double = Cfg.tracerWidth
    override fun cfgMaxIdOffset(): Int = Cfg.maxIdOffset

    // Pests only exist on the SkyBlock Garden, so only detect/render there. The location comes
    // from the Hypixel Mod API's location event (see [HypixelLocation]).
    override fun isActive(): Boolean = enabled && HypixelLocation.onGarden

    override fun matchesName(cleaned: String): Boolean {
        if (cleaned.isEmpty()) return false
        val lower = cleaned.lowercase()
        return PEST_NAMES.any { lower.contains(it) }
    }

    private companion object {
        // Lowercased for case-insensitive substring matching (inclusive, like the highlighter).
        val PEST_NAMES: List<String> = listOf(
            "Beetle", "Cricket", "Dragonfly", "Earthworm", "Field Mouse", "Firefly",
            "Fly", "Locust", "Lunar Moth", "Mite", "Mosquito", "Moth",
            "Praying Mantis", "Rat", "Slug",
        ).map { it.lowercase() }
    }
}
