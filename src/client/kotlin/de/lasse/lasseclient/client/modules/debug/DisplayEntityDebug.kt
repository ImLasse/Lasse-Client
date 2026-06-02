package de.lasse.lasseclient.client.modules.debug

import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.client.module.Module
import de.lasse.lasseclient.client.render.RenderUtil
import de.lasse.lasseclient.config.LasseConfig.Utilities.DisplayEntityDebug as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

/**
 * Inspection tool for the lily-pad "bomb" display entity (and display entities in general).
 *
 * SkyBlock renders the growing lily pad as a vanilla [DisplayEntity] (a block display showing
 * `minecraft:lily_pad`) with no nametag armor stand attached, so the existing nametag-based ESP
 * can't see it. This module finds those entities purely from the client world snapshot — it never
 * reads or touches packets — and reports their live scale so we can pick the right thresholds for
 * the eventual notifier (when to warn, tracer range, etc.).
 *
 * What it gives you:
 *  - A box drawn around every candidate, sized to its current (interpolated) scale.
 *  - The nearest candidate's live scale shown on the action bar, so you can watch it climb.
 *  - Optional once-per-second chat dump of every candidate: type, block/item, scale x/y/z, pos, dist.
 */
@Environment(EnvType.CLIENT)
class DisplayEntityDebug : Module(
    name = "Display Entity Debug",
    description = "Inspects lily-pad / display entities and reports their live scale.",
    category = Category.UTILITIES,
) {

    private var tickCounter = 0

    init {
        WorldRenderEvents.END_MAIN.register(WorldRenderEvents.EndMain { ctx ->
            if (enabled) render(ctx)
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            if (!enabled) return@EndTick
            tick(mc)
        })
    }

    private fun isCandidate(e: DisplayEntity): Boolean {
        if (!Cfg.lilyPadsOnly) return true
        // SkyBlock's lily-pad bomb is an *item* display showing minecraft:lily_pad, not a block
        // display — but accept either form so we don't miss a variant.
        return when (e) {
            is DisplayEntity.ItemDisplayEntity -> e.itemStack.isOf(Items.LILY_PAD)
            is DisplayEntity.BlockDisplayEntity -> e.blockState.isOf(Blocks.LILY_PAD)
            else -> false
        }
    }

    /** Current interpolated scale of a display entity, as (x, y, z). Null until first render. */
    private fun scaleOf(e: DisplayEntity, tickDelta: Float): Triple<Float, Float, Float>? {
        val state = e.renderState ?: return null
        val s = state.transformation().interpolate(tickDelta).scale
        return Triple(s.x(), s.y(), s.z())
    }

    private fun describe(e: DisplayEntity): String = when (e) {
        is DisplayEntity.BlockDisplayEntity -> "block:${blockId(e)}"
        is DisplayEntity.ItemDisplayEntity -> "item:${e.itemStack.item}"
        is DisplayEntity.TextDisplayEntity -> "text"
        else -> "display"
    }

    private fun blockId(e: DisplayEntity.BlockDisplayEntity): String =
        e.blockState.block.toString().substringAfterLast('{').removeSuffix("}")

    private fun tick(mc: MinecraftClient) {
        val world = mc.world ?: return
        val player = mc.player ?: return
        val range = Cfg.radius.toDouble()
        val rangeSq = range * range

        var nearest: DisplayEntity? = null
        var nearestDistSq = Double.MAX_VALUE
        val candidates = mutableListOf<DisplayEntity>()

        for (entity in world.entities) {
            if (entity !is DisplayEntity || entity.isRemoved) continue
            if (!isCandidate(entity)) continue
            val distSq = entity.squaredDistanceTo(player)
            if (distSq > rangeSq) continue
            candidates += entity
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearest = entity
            }
        }

        // Live action-bar readout of the nearest candidate's scale.
        nearest?.let { e ->
            val (sx, sy, sz) = scaleOf(e, 1f) ?: return@let
            val dist = Math.sqrt(nearestDistSq)
            player.sendMessage(
                Text.literal(
                    "LilyPad scale=%.3f  (x%.2f y%.2f z%.2f)  dist=%.1fm".format(maxOf(sx, sy, sz), sx, sy, sz, dist)
                ),
                /* overlay = */ true,
            )
        }

        // Throttled full dump to chat.
        if (Cfg.logToChat) {
            tickCounter++
            if (tickCounter >= 20) {
                tickCounter = 0
                if (candidates.isEmpty()) {
                    chat(mc, "[DisplayDebug] no candidates within ${Cfg.radius}m")
                } else {
                    chat(mc, "[DisplayDebug] ${candidates.size} candidate(s):")
                    for (e in candidates) {
                        val (sx, sy, sz) = scaleOf(e, 1f) ?: continue
                        chat(
                            mc,
                            "  id=${e.id} ${describe(e)} scale=(%.2f,%.2f,%.2f) pos=(%.1f,%.1f,%.1f) dist=%.1f".format(
                                sx, sy, sz, e.x, e.y, e.z, Math.sqrt(e.squaredDistanceTo(player)),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun render(ctx: WorldRenderContext) {
        val mc = MinecraftClient.getInstance()
        val world = mc.world ?: return
        val player = mc.player ?: return
        val matrices = ctx.matrices() ?: return
        val consumers = ctx.consumers() ?: return
        val cam = ctx.worldState().cameraRenderState.pos ?: return
        val tickDelta = mc.renderTickCounter.getTickProgress(false)
        val range = Cfg.radius.toDouble()
        val rangeSq = range * range

        val tracerOrigin = if (Cfg.showTracer) {
            val eye = Vec3d(
                MathHelper.lerp(tickDelta.toDouble(), player.lastRenderX, player.x),
                MathHelper.lerp(tickDelta.toDouble(), player.lastRenderY, player.y) +
                    player.getEyeHeight(player.pose).toDouble(),
                MathHelper.lerp(tickDelta.toDouble(), player.lastRenderZ, player.z),
            )
            eye.add(player.getRotationVec(tickDelta).multiply(0.4))
        } else null

        matrices.push()
        matrices.translate(-cam.x, -cam.y, -cam.z)

        for (entity in world.entities) {
            if (entity !is DisplayEntity || entity.isRemoved) continue
            if (!isCandidate(entity)) continue
            if (entity.squaredDistanceTo(player) > rangeSq) continue

            val px = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderX, entity.x)
            val py = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderY, entity.y)
            val pz = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderZ, entity.z)

            val (sx, sy, sz) = scaleOf(entity, tickDelta) ?: continue
            // The item display renders centered on the entity origin, so center the box there too.
            // Lily pads are flat, so use a quarter of the vertical scale for the box height.
            val halfW = sx / 2.0
            val halfD = sz / 2.0
            val halfH = (sy / 4.0) / 2.0
            val box = Box(px - halfW, py - halfH, pz - halfD, px + halfW, py + halfH, pz + halfD)

            if (Cfg.renderBox) {
                RenderUtil.drawBoxOutline(matrices, consumers, box, 0xFFFF4DFF.toInt(), 2f, throughWalls = true)
            }

            if (tracerOrigin != null) {
                RenderUtil.drawLine(matrices, consumers, tracerOrigin, box.center, 0xFFFF4DFF.toInt(), 1.5f, true)
            }

            // Single size number floating above the pad (scale is uniform, so one value says it all).
            val labelPos = Vec3d(px, box.maxY + 0.3, pz)
            drawSizeLabel(mc, matrices, consumers, labelPos, maxOf(sx, sy, sz))
        }

        matrices.pop()

        RenderUtil.flush(consumers)
    }

    /** Billboarded "size" number drawn at [worldPos]. Matrix stack is already camera-translated. */
    private fun drawSizeLabel(
        mc: MinecraftClient,
        matrices: net.minecraft.client.util.math.MatrixStack,
        consumers: net.minecraft.client.render.VertexConsumerProvider,
        worldPos: Vec3d,
        size: Float,
    ) {
        val textRenderer = mc.textRenderer
        val label = "%.2f".format(size)

        matrices.push()
        matrices.translate(worldPos.x, worldPos.y, worldPos.z)
        matrices.multiply(mc.gameRenderer.camera.rotation)
        matrices.scale(-0.025f, -0.025f, 0.025f)

        val matrix = matrices.peek().positionMatrix
        val x = -textRenderer.getWidth(label) / 2f
        textRenderer.draw(
            label, x, 0f, 0xFFFFFFFF.toInt(), false, matrix, consumers,
            TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0,
        )
        matrices.pop()
    }

    private fun chat(mc: MinecraftClient, msg: String) {
        mc.inGameHud.chatHud.addMessage(Text.literal(msg))
    }
}
