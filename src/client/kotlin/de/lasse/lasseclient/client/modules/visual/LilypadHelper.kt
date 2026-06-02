package de.lasse.lasseclient.client.modules.visual

import de.lasse.lasseclient.client.hud.HudElement
import de.lasse.lasseclient.client.hud.HudManager
import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.client.module.Module
import de.lasse.lasseclient.client.render.RenderUtil
import de.lasse.lasseclient.config.HighlightMode
import de.lasse.lasseclient.config.LasseConfig.Visual.LilypadHelper as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.item.Items
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentHashMap

/**
 * SkyBlock Garden "lily pad bomb" helper.
 *
 * The bomb is a vanilla item [DisplayEntity] showing `minecraft:lily_pad` (no nametag), which
 * grows in scale and detonates at ~8.2. This module watches those entities purely from the client
 * world snapshot (no packets). All settings come from [LasseConfig.Visual.LilypadHelper].
 *
 *  - **Explosion alert**: when a pad's scale crosses `alertThreshold` (~6.5), flash a HUD warning +
 *    loud ping and draw a 40%-opacity red filled box with a thick tracer until it despawns.
 *  - **Highlight all**: optionally box/tracer *every* lily pad regardless of size.
 */
@Environment(EnvType.CLIENT)
class LilypadHelper : Module(
    name = "Lilypad Helper",
    description = "Alerts before a Garden lily pad explodes and can highlight all lily pads.",
    category = Category.VISUAL,
) {
    // ---- Alert state ----------------------------------------------------------
    /** Entity ids already alerted for, so a single pad only fires once while it stays big. */
    private val alertedIds = ConcurrentHashMap.newKeySet<Int>()

    @Volatile private var animStart = 0L
    @Volatile private var shownUntil = 0L
    private var soundsRemaining = 0
    private var soundCooldown = 0

    private val hud = object : HudElement(
        "lilypad_alert", "Lilypad Alert",
        { Cfg.hudX }, { Cfg.hudX = it },
        { Cfg.hudY }, { Cfg.hudY = it },
        { Cfg.hudScale }, { Cfg.hudScale = it },
    ) {
        override fun contentWidth(): Int {
            val tr = MinecraftClient.getInstance().textRenderer
            return (tr.getWidth(LABEL) * TEXT_SCALE).toInt() + PAD * 2
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

            val tr = MinecraftClient.getInstance().textRenderer

            val m = context.matrices
            m.pushMatrix()
            m.scale(TEXT_SCALE, TEXT_SCALE)
            context.drawText(tr, LABEL, 0, 0, (a shl 24) or 0xFF5555, true)
            m.popMatrix()
        }
    }

    init {
        HudManager.register(hud)

        WorldRenderEvents.END_MAIN.register(WorldRenderEvents.EndMain { ctx ->
            if (enabled) render(ctx)
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            if (!enabled) return@EndTick
            tick(mc)
            pumpSound(mc)
        })
    }

    // ---- Detection ------------------------------------------------------------

    private fun isLilyPad(e: DisplayEntity): Boolean = when (e) {
        is DisplayEntity.ItemDisplayEntity -> e.itemStack.isOf(Items.LILY_PAD)
        is DisplayEntity.BlockDisplayEntity -> e.blockState.isOf(Blocks.LILY_PAD)
        else -> false
    }

    /** Uniform scale of the pad (max axis), or null until its render state exists. */
    private fun sizeOf(e: DisplayEntity, tickDelta: Float): Float? {
        val state = e.renderState ?: return null
        val s = state.transformation().interpolate(tickDelta).scale
        return maxOf(s.x(), s.y(), s.z())
    }

    private fun tick(mc: MinecraftClient) {
        val world = mc.world ?: return
        val player = mc.player ?: return
        if (!Cfg.alertOnBig) {
            alertedIds.clear()
            return
        }
        val range = Cfg.range.toDouble()
        val rangeSq = range * range
        val threshold = Cfg.alertThreshold.toFloat()
        val live = HashSet<Int>()

        for (entity in world.entities) {
            if (entity !is DisplayEntity || entity.isRemoved || !isLilyPad(entity)) continue
            if (entity.squaredDistanceTo(player) > rangeSq) continue
            val size = sizeOf(entity, 1f) ?: continue
            if (size < threshold) continue
            live += entity.id
            if (alertedIds.add(entity.id)) triggerAlert()
        }
        // Forget pads that despawned or shrank, so they can re-alert if they grow again.
        alertedIds.retainAll(live)
    }

    private fun triggerAlert() {
        val now = System.currentTimeMillis()
        animStart = now
        shownUntil = now + (Cfg.alertDuration * 1000.0).toLong()
        soundsRemaining = 6
        soundCooldown = 0
    }

    private fun pumpSound(mc: MinecraftClient) {
        if (soundsRemaining <= 0) return
        if (soundCooldown > 0) {
            soundCooldown--
            return
        }
        mc.soundManager.play(PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_USE, 1.8f, 1.0f))
        soundsRemaining--
        soundCooldown = SOUND_GAP_TICKS
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

    // ---- Render ---------------------------------------------------------------

    private fun render(ctx: WorldRenderContext) {
        val mc = MinecraftClient.getInstance()
        val world = mc.world ?: return
        val player = mc.player ?: return
        val matrices = ctx.matrices() ?: return
        val consumers = ctx.consumers() ?: return
        val cam = ctx.worldState().cameraRenderState.pos ?: return
        val tickDelta = mc.renderTickCounter.getTickProgress(false)
        val range = Cfg.range.toDouble()
        val rangeSq = range * range
        val threshold = Cfg.alertThreshold.toFloat()

        val doAlert = Cfg.alertOnBig
        val doHighlight = Cfg.highlightAll
        if (!doAlert && !doHighlight) return

        val tracerOrigin = Vec3d(
            MathHelper.lerp(tickDelta.toDouble(), player.lastRenderX, player.x),
            MathHelper.lerp(tickDelta.toDouble(), player.lastRenderY, player.y) +
                player.getEyeHeight(player.pose).toDouble(),
            MathHelper.lerp(tickDelta.toDouble(), player.lastRenderZ, player.z),
        ).add(player.getRotationVec(tickDelta).multiply(0.4))

        matrices.push()
        matrices.translate(-cam.x, -cam.y, -cam.z)

        for (entity in world.entities) {
            if (entity !is DisplayEntity || entity.isRemoved || !isLilyPad(entity)) continue
            if (entity.squaredDistanceTo(player) > rangeSq) continue
            val size = sizeOf(entity, tickDelta) ?: continue

            val px = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderX, entity.x)
            val py = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderY, entity.y)
            val pz = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderZ, entity.z)
            val box = boxFor(px, py, pz, size)

            val isBig = doAlert && size >= threshold
            if (isBig) {
                // Fixed alert style: 40% red fill, red outline, thick red tracer.
                val fill = (FORTY_PCT shl 24) or RED_RGB
                RenderUtil.drawFilledBox(matrices, consumers, box, fill, 0xFFFF0000.toInt(), 3f, throughWalls = true)
                RenderUtil.drawLine(matrices, consumers, tracerOrigin, box.center, 0xFFFF0000.toInt(), 5f, true)
            } else if (doHighlight) {
                val color = Cfg.highlightColor
                val lineWidth = Cfg.lineWidth.toFloat()
                when (Cfg.highlightMode) {
                    HighlightMode.BOX ->
                        RenderUtil.drawBoxOutline(matrices, consumers, box, color, lineWidth, true)
                    HighlightMode.FILLED_BOX -> {
                        val a = ((Cfg.fillOpacity / 100.0) * 255).toInt().coerceIn(0, 255)
                        val fill = (color and 0x00FFFFFF) or (a shl 24)
                        RenderUtil.drawFilledBox(matrices, consumers, box, fill, color, lineWidth, true)
                    }
                }
                if (Cfg.highlightTracers) {
                    RenderUtil.drawLine(matrices, consumers, tracerOrigin, box.center, Cfg.tracerColor, Cfg.tracerWidth.toFloat(), true)
                }
            }
        }

        matrices.pop()

        RenderUtil.flush(consumers)
    }

    /** Box centered on the pad's origin, with a quarter-scale height (lily pads are flat). */
    private fun boxFor(px: Double, py: Double, pz: Double, size: Float): Box {
        val half = size / 2.0
        val halfH = (size / 4.0) / 2.0
        return Box(px - half, py - halfH, pz - half, px + half, py + halfH, pz + half)
    }

    private companion object {
        const val LABEL = "LILYPAD ABOUT TO EXPLODE"
        const val TEXT_SCALE = 2.5f
        const val PAD = 0
        const val FADE_IN_MS = 150L
        const val FADE_OUT_MS = 300L
        const val SOUND_GAP_TICKS = 2
        const val RED_RGB = 0xFF0000
        const val FORTY_PCT = 102 // 0.40 * 255
    }
}
