package de.lasse.lasseclient.client.modules.visual

import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.config.HighlightMode
import de.lasse.lasseclient.config.LasseConfig.Visual
import de.lasse.lasseclient.config.LasseConfig.Visual.MobHighlighter as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/**
 * Highlights SkyBlock mobs whose nametag matches a user-configured list of substrings.
 * All detection/render logic lives in [NametagEspModule]; this only supplies the name list and
 * the ESP style, both read live from [LasseConfig.Visual.MobHighlighter].
 */
@Environment(EnvType.CLIENT)
class MobHighlighter : NametagEspModule(
    name = "Mob Highlighter",
    description = "Highlights SkyBlock mobs by nametag.",
    category = Category.VISUAL,
) {
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

    // A copy of the list so the base class can detect content changes between ticks.
    override fun matchRuleSnapshot(): Any = ArrayList(Visual.mobHighlighterNames)

    override fun matchesName(cleaned: String): Boolean {
        if (cleaned.isEmpty()) return false
        val lower = cleaned.lowercase()
        return Visual.mobHighlighterNames.any { it.isNotEmpty() && lower.contains(it.lowercase()) }
    }
}
