package de.lasse.lasseclient.client.modules.notification

import de.lasse.lasseclient.client.hud.HudElement
import de.lasse.lasseclient.client.hud.HudManager
import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.client.module.Module
import de.lasse.lasseclient.config.LasseConfig.Notifications
import de.lasse.lasseclient.config.NotificationSound
import de.lasse.lasseclient.config.LasseConfig.Notifications.ChatNotification as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents

/**
 * Watches incoming chat for user-defined substrings and, on a match, flashes a configurable
 * message on the HUD and plays a chosen sound.
 *
 * Each rule ([de.lasse.lasseclient.config.ChatNotificationRule]) carries its own match substring,
 * display message, color, and sound, all editable in the Chat Notifications list. Matching is done
 * on the raw chat string with formatting codes stripped, so `§6Gold!` matches a rule looking for
 * `gold` — and it's a `contains` test, not an exact match. The displayed message itself may use
 * `&`-style color codes for inline formatting on top of the rule's base color.
 */
@Environment(EnvType.CLIENT)
class ChatNotification : Module(
    name = "Chat Notifications",
    description = "Flashes a HUD message + plays a sound when chat matches your rules.",
    category = Category.NOTIFICATIONS,
) {
    // ---- Live notification state ---------------------------------------------
    @Volatile private var animStart = 0L
    @Volatile private var shownUntil = 0L
    @Volatile private var currentMessage = PREVIEW_LABEL
    @Volatile private var currentColor = 0xFFFFFF55.toInt()
    @Volatile private var pendingSound: SoundEvent? = null

    private val hud = object : HudElement(
        "chat_notification", "Chat Notifications",
        { Cfg.hudX }, { Cfg.hudX = it },
        { Cfg.hudY }, { Cfg.hudY = it },
        { Cfg.hudScale }, { Cfg.hudScale = it },
    ) {
        private fun text(editing: Boolean): String =
            (if (editing) PREVIEW_LABEL else currentMessage).ifEmpty { PREVIEW_LABEL }

        override fun contentWidth(): Int {
            val tr = MinecraftClient.getInstance().textRenderer
            return (tr.getWidth(text(false)) * TEXT_SCALE).toInt() + PAD * 2
        }

        override fun contentHeight(): Int {
            val tr = MinecraftClient.getInstance().textRenderer
            return (tr.fontHeight * TEXT_SCALE).toInt() + PAD * 2
        }

        override fun visibleInGame(): Boolean = enabled && System.currentTimeMillis() < shownUntil

        override fun renderContent(context: DrawContext, editing: Boolean) {
            val fade = if (editing) 1f else currentAlpha()
            if (fade <= 0f) return
            val text = text(editing)

            // Base color is the rule color; multiply its alpha by the fade so the message fades in/out.
            val colorAlpha = ((currentColor ushr 24) and 0xFF) / 255f
            val a = (fade * colorAlpha * 255f).toInt().coerceIn(0, 255)
            val textColor = (a shl 24) or (currentColor and 0xFFFFFF)

            val tr = MinecraftClient.getInstance().textRenderer
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
            val raw = stripFormatting(message.string)
            if (raw.isEmpty()) return@Game
            val lower = raw.lowercase()
            for (rule in Notifications.chatNotificationRules) {
                val needle = rule.match
                if (needle.isNotEmpty() && lower.contains(needle.lowercase())) {
                    trigger(rule.message, rule.color, soundOf(rule.sound))
                    break
                }
            }
        })

        // Play the queued sound on the client thread.
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            val sound = pendingSound ?: return@EndTick
            pendingSound = null
            mc.soundManager.play(PositionedSoundInstance.ui(sound, PING_PITCH, PING_VOLUME))
        })
    }

    private fun trigger(message: String, color: Int, sound: SoundEvent?) {
        val now = System.currentTimeMillis()
        // `&` is the user-facing color-code char; translate to the section sign the font reads.
        currentMessage = message.replace('&', '§').ifEmpty { PREVIEW_LABEL }
        currentColor = color
        animStart = now
        shownUntil = now + (Cfg.duration * 1000.0).toLong()
        if (sound != null) pendingSound = sound
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

    /** Strip Minecraft formatting codes (§ + one char) so matching uses the plain text. */
    private fun stripFormatting(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if ((c == '§') && i + 1 < input.length) {
                i += 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private companion object {
        const val PREVIEW_LABEL = "Chat Notification"
        const val TEXT_SCALE = 2f
        const val PAD = 0
        const val FADE_IN_MS = 150L
        const val FADE_OUT_MS = 300L
        const val PING_PITCH = 1.4f
        const val PING_VOLUME = 1.0f

        fun soundOf(sound: NotificationSound): SoundEvent? = when (sound) {
            NotificationSound.NONE -> null
            NotificationSound.PLING -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
            NotificationSound.BELL -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value()
            NotificationSound.CHIME -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value()
            NotificationSound.HARP -> SoundEvents.BLOCK_NOTE_BLOCK_HARP.value()
            NotificationSound.ANVIL -> SoundEvents.BLOCK_ANVIL_USE
            NotificationSound.LEVEL_UP -> SoundEvents.ENTITY_PLAYER_LEVELUP
            NotificationSound.ORB -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP
            NotificationSound.EXPLODE -> SoundEvents.ENTITY_GENERIC_EXPLODE.value()
        }
    }
}
