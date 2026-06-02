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
import net.minecraft.world.chunk.ChunkStatus
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

    /** Entity ids of corpses the player has opened (loot message seen); skipped while highlighting. */
    private val openedCorpses: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Entity ids already announced to party chat, so each corpse is shared at most once. */
    private val sharedCorpses: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Server we last saw a corpse on — when it changes (warp to a new instance) we drop stale state. */
    @Volatile private var lastServerName: String? = null

    /** Ticks until the next party-chat share is allowed, to stay under Hypixel's chat rate limit. */
    private var shareCooldown = 0

    // ---- Fossil state ---------------------------------------------------------
    //
    // Fossils are scanned exactly once per mineshaft instance: when you enter, we wait briefly for
    // chunks to load, then sweep every loaded chunk around you a few chunks per tick (so there's no
    // frame hitch), cluster the hits, and stop. We don't scan again until you enter another
    // mineshaft (detected by the server name changing in [resetOnInstanceChange]).

    /**
     * A detected fossil: the quartz block positions that make it up plus their bounding box. We
     * keep the blocks (not just the box) so we can shrink/drop the waypoint as they're mined,
     * without re-running the chunk scan.
     */
    private class Fossil(val blocks: MutableSet<Long>, @Volatile var box: Box)

    /** Detected fossils. Set once when the entry scan finishes, then pruned as blocks are mined. */
    @Volatile private var fossils: List<Fossil> = emptyList()

    /** Server name we've already completed a scan for; `== current` means "don't scan again". */
    private var scannedServer: String? = null

    /** True while a background scan is running, so we don't launch a second one. */
    @Volatile private var scanning = false

    /** Result handed back from the background scan thread; applied on the next client tick. */
    @Volatile private var scanResult: ScanResult? = null

    /** Ticks to wait after entering before scanning, so nearby chunks have arrived. */
    private var entryDelay = 0

    /** Ticks until the next mined-block re-validation of existing fossils. */
    private var validateCooldown = 0

    /** Background scan output, tagged with the instance it was for so a stale result is ignored. */
    private class ScanResult(val server: String?, val fossils: List<Fossil>)

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
            if (Cfg.corpseEnabled && Cfg.shareToParty) shareNewCorpses(mc)
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
     * Entity ids are per-server; warping to another mineshaft instance reuses them, so stale
     * "opened"/"shared" ids could hide or skip a fresh corpse. Drop per-instance state when the
     * server name changes.
     */
    private fun resetOnInstanceChange() {
        val server = HypixelLocation.serverName
        if (server != lastServerName) {
            lastServerName = server
            openedCorpses.clear()
            sharedCorpses.clear()
            // New mineshaft instance: forget old fossils and arm a fresh one-shot scan.
            fossils = emptyList()
            scannedServer = null
            scanResult = null
            scanning = false
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
     * Announce up to one newly-found corpse per cooldown to party chat via `/pc`. Only runs while
     * another player shares the mineshaft (solo runs stay silent). Mirrors the highlight gating:
     * only enabled types, in range, not already opened or shared.
     */
    private fun shareNewCorpses(mc: MinecraftClient) {
        if (shareCooldown-- > 0) return
        val world = mc.world ?: return
        val player = mc.player ?: return
        if (!hasOtherPlayers(mc)) return

        val rangeSq = Cfg.range.toDouble() * Cfg.range.toDouble()
        for (entity in world.entities) {
            if (entity !is ArmorStandEntity || entity.isRemoved || entity.isInvisible) continue
            val id = entity.id
            if (id in sharedCorpses || id in openedCorpses) continue
            if (entity.squaredDistanceTo(player) > rangeSq) continue
            val type = corpseTypeOf(entity) ?: continue
            if (!isTypeEnabled(type)) continue

            val pos = entity.blockPos
            val msg = "x: ${pos.x}, y: ${pos.y}, z: ${pos.z} | (${labelOf(type)} Corpse)"
            mc.player?.networkHandler?.sendChatCommand("pc $msg")
            sharedCorpses.add(id)
            shareCooldown = SHARE_COOLDOWN_TICKS
            return // one per cooldown
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

    /** Find the closest visible armor stand of [type] to the player and remember it as opened. */
    private fun markNearestOpened(type: CorpseType) {
        val mc = MinecraftClient.getInstance()
        val world = mc.world ?: return
        val player = mc.player ?: return
        var best: ArmorStandEntity? = null
        var bestDistSq = OPEN_RANGE_SQ
        for (entity in world.entities) {
            if (entity !is ArmorStandEntity || entity.isRemoved || entity.isInvisible) continue
            if (entity.id in openedCorpses) continue
            if (corpseTypeOf(entity) != type) continue
            val d = entity.squaredDistanceTo(player)
            if (d < bestDistSq) {
                bestDistSq = d
                best = entity
            }
        }
        best?.let { openedCorpses.add(it.id) }
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

    /** The armor stand's standing hitbox at its interpolated position, widened a touch like the reference. */
    private fun interpolatedBox(stand: ArmorStandEntity, tickDelta: Float): Box {
        val x = MathHelper.lerp(tickDelta.toDouble(), stand.lastRenderX, stand.x)
        val y = MathHelper.lerp(tickDelta.toDouble(), stand.lastRenderY, stand.y)
        val z = MathHelper.lerp(tickDelta.toDouble(), stand.lastRenderZ, stand.z)
        return stand.getDimensions(EntityPose.STANDING)
            .getBoxAt(Vec3d(x, y, z))
            .expand(0.25, 0.0, 0.25)
    }

    // ====================== Fossil Finder ======================

    /**
     * Drives the one-shot entry scan. Does nothing once this instance has been scanned; the heavy
     * work runs on a background thread (see [launchScan]) and its result is applied here.
     */
    private fun tickFossils(mc: MinecraftClient) {
        if (!Cfg.fossilEnabled) {
            // Disabled: drop results and re-arm so turning it back on re-scans this instance.
            if (fossils.isNotEmpty()) fossils = emptyList()
            scanResult = null
            scanning = false
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

        // A background scan finished — apply it (if it's still for this instance) on the main thread.
        val result = scanResult
        if (result != null) {
            scanResult = null
            scanning = false
            if (result.server == HypixelLocation.serverName) {
                fossils = result.fossils
                scannedServer = HypixelLocation.serverName
                validateCooldown = VALIDATE_INTERVAL
            }
            return
        }

        if (scanning) return // background scan in progress

        // Not started yet: let chunks load in, then snapshot + launch the async scan once.
        if (entryDelay-- > 0) return
        launchScan(mc)
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
     * Collect fossil block positions on the main thread (cheap: only sections that actually contain
     * quartz, found via a palette `hasAny` check, are read at all), then hand the positions to a
     * background thread that does the pure-CPU clustering. No world/chunk access happens off-thread,
     * so there's no race — and reading just the quartz sections is fast enough to not hitch a frame.
     */
    private fun launchScan(mc: MinecraftClient) {
        val world: ClientWorld = mc.world ?: return
        val player = mc.player ?: return
        val server = HypixelLocation.serverName
        val r = Cfg.fossilRange
        val rChunks = (r + 15) / 16
        val pcx = MathHelper.floor(player.x) shr 4
        val pcz = MathHelper.floor(player.z) shr 4

        val py = MathHelper.floor(player.y)
        val worldBottom = world.bottomY
        val worldTop = worldBottom + world.height - 1
        val minY = (py - r).coerceAtLeast(worldBottom)
        val maxY = (py + r).coerceAtMost(worldTop)
        val bottomSection = worldBottom shr 4
        val firstSec = (minY shr 4) - bottomSection
        val lastSec = (maxY shr 4) - bottomSection

        val hits = HashSet<Long>()
        for (cx in (pcx - rChunks)..(pcx + rChunks)) {
            for (cz in (pcz - rChunks)..(pcz + rChunks)) {
                if (!world.isChunkLoaded(cx, cz)) continue
                val chunk = world.chunkManager.getChunk(cx, cz, ChunkStatus.FULL, false) ?: continue
                val sections = chunk.sectionArray
                for (i in firstSec.coerceAtLeast(0)..lastSec.coerceAtMost(sections.size - 1)) {
                    val section = sections[i]
                    if (section.isEmpty) continue
                    if (!section.blockStateContainer.hasAny { isFossilBlock(it) }) continue
                    val baseX = cx shl 4
                    val baseY = (bottomSection + i) shl 4
                    val baseZ = cz shl 4
                    for (lx in 0..15) for (ly in 0..15) for (lz in 0..15) {
                        val y = baseY + ly
                        if (y < minY || y > maxY) continue
                        if (isFossilBlock(section.getBlockState(lx, ly, lz))) {
                            hits.add(BlockPos.asLong(baseX + lx, y, baseZ + lz))
                        }
                    }
                }
            }
        }

        // Nothing with quartz nearby — finish immediately, no thread needed.
        if (hits.isEmpty()) {
            fossils = emptyList()
            scannedServer = server
            validateCooldown = VALIDATE_INTERVAL
            return
        }

        scanning = true
        val minCluster = Cfg.fossilMinCluster
        Thread({
            val result = try {
                clusterize(hits, minCluster)
            } catch (t: Throwable) {
                t.printStackTrace()
                emptyList()
            }
            scanResult = ScanResult(server, result)
        }, "lasseclient-fossil-scan").apply { isDaemon = true }.start()
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
        val world = mc.world ?: return
        val player = mc.player ?: return
        val matrices = ctx.matrices() ?: return
        val consumers = ctx.consumers() ?: return
        val cam = ctx.worldState().cameraRenderState.pos ?: return
        val tickDelta = mc.renderTickCounter.getTickProgress(false)

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

        if (drawCorpses) renderCorpses(mc, world, player, matrices, consumers, tickDelta, tracerOrigin)
        if (drawFossils) renderFossils(matrices, consumers, cachedFossils, tracerOrigin)

        matrices.pop()
        RenderUtil.flush(consumers)
    }

    private fun renderCorpses(
        mc: MinecraftClient,
        world: ClientWorld,
        player: net.minecraft.entity.player.PlayerEntity,
        matrices: net.minecraft.client.util.math.MatrixStack,
        consumers: net.minecraft.client.render.VertexConsumerProvider,
        tickDelta: Float,
        tracerOrigin: Vec3d?,
    ) {
        val rangeSq = Cfg.range.toDouble() * Cfg.range.toDouble()
        val mode = Cfg.mode
        val lineWidth = Cfg.lineWidth.toFloat()
        val through = Cfg.throughWalls
        val drawTracers = Cfg.tracers
        val tracerWidth = Cfg.tracerWidth.toFloat()

        for (entity in world.entities) {
            if (entity !is ArmorStandEntity || entity.isRemoved || entity.isInvisible) continue
            if (entity.squaredDistanceTo(player) > rangeSq) continue
            if (Cfg.hideOpened && entity.id in openedCorpses) continue
            val type = corpseTypeOf(entity) ?: continue
            if (!isTypeEnabled(type)) continue

            val box = interpolatedBox(entity, tickDelta)
            val color = colorOf(type)

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
