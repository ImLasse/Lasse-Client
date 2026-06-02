package de.lasse.lasseclient.client.modules.visual

import de.lasse.lasseclient.client.hypixel.HypixelLocation
import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.client.module.Module
import de.lasse.lasseclient.client.render.RenderUtil
import de.lasse.lasseclient.config.HighlightMode
import de.lasse.lasseclient.config.LasseConfig.Visual.MineshaftUtils as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glacite Mineshaft helpers, all gated on being inside a mineshaft ([HypixelLocation.onMineshaft]).
 * Two independent sub-features share this module so they sit under one toggle:
 *
 *  - **Corpse Finder** — highlights the four corpse types through walls (each a *visible* armor
 *    stand wearing a tell-tale helmet), can hide opened ones (from the loot chat line), and can
 *    share coordinates to party chat.
 *  - **Fossil Finder** — periodically scans nearby blocks for clusters of quartz (the blocks
 *    fossils are made of) and highlights each cluster as a waypoint, separately styled/colored.
 *
 * All options come from [LasseConfig.Visual.MineshaftUtils].
 */
@Environment(EnvType.CLIENT)
class MineshaftUtils : Module(
    name = "Mineshaft Utils",
    description = "Glacite Mineshaft helpers: corpse highlighting and fossil finding.",
    category = Category.VISUAL,
) {
    // ---- Corpse state ---------------------------------------------------------

    /** A cached corpse waypoint: its type and the (static) box to draw. Corpses don't move. */
    private class CorpseWaypoint(val type: CorpseType, val box: Box)

    /**
     * Corpses seen this mineshaft, keyed by block position. Once a corpse is detected its box is
     * remembered here and keeps rendering even after the armor stand unloads (out of render
     * distance) — it's only removed when opened or when leaving the mineshaft.
     */
    private val corpses: MutableMap<Long, CorpseWaypoint> = ConcurrentHashMap()

    /** Block-position keys of corpses the player has opened; removed from the cache and never re-added. */
    private val openedKeys: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Block-position keys already announced to party chat, so each corpse is shared at most once. */
    private val sharedKeys: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Server we last saw a corpse on — when it changes (warp to a new instance) we drop stale state. */
    @Volatile private var lastServerName: String? = null

    /** Ticks until the next party-chat share is allowed, to stay under Hypixel's chat rate limit. */
    private var shareCooldown = 0

    // ---- Fossil state ---------------------------------------------------------
    //
    // Fossils are scanned exactly once per mineshaft instance: a short delay after entering (so
    // chunks load) we do one straightforward block sweep of the cube around us, cluster the hits,
    // and stop. We don't scan again until you enter another mineshaft (server name changes). As you
    // mine, the affected waypoints shrink/disappear without any re-scan.

    /**
     * A detected fossil: the quartz block positions that make it up plus their bounding box. We
     * keep the blocks (not just the box) so we can shrink/drop the waypoint as they're mined,
     * without re-running the scan.
     */
    private class Fossil(val blocks: MutableSet<Long>, @Volatile var box: Box)

    /** Detected fossils. Set once when the entry scan finishes, then pruned as blocks are mined. */
    @Volatile private var fossils: List<Fossil> = emptyList()

    /** Server name we've already completed a scan for; `== current` means "don't scan again". */
    private var scannedServer: String? = null

    /** Ticks to wait after entering before scanning, so nearby chunks have arrived. */
    private var entryDelay = 0

    /** Ticks until the next mined-block re-validation of existing fossils. */
    private var validateCooldown = 0

    init {
        WorldRenderEvents.END_MAIN.register(WorldRenderEvents.EndMain { ctx ->
            if (isActive()) render(ctx)
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            if (!isActive()) {
                if (fossils.isNotEmpty()) fossils = emptyList()
                return@EndTick
            }
            resetOnInstanceChange()
            if (Cfg.corpseEnabled) scanCorpses(mc)
            tickFossils(mc)
        })

        // A corpse you open prints "<TYPE> CORPSE LOOT!" to chat. There's no entity id in the
        // message, but you're standing on the corpse when you open it, so the nearest visible
        // armor stand of that type is the one to forget.
        ClientReceiveMessageEvents.GAME.register(ClientReceiveMessageEvents.Game { message, overlay ->
            if (!isActive() || overlay || !Cfg.corpseEnabled || !Cfg.hideOpened) return@Game
            val type = lootMessageType(stripFormatting(message.string)) ?: return@Game
            markNearestOpened(type)
        })
    }

    private fun isActive(): Boolean = enabled && HypixelLocation.onMineshaft

    /**
     * Corpse keys/positions are per-instance; warping to another mineshaft must not carry over old
     * waypoints. Drop all per-instance corpse state (and re-arm the fossil scan) on a server change.
     */
    private fun resetOnInstanceChange() {
        val server = HypixelLocation.serverName
        if (server != lastServerName) {
            lastServerName = server
            corpses.clear()
            openedKeys.clear()
            sharedKeys.clear()
            // New mineshaft instance: forget old fossils and arm a fresh one-shot scan.
            fossils = emptyList()
            scannedServer = null
            entryDelay = ENTRY_SCAN_DELAY_TICKS
        }
    }

    // ====================== Corpse Finder ======================

    private enum class CorpseType { LAPIS, TUNGSTEN, UMBER, VANGUARD }

    /** True when at least one *other* real player (version-4 UUID excludes Hypixel NPCs) is loaded. */
    private fun hasOtherPlayers(mc: MinecraftClient): Boolean {
        val world = mc.world ?: return false
        val self = mc.player ?: return false
        return world.players.any { it !== self && it.uuid.version() == 4 }
    }

    /**
     * Detect loaded corpses within range and remember each one in [corpses] by block position, so
     * its box persists after the armor stand unloads. Newly-seen corpses are also announced to party
     * chat (at most one per cooldown) when [Cfg.shareToParty] is on and another real player shares
     * the mineshaft. Corpses are cached regardless of their per-type toggle (render filters by it),
     * so toggling a type back on reveals ones already discovered.
     */
    private fun scanCorpses(mc: MinecraftClient) {
        val world = mc.world ?: return
        val player = mc.player ?: return
        if (shareCooldown > 0) shareCooldown--
        val canShare = Cfg.shareToParty && hasOtherPlayers(mc)
        val rangeSq = Cfg.range.toDouble() * Cfg.range.toDouble()

        for (entity in world.entities) {
            if (entity !is ArmorStandEntity || entity.isRemoved || entity.isInvisible) continue
            if (entity.squaredDistanceTo(player) > rangeSq) continue
            val key = entity.blockPos.asLong()
            if (key in openedKeys) continue
            val type = corpseTypeOf(entity) ?: continue

            corpses.putIfAbsent(key, CorpseWaypoint(type, corpseBoxOf(entity)))

            if (canShare && isTypeEnabled(type) && shareCooldown <= 0 && sharedKeys.add(key)) {
                val pos = entity.blockPos
                mc.player?.networkHandler?.sendChatCommand(
                    "pc x: ${pos.x}, y: ${pos.y}, z: ${pos.z} | (${labelOf(type)} Corpse)"
                )
                shareCooldown = SHARE_COOLDOWN_TICKS
            }
        }
    }

    /** Parse a corpse-loot chat line into its type, or null if it isn't one. */
    private fun lootMessageType(text: String): CorpseType? {
        val upper = text.uppercase()
        if (!upper.contains("CORPSE LOOT")) return null
        return when {
            upper.contains("LAPIS") -> CorpseType.LAPIS
            upper.contains("TUNGSTEN") -> CorpseType.TUNGSTEN
            upper.contains("UMBER") -> CorpseType.UMBER
            upper.contains("VANGUARD") -> CorpseType.VANGUARD
            else -> null
        }
    }

    /** Find the closest cached corpse of [type] to the player, drop its waypoint, and mark it opened. */
    private fun markNearestOpened(type: CorpseType) {
        val mc = MinecraftClient.getInstance()
        val player = mc.player ?: return
        var bestKey: Long? = null
        var bestDistSq = OPEN_RANGE_SQ
        for ((key, wp) in corpses) {
            if (wp.type != type) continue
            val c = wp.box.center
            val dx = c.x - player.x; val dy = c.y - player.y; val dz = c.z - player.z
            val d = dx * dx + dy * dy + dz * dz
            if (d < bestDistSq) {
                bestDistSq = d
                bestKey = key
            }
        }
        bestKey?.let {
            corpses.remove(it)
            openedKeys.add(it)
        }
    }

    /** Map a (visible) armor stand to its corpse type by its helmet's stripped name, or null. */
    private fun corpseTypeOf(stand: ArmorStandEntity): CorpseType? {
        val helmet = stand.getEquippedStack(EquipmentSlot.HEAD)
        if (helmet.isEmpty) return null
        return when (stripFormatting(helmet.name.string).trim()) {
            "Lapis Armor Helmet" -> CorpseType.LAPIS
            "Mineral Helmet" -> CorpseType.TUNGSTEN
            "Yog Helmet" -> CorpseType.UMBER
            "Vanguard Helmet" -> CorpseType.VANGUARD
            else -> null
        }
    }

    /** Whether this corpse type is currently enabled in config. */
    private fun isTypeEnabled(type: CorpseType): Boolean = when (type) {
        CorpseType.LAPIS -> Cfg.lapisEnabled
        CorpseType.TUNGSTEN -> Cfg.tungstenEnabled
        CorpseType.UMBER -> Cfg.umberEnabled
        CorpseType.VANGUARD -> Cfg.vanguardEnabled
    }

    /** Display label for the corpse type (used in the party-chat message). */
    private fun labelOf(type: CorpseType): String = when (type) {
        CorpseType.LAPIS -> "Lapis"
        CorpseType.TUNGSTEN -> "Tungsten"
        CorpseType.UMBER -> "Umber"
        CorpseType.VANGUARD -> "Vanguard"
    }

    /** The configured color for this corpse type (ARGB). */
    private fun colorOf(type: CorpseType): Int = when (type) {
        CorpseType.LAPIS -> Cfg.lapisColor
        CorpseType.TUNGSTEN -> Cfg.tungstenColor
        CorpseType.UMBER -> Cfg.umberColor
        CorpseType.VANGUARD -> Cfg.vanguardColor
    }

    /** The armor stand's standing hitbox at its (fixed) position, widened a touch like the reference. */
    private fun corpseBoxOf(stand: ArmorStandEntity): Box =
        stand.getDimensions(EntityPose.STANDING)
            .getBoxAt(Vec3d(stand.x, stand.y, stand.z))
            .expand(0.25, 0.0, 0.25)

    // ====================== Fossil Finder ======================

    /**
     * Drives the one-shot entry scan. Does nothing once this instance has been scanned; the heavy
     * work is a single one-shot block sweep done once per instance (see [scanFossils]).
     */
    private fun tickFossils(mc: MinecraftClient) {
        if (!Cfg.fossilEnabled) {
            // Disabled: drop results and re-arm so turning it back on re-scans this instance.
            if (fossils.isNotEmpty()) fossils = emptyList()
            scannedServer = null
            return
        }

        // Already finished a scan for the current instance — just keep waypoints in sync with
        // mining (drop blocks that became air, shrink/remove the affected fossils).
        if (scannedServer == HypixelLocation.serverName) {
            if (validateCooldown-- <= 0) {
                validateCooldown = VALIDATE_INTERVAL
                validateMinedFossils(mc)
            }
            return
        }

        // Not started yet: let chunks load in, then do the one-shot scan once.
        if (entryDelay-- > 0) return
        scanFossils(mc)
    }

    /**
     * Re-check the blocks of every known fossil against the (loaded) world and prune mined ones.
     * Cheap — total fossil blocks is small. A block whose chunk isn't loaded is left untouched so
     * fossils that merely went out of render distance aren't falsely cleared; only blocks that are
     * loaded *and* no longer quartz are removed. A fossil with no blocks left loses its waypoint.
     */
    private fun validateMinedFossils(mc: MinecraftClient) {
        val current = fossils
        if (current.isEmpty()) return
        val world: ClientWorld = mc.world ?: return

        var changed = false
        val survivors = ArrayList<Fossil>(current.size)
        val cursor = BlockPos.Mutable()
        for (fossil in current) {
            var mined = false
            val it = fossil.blocks.iterator()
            while (it.hasNext()) {
                val packed = it.next()
                val x = BlockPos.unpackLongX(packed)
                val y = BlockPos.unpackLongY(packed)
                val z = BlockPos.unpackLongZ(packed)
                if (!world.isChunkLoaded(x shr 4, z shr 4)) continue
                cursor.set(x, y, z)
                if (!isFossilBlock(world.getBlockState(cursor))) {
                    it.remove()
                    mined = true
                }
            }
            if (fossil.blocks.isEmpty()) {
                changed = true // fully mined: drop the waypoint
            } else {
                if (mined) {
                    fossil.box = boundingBoxOf(fossil.blocks)
                    changed = true
                }
                survivors.add(fossil)
            }
        }
        if (changed) fossils = survivors
    }

    /** Blocks that make up a fossil. Fossils are quartz structures (F3 shows `minecraft:quartz_block`). */
    private fun isFossilBlock(state: BlockState): Boolean = when (state.block) {
        Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_SLAB, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_PILLAR,
        Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK,
        Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_STAIRS -> true
        else -> false
    }

    /**
     * One straightforward block sweep of the cube within [Cfg.fossilRange] around the player, using
     * plain `world.getBlockState` (the simple, reliable approach). Runs once per instance on the
     * client thread; the range default is kept modest so the single pass doesn't hitch. Unloaded
     * blocks read as air, so out-of-range chunks cost nothing.
     */
    private fun scanFossils(mc: MinecraftClient) {
        val world: ClientWorld = mc.world ?: return
        val player = mc.player ?: return

        val r = Cfg.fossilRange
        val ox = MathHelper.floor(player.x)
        val oy = MathHelper.floor(player.y)
        val oz = MathHelper.floor(player.z)
        val worldBottom = world.bottomY
        val worldTop = worldBottom + world.height - 1
        val minY = (oy - r).coerceAtLeast(worldBottom)
        val maxY = (oy + r).coerceAtMost(worldTop)

        val hits = HashSet<Long>()
        val cursor = BlockPos.Mutable()
        for (x in (ox - r)..(ox + r)) {
            for (z in (oz - r)..(oz + r)) {
                for (y in minY..maxY) {
                    cursor.set(x, y, z)
                    if (isFossilBlock(world.getBlockState(cursor))) hits.add(cursor.asLong())
                }
            }
        }

        fossils = clusterize(hits, Cfg.fossilMinCluster)
        scannedServer = HypixelLocation.serverName
        validateCooldown = VALIDATE_INTERVAL
    }

    /**
     * Turn fossil block positions into fossils in two stages:
     *  1. flood-fill directly-touching (26-neighbour) blocks into raw components, then
     *  2. merge components that sit within [Cfg.fossilMergeDistance] of each other, so a fossil made
     *     of several near-but-not-touching quartz chunks shows as one box instead of many.
     * Only merged groups of at least [minSize] blocks are kept.
     */
    private fun clusterize(positions: Set<Long>, minSize: Int): List<Fossil> {
        if (positions.isEmpty()) return emptyList()

        // Stage 1: connected components of touching blocks.
        val remaining = HashSet(positions)
        val components = ArrayList<MutableSet<Long>>()
        val stack = ArrayDeque<Long>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.iterator().next()
            remaining.remove(seed)
            stack.addLast(seed)
            val members = HashSet<Long>()
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                members.add(cur)
                val cx = BlockPos.unpackLongX(cur)
                val cy = BlockPos.unpackLongY(cur)
                val cz = BlockPos.unpackLongZ(cur)
                for (a in -1..1) for (b in -1..1) for (c in -1..1) {
                    if (a == 0 && b == 0 && c == 0) continue
                    val n = BlockPos.asLong(cx + a, cy + b, cz + c)
                    if (remaining.remove(n)) stack.addLast(n)
                }
            }
            components.add(members)
        }

        // Stage 2: union-find merge of components whose bounding boxes are within the gap distance.
        val boxes = components.map { boundingBoxOf(it) }
        val parent = IntArray(components.size) { it }
        fun find(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; var c = i; while (parent[c] != c) { val n = parent[c]; parent[c] = r; c = n }; return r }
        val gap = Cfg.fossilMergeDistance.toDouble()
        for (i in components.indices) {
            for (j in i + 1 until components.size) {
                if (find(i) == find(j)) continue
                if (boxGap(boxes[i], boxes[j]) <= gap) parent[find(j)] = find(i)
            }
        }

        val grouped = HashMap<Int, MutableSet<Long>>()
        for (i in components.indices) {
            grouped.getOrPut(find(i)) { HashSet() }.addAll(components[i])
        }

        val result = ArrayList<Fossil>()
        for (blocks in grouped.values) {
            if (blocks.size >= minSize) result.add(Fossil(blocks, boundingBoxOf(blocks)))
        }
        return result
    }

    /** Axis-aligned block bounding box (full-block, so a single block is 1×1×1) of a position set. */
    private fun boundingBoxOf(blocks: Set<Long>): Box {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        for (p in blocks) {
            val x = BlockPos.unpackLongX(p); val y = BlockPos.unpackLongY(p); val z = BlockPos.unpackLongZ(p)
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        return Box(
            minX.toDouble(), minY.toDouble(), minZ.toDouble(),
            (maxX + 1).toDouble(), (maxY + 1).toDouble(), (maxZ + 1).toDouble(),
        )
    }

    /** Largest per-axis separation between two boxes (0 if they touch/overlap on every axis). */
    private fun boxGap(a: Box, b: Box): Double {
        val dx = maxOf(0.0, a.minX - b.maxX, b.minX - a.maxX)
        val dy = maxOf(0.0, a.minY - b.maxY, b.minY - a.maxY)
        val dz = maxOf(0.0, a.minZ - b.maxZ, b.minZ - a.maxZ)
        return maxOf(dx, dy, dz)
    }

    // ====================== Render ======================

    private fun render(ctx: WorldRenderContext) {
        val mc = MinecraftClient.getInstance()
        if (mc.world == null) return
        val matrices = ctx.matrices() ?: return
        val consumers = ctx.consumers() ?: return
        val cam = ctx.worldState().cameraRenderState.pos ?: return

        resetOnInstanceChange()

        val cachedFossils = fossils
        val drawCorpses = Cfg.corpseEnabled
        val drawFossils = Cfg.fossilEnabled && cachedFossils.isNotEmpty()
        if (!drawCorpses && !drawFossils) return

        // Anchor tracers to the camera (matches the nametag-ESP modules so F5 views stay correct).
        val needTracer = (drawCorpses && Cfg.tracers) || (drawFossils && Cfg.fossilTracers)
        val tracerOrigin: Vec3d? = if (needTracer) {
            val camera = mc.gameRenderer.camera
            val cp = camera.cameraPos
            val yawRad = Math.toRadians(camera.yaw.toDouble())
            val pitchRad = Math.toRadians(camera.pitch.toDouble())
            val cosPitch = cos(pitchRad)
            Vec3d(
                cp.x + -sin(yawRad) * cosPitch * 0.4,
                cp.y + -sin(pitchRad) * 0.4,
                cp.z + cos(yawRad) * cosPitch * 0.4,
            )
        } else null

        matrices.push()
        matrices.translate(-cam.x, -cam.y, -cam.z)

        if (drawCorpses) renderCorpses(matrices, consumers, tracerOrigin)
        if (drawFossils) renderFossils(matrices, consumers, cachedFossils, tracerOrigin)

        matrices.pop()
        RenderUtil.flush(consumers)
    }

    private fun renderCorpses(
        matrices: net.minecraft.client.util.math.MatrixStack,
        consumers: net.minecraft.client.render.VertexConsumerProvider,
        tracerOrigin: Vec3d?,
    ) {
        val mode = Cfg.mode
        val lineWidth = Cfg.lineWidth.toFloat()
        val through = Cfg.throughWalls
        val drawTracers = Cfg.tracers
        val tracerWidth = Cfg.tracerWidth.toFloat()

        for (wp in corpses.values) {
            if (!isTypeEnabled(wp.type)) continue

            val box = wp.box
            val color = colorOf(wp.type)

            when (mode) {
                HighlightMode.BOX ->
                    RenderUtil.drawBoxOutline(matrices, consumers, box, color, lineWidth, through)
                HighlightMode.FILLED_BOX -> {
                    val alpha = ((Cfg.fillOpacity / 100.0) * 255).toInt().coerceIn(0, 255)
                    val fill = (color and 0x00FFFFFF) or (alpha shl 24)
                    RenderUtil.drawFilledBox(matrices, consumers, box, fill, color, lineWidth, through)
                }
            }

            if (drawTracers && tracerOrigin != null) {
                RenderUtil.drawLine(matrices, consumers, tracerOrigin, box.center, color, tracerWidth, through)
            }
        }
    }

    private fun renderFossils(
        matrices: net.minecraft.client.util.math.MatrixStack,
        consumers: net.minecraft.client.render.VertexConsumerProvider,
        list: List<Fossil>,
        tracerOrigin: Vec3d?,
    ) {
        val mode = Cfg.fossilMode
        val color = Cfg.fossilColor
        val lineWidth = Cfg.fossilLineWidth.toFloat()
        val through = Cfg.fossilThroughWalls
        val drawTracers = Cfg.fossilTracers
        val tracerWidth = Cfg.fossilTracerWidth.toFloat()

        for (fossil in list) {
            val box = fossil.box
            when (mode) {
                HighlightMode.BOX ->
                    RenderUtil.drawBoxOutline(matrices, consumers, box, color, lineWidth, through)
                HighlightMode.FILLED_BOX -> {
                    val alpha = ((Cfg.fossilFillOpacity / 100.0) * 255).toInt().coerceIn(0, 255)
                    val fill = (color and 0x00FFFFFF) or (alpha shl 24)
                    RenderUtil.drawFilledBox(matrices, consumers, box, fill, color, lineWidth, through)
                }
            }
            if (drawTracers && tracerOrigin != null) {
                RenderUtil.drawLine(matrices, consumers, tracerOrigin, box.center, color, tracerWidth, through)
            }
        }
    }

    /** Strip Minecraft formatting codes (§ + one char). */
    private fun stripFormatting(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if ((c == '§' || c.code == 167) && i + 1 < input.length) {
                i += 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private companion object {
        /** You open a corpse from right next to it, so only consider stands within this radius². */
        const val OPEN_RANGE_SQ = 6.0 * 6.0

        /** ~0.75s at 20 TPS between party-chat shares, so a burst of corpses can't trip the spam filter. */
        const val SHARE_COOLDOWN_TICKS = 15

        /** ~0.5s at 20 TPS after entering before snapshotting, so nearby chunks have arrived. */
        const val ENTRY_SCAN_DELAY_TICKS = 10

        /** ~0.5s at 20 TPS between mined-fossil re-checks; cheap since total fossil blocks is small. */
        const val VALIDATE_INTERVAL = 10
    }
}
