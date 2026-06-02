package de.lasse.lasseclient.client.modules.notification

import de.lasse.lasseclient.client.hud.HudElement
import de.lasse.lasseclient.client.hud.HudManager
import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.client.modules.visual.NametagEspModule
import de.lasse.lasseclient.config.HighlightMode
import de.lasse.lasseclient.config.LasseConfig.Notifications
import de.lasse.lasseclient.config.LasseConfig.Notifications.SpawnNotification as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvents

/**
 * Two ways to fire a HUD notification (+ a loud triple anvil ping):
 *
 *  1. **Chat messages** — any configured chat line flashes "Spawned!!!".
 *  2. **Mob names** — reuses the nametag-ESP detection ([NametagEspModule]); when a mob whose
 *     nametag matches a configured name spawns in, it flashes "RARE MOB SPAWNED IN" and the mob
 *     is highlighted via the inherited ESP (filled box + thick tracers, all changeable).
 *
 * All settings come from [LasseConfig.Notifications.SpawnNotification].
 */
@Environment(EnvType.CLIENT)
class SpawnNotification : NametagEspModule(
    name = "Spawn Notification",
    description = "Flashes a HUD notification + pings on configured chat lines or rare mob spawns.",
    category = Category.NOTIFICATIONS,
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

    override fun matchRuleSnapshot(): Any = ArrayList(Notifications.spawnNotificationMobNames)

    override fun matchesName(cleaned: String): Boolean =
        cleaned.isNotEmpty() && containsAny(Notifications.spawnNotificationMobNames, cleaned)

    override fun onMobMatched(name: String) = trigger(RARE_LABEL)

    // ---- Live notification state ---------------------------------------------
    @Volatile private var animStart = 0L
    @Volatile private var shownUntil = 0L
    @Volatile private var currentLabel = PREVIEW_LABEL

    private var soundsRemaining = 0
    private var soundCooldown = 0

    private val hud = object : HudElement(
        "spawn_notification", "Spawn Notification",
        { Cfg.hudX }, { Cfg.hudX = it },
        { Cfg.hudY }, { Cfg.hudY = it },
        { Cfg.hudScale }, { Cfg.hudScale = it },
    ) {
        private fun label(editing: Boolean): String = if (editing) PREVIEW_LABEL else currentLabel

        override fun contentWidth(): Int {
            val tr = MinecraftClient.getInstance().textRenderer
            return (tr.getWidth(label(false).ifEmpty { PREVIEW_LABEL }) * TEXT_SCALE).toInt() + PAD * 2
        }

        override fun contentHeight(): Int {
            val tr = MinecraftClient.getInstance().textRenderer
            return (tr.fontHeight * TEXT_SCALE).toInt() + PAD * 2
        }

        override fun visibleInGame(): Boolean = enabled && System.currentTimeMillis() < shownUntil

        override fun renderContent(context: DrawContext, editing: Boolean) {
            val alpha = if (editing) 1f else currentAlpha()
            if (alpha <= 0f) return
            val a = (alpha * 255f).toInt().coerceIn(0, 255)
            val text = label(editing).ifEmpty { PREVIEW_LABEL }

            val tr = MinecraftClient.getInstance().textRenderer

            val textColor = (a shl 24) or 0xFFFFFF
            val m = context.matrices
            m.pushMatrix()
            m.scale(TEXT_SCALE, TEXT_SCALE)
            context.drawText(tr, text, 0, 0, textColor, true)
            m.popMatrix()
        }
    }

    init {
        HudManager.register(hud)

        ClientReceiveMessageEvents.GAME.register(ClientReceiveMessageEvents.Game { message, overlay ->
            if (!enabled || overlay) return@Game
            if (containsAny(Notifications.spawnNotificationMessages, stripFormatting(message.string))) trigger(MESSAGE_LABEL)
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            if (soundsRemaining <= 0) return@EndTick
            if (soundCooldown > 0) {
                soundCooldown--
                return@EndTick
            }
            // Non-attenuated master sound at full volume — loud and clear regardless of distance.
            mc.soundManager.play(PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_USE, PING_PITCH, PING_VOLUME))
            soundsRemaining--
            soundCooldown = SOUND_GAP_TICKS
        })
    }

    private fun trigger(label: String) {
        val now = System.currentTimeMillis()
        currentLabel = label
        animStart = now
        shownUntil = now + (Cfg.duration * 1000.0).toLong()
        soundsRemaining = 10
        soundCooldown = 0
    }

    private fun currentAlpha(): Float {
        val now = System.currentTimeMillis()
        val total = shownUntil - animStart
        if (total <= 0L) return 0f
        val elapsed = now - animStart
        if (elapsed < 0L || elapsed > total) return 0f
        return when {
            elapsed < FADE_IN_MS -> elapsed / FADE_IN_MS.toFloat()
            elapsed > total - FADE_OUT_MS -> (total - elapsed) / FADE_OUT_MS.toFloat()
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    private companion object {
        const val MESSAGE_LABEL = "Spawned!!!"
        const val RARE_LABEL = "RARE MOB SPAWNED IN"
        const val PREVIEW_LABEL = "Spawned!!!"
        const val TEXT_SCALE = 3f
        const val PAD = 0
        const val FADE_IN_MS = 150L
        const val FADE_OUT_MS = 300L
        const val SOUND_GAP_TICKS = 2
        const val PING_PITCH = 1.8f
        const val PING_VOLUME = 1.0f

        /** Case-insensitive: does [haystack] contain any non-empty entry of [needles]? */
        fun containsAny(needles: List<String>, haystack: String): Boolean {
            if (haystack.isEmpty()) return false
            val lower = haystack.lowercase()
            return needles.any { it.isNotEmpty() && lower.contains(it.lowercase()) }
        }
    }
}
