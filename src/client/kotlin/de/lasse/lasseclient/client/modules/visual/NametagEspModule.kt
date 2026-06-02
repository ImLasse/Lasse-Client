package de.lasse.lasseclient.client.modules.visual

import de.lasse.lasseclient.client.event.EventBus
import de.lasse.lasseclient.client.event.NameChangeEvent
import de.lasse.lasseclient.client.event.PacketReceivedEvent
import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.client.module.Module
import de.lasse.lasseclient.client.render.RenderUtil
import de.lasse.lasseclient.config.HighlightMode
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Highlights Hypixel SkyBlock mobs by reading nametags on nearby armor stands.
 *
 * Detection (mirrors Devonian's `BoxStarMob` pattern):
 *  1. We observe S2C `EntitySpawnS2CPacket` via mixin. When an ARMOR_STAND spawns, we
 *     stash its entity id in [lastStandId].
 *  2. We observe `NameChangeEvent` (fired from a CUSTOM_NAME tracked-data update via
 *     mixin). If the changed entity matches [lastStandId] and the stripped name matches
 *     [matchesName], we queue `(standId, name)` for resolution.
 *  3. On client tick we drain the queue: the real mob's entity id is `standId - N` for
 *     small N — Hypixel spawns the mob first, the nametag stand right after, in the same
 *     tick, so ids are adjacent and unambiguous.
 *  4. On `EntitiesDestroyS2CPacket` we evict tracked mobs whose ids appear in the packet.
 *  5. On world change we clear all state.
 *
 * No packet is modified or cancelled anywhere — all mixins are HEAD-injected observers.
 *
 * Subclasses decide which nametags to track by implementing [matchesName], and supply their
 * ESP style by implementing the `cfg*` accessors (each subclass has its own Resourceful Config
 * category, so the common settings can't live here).
 */
@Environment(EnvType.CLIENT)
abstract class NametagEspModule(
    name: String,
    description: String,
    category: Category = Category.VISUAL,
) : Module(name, description, category) {

    // ---- Config accessors (backed by each subclass's RC category) -------------

    protected abstract fun cfgMode(): HighlightMode
    protected abstract fun cfgColor(): Int
    protected abstract fun cfgLineWidth(): Double
    protected abstract fun cfgFillOpacity(): Int
    protected abstract fun cfgThroughWalls(): Boolean
    protected abstract fun cfgRange(): Int
    protected abstract fun cfgTracers(): Boolean
    protected abstract fun cfgTracerColor(): Int
    protected abstract fun cfgTracerWidth(): Double
    protected abstract fun cfgMaxIdOffset(): Int

    /** Decide whether a stripped nametag belongs to a mob this module should highlight. */
    protected abstract fun matchesName(cleaned: String): Boolean

    /**
     * Whether the module should actually detect and render this tick. Defaults to the plain
     * [enabled] flag; subclasses can override to add extra gating (e.g. only on a specific
     * Hypixel location). When this is false the tick/name/render work is skipped entirely.
     */
    protected open fun isActive(): Boolean = enabled

    /**
     * Subclass returns a value representing its current match rules (e.g. the editable name list,
     * copied). When it changes between ticks we drop tracked mobs that no longer match and re-scan
     * present armor stands so newly-matching mobs are picked up at once. Default `null` = fixed
     * rules that never change at runtime.
     */
    protected open fun matchRuleSnapshot(): Any? = null

    /** Called once when a matching mob is first detected (newly spawned/resolved). */
    protected open fun onMobMatched(name: String) {}

    /**
     * Minimum box dimensions in blocks. Some SkyBlock mobs (e.g. Garden pests, which are
     * silverfish with a floating display model) have a hitbox far smaller than what's drawn
     * on screen, so the raw entity box is a barely-visible sliver. A non-zero minimum
     * inflates the box to cover the visible model. 0 = use the entity's real hitbox.
     */
    protected open val minBoxWidth: Double = 0.0
    protected open val minBoxHeight: Double = 0.0

    // ---- Detection state ------------------------------------------------------

    private val rawNameChanges = ConcurrentLinkedQueue<Pair<Int, String>>()
    private val pairedStandIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val queuedStandIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val pendingResolutions = ConcurrentLinkedQueue<Pair<Int, String>>()
    private val matchedMobs = ConcurrentHashMap<UUID, MatchedMob>()
    private var sweepCooldown = 0

    /** Set when match criteria change (e.g. the name list) so tracked mobs get re-evaluated. */
    @Volatile private var matchRulesDirty = false
    private var lastMatchSnapshot: Any? = null

    private class MatchedMob(
        val uuid: UUID,
        val matchedName: String,
        @Volatile var entity: LivingEntity?,
        @Volatile var lastX: Double,
        @Volatile var lastY: Double,
        @Volatile var lastZ: Double,
        @Volatile var width: Float,
        @Volatile var height: Float,
        @Volatile var unloadedTicks: Int = 0,
    )

    // ---- Lifecycle ------------------------------------------------------------

    init {
        // Render hook — END_MAIN runs after the main scene pass; matches the original code.
        WorldRenderEvents.END_MAIN.register(WorldRenderEvents.EndMain { ctx ->
            if (isActive()) renderHighlights(ctx)
        })

        // Drain queues + maintenance on the client thread.
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            if (!isActive()) return@EndTick
            val snap = matchRuleSnapshot()
            if (snap != lastMatchSnapshot) {
                lastMatchSnapshot = snap
                matchRulesDirty = true
            }
            if (matchRulesDirty) {
                matchRulesDirty = false
                reevaluateMatchRules()
            }
            sweepExistingStands(mc)
            drainRawNameChanges(mc)
            drainPendingResolutions(mc)
            refreshMatchedMobs(mc)
        })

        // Packet observer: only forget the stand pairing on destroy, never the mob itself.
        // Chunk unloads also send destroy packets, so dropping a mob here would defeat the
        // "remember mobs across unload" behavior we want.
        EventBus.on<PacketReceivedEvent> { event ->
            val packet = event.packet
            if (packet is EntitiesDestroyS2CPacket) {
                val ids = packet.entityIds
                if (ids.isEmpty()) return@on
                val it = ids.intIterator()
                while (it.hasNext()) {
                    val id = it.nextInt()
                    pairedStandIds.remove(id)
                    queuedStandIds.remove(id)
                }
            }
        }

        // Name observer: just buffer the raw event. Lookup happens on the client thread to
        // avoid touching ClientWorld's entity table from the network thread.
        EventBus.on<NameChangeEvent> { event ->
            if (!isActive()) return@on
            if (event.name.isEmpty()) return@on
            rawNameChanges.add(event.entityId to event.name)
        }
    }

    // ---- Resolution -----------------------------------------------------------

    /**
     * Re-apply [matchesName] to everything we're tracking after the rules changed. Mobs whose
     * stored nametag no longer matches are dropped instantly; the pairing caches are cleared and
     * the sweep is forced to run next tick so present stands that now match get re-detected.
     */
    private fun reevaluateMatchRules() {
        val stale = matchedMobs.values.filter { !matchesName(it.matchedName) }.map { it.uuid }
        for (id in stale) matchedMobs.remove(id)

        pairedStandIds.clear()
        queuedStandIds.clear()
        pendingResolutions.clear()
        sweepCooldown = 0
    }

    /**
     * Periodically scan armor stands already in the world. The spawn/name-change observers only
     * fire for stands that appear (or are renamed) *after* the module is watching, so a mob that
     * was already present when the module is enabled — or whose name is added to the list at
     * runtime — would otherwise never be detected. Non-matching stands are intentionally not
     * remembered, so a later name-list change re-evaluates them on the next sweep.
     */
    private fun sweepExistingStands(mc: MinecraftClient) {
        if (sweepCooldown-- > 0) return
        sweepCooldown = SWEEP_INTERVAL_TICKS
        val world = mc.world ?: return
        for (ent in world.entities) {
            if (ent !is ArmorStandEntity) continue
            val id = ent.id
            if (id in pairedStandIds || id in queuedStandIds) continue
            val nameText = ent.customName ?: continue
            val cleaned = stripFormatting(nameText.string)
            if (cleaned.isEmpty()) continue
            if (!matchesName(cleaned)) continue
            queuedStandIds.add(id)
            pendingResolutions.add(id to cleaned)
        }
    }

    private fun drainRawNameChanges(mc: MinecraftClient) {
        val world = mc.world ?: run { rawNameChanges.clear(); return }
        while (true) {
            val (entityId, rawName) = rawNameChanges.poll() ?: break
            if (entityId in pairedStandIds) continue
            val ent = world.getEntityById(entityId) ?: continue
            if (ent !is ArmorStandEntity) continue
            val cleaned = stripFormatting(rawName)
            if (!matchesName(cleaned)) continue
            if (!queuedStandIds.add(entityId)) continue
            pendingResolutions.add(entityId to cleaned)
        }
    }

    private fun drainPendingResolutions(mc: MinecraftClient) {
        val world = mc.world ?: return
        val maxOffset = cfgMaxIdOffset().coerceAtLeast(1)

        val snapshot = mutableListOf<Pair<Int, String>>()
        while (true) snapshot += pendingResolutions.poll() ?: break

        for (entry in snapshot) {
            val (standId, name) = entry
            var resolved: LivingEntity? = null
            for (offset in 1..maxOffset) {
                val candidate = world.getEntityById(standId - offset) as? LivingEntity ?: continue
                if (candidate is ArmorStandEntity) continue
                resolved = candidate
                break
            }
            if (resolved != null) {
                val uuid = resolved.uuid
                if (!matchedMobs.containsKey(uuid)) {
                    matchedMobs[uuid] = MatchedMob(
                        uuid = uuid,
                        matchedName = name,
                        entity = resolved,
                        lastX = resolved.x,
                        lastY = resolved.y,
                        lastZ = resolved.z,
                        width = resolved.width,
                        height = resolved.height,
                    )
                    try { onMobMatched(name) } catch (t: Throwable) { t.printStackTrace() }
                }
                pairedStandIds += standId
                queuedStandIds.remove(standId)
            } else {
                // Mob entity may not have been added to the client world yet — retry next tick.
                pendingResolutions.add(entry)
            }
        }
    }

    private fun refreshMatchedMobs(mc: MinecraftClient) {
        val world = mc.world ?: run {
            matchedMobs.clear()
            pairedStandIds.clear()
            queuedStandIds.clear()
            rawNameChanges.clear()
            pendingResolutions.clear()
            return
        }
        if (matchedMobs.isEmpty()) return

        val needsLookup = mutableListOf<MatchedMob>()
        val deadNow = mutableListOf<UUID>()
        for (m in matchedMobs.values) {
            val e = m.entity
            if (e != null && !e.isRemoved && world.getEntityById(e.id) === e) {
                if (e.isDead || e.deathTime > 0 || e.health <= 0f) {
                    deadNow += m.uuid
                    continue
                }
                m.lastX = e.x
                m.lastY = e.y
                m.lastZ = e.z
                m.width = e.width
                m.height = e.height
                m.unloadedTicks = 0
            } else {
                m.entity = null
                needsLookup.add(m)
            }
        }
        for (id in deadNow) matchedMobs.remove(id)

        if (needsLookup.isNotEmpty()) {
            val byUuid = HashMap<UUID, LivingEntity>(needsLookup.size * 2)
            for (ent in world.entities) {
                if (ent is LivingEntity && ent !is ArmorStandEntity && !ent.isRemoved) {
                    if (matchedMobs.containsKey(ent.uuid)) byUuid[ent.uuid] = ent
                }
            }
            for (m in needsLookup) {
                val e = byUuid[m.uuid]
                if (e != null) {
                    m.entity = e
                    m.lastX = e.x
                    m.lastY = e.y
                    m.lastZ = e.z
                    m.width = e.width
                    m.height = e.height
                    m.unloadedTicks = 0
                } else {
                    m.unloadedTicks++
                }
            }
        }

        val player = mc.player
        if (player != null) {
            val rng = cfgRange().toDouble()
            val rangeSq = rng * rng
            val toRemove = mutableListOf<UUID>()
            for (m in matchedMobs.values) {
                if (m.entity != null) continue
                if (m.unloadedTicks < UNLOADED_DEATH_GRACE_TICKS) continue
                val dx = m.lastX - player.x
                val dy = m.lastY - player.y
                val dz = m.lastZ - player.z
                if (dx * dx + dy * dy + dz * dz <= rangeSq) toRemove += m.uuid
            }
            for (id in toRemove) matchedMobs.remove(id)
        }
    }

    private companion object {
        /** ~1s at 20 TPS — enough to swallow a destroy/respawn flicker but feel instant on real deaths. */
        const val UNLOADED_DEATH_GRACE_TICKS = 20

        /** ~0.5s at 20 TPS between full scans for already-present armor stands. */
        const val SWEEP_INTERVAL_TICKS = 10
    }

    // ---- Render ---------------------------------------------------------------

    private fun renderHighlights(ctx: WorldRenderContext) {
        if (matchedMobs.isEmpty()) return

        val mc = MinecraftClient.getInstance()
        val player = mc.player ?: return
        val matrices = ctx.matrices() ?: return
        val consumers = ctx.consumers() ?: return
        val cam = ctx.worldState().cameraRenderState.pos ?: return
        val tickDelta = mc.renderTickCounter.getTickProgress(false)

        val rng = cfgRange().toDouble()
        val rangeSq = rng * rng
        val currentMode = cfgMode()
        val baseColor = cfgColor()
        val outlineWidth = cfgLineWidth().toFloat()
        val through = cfgThroughWalls()
        val drawTracers = cfgTracers()
        val tColor = cfgTracerColor()
        val tWidth = cfgTracerWidth().toFloat()

        // Anchor tracers to the actual camera, not the player's eye: in third person (F5) the
        // camera sits behind/in-front-of the player, so a player-eye origin makes lines emanate
        // from the wrong spot (and F5-front even reverses the player's look vector).
        val tracerOrigin = if (drawTracers) {
            val camera = mc.gameRenderer.camera
            val cp = camera.cameraPos
            val yawRad = Math.toRadians(camera.yaw.toDouble())
            val pitchRad = Math.toRadians(camera.pitch.toDouble())
            val cosPitch = cos(pitchRad)
            val fx = -sin(yawRad) * cosPitch
            val fy = -sin(pitchRad)
            val fz = cos(yawRad) * cosPitch
            Vec3d(cp.x + fx * 0.4, cp.y + fy * 0.4, cp.z + fz * 0.4)
        } else null

        matrices.push()
        matrices.translate(-cam.x, -cam.y, -cam.z)

        for (m in matchedMobs.values) {
            val live = m.entity?.takeIf { !it.isRemoved }
            val dx = (live?.x ?: m.lastX) - player.x
            val dy = (live?.y ?: m.lastY) - player.y
            val dz = (live?.z ?: m.lastZ) - player.z
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue

            val box = if (live != null) interpolatedBox(live, tickDelta) else cachedBox(m)

            when (currentMode) {
                HighlightMode.BOX -> {
                    RenderUtil.drawBoxOutline(matrices, consumers, box, baseColor, outlineWidth, through)
                }
                HighlightMode.FILLED_BOX -> {
                    val alpha = ((cfgFillOpacity() / 100.0) * 255).toInt().coerceIn(0, 255)
                    val fillColor = (baseColor and 0x00FFFFFF) or (alpha shl 24)
                    RenderUtil.drawFilledBox(matrices, consumers, box, fillColor, baseColor, outlineWidth, through)
                }
            }

            if (drawTracers && tracerOrigin != null) {
                RenderUtil.drawLine(matrices, consumers, tracerOrigin, box.center, tColor, tWidth, through)
            }
        }

        matrices.pop()

        RenderUtil.flush(consumers)
    }

    private fun cachedBox(m: MatchedMob): Box {
        val w = max(m.width.toDouble(), minBoxWidth) / 2.0
        val h = max(m.height.toDouble(), minBoxHeight)
        return Box(m.lastX - w, m.lastY, m.lastZ - w, m.lastX + w, m.lastY + h, m.lastZ + w).expand(0.05)
    }

    private fun interpolatedBox(entity: Entity, tickDelta: Float): Box {
        val x = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderX, entity.x)
        val y = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderY, entity.y)
        val z = MathHelper.lerp(tickDelta.toDouble(), entity.lastRenderZ, entity.z)
        val w = max(entity.width.toDouble(), minBoxWidth) / 2.0
        val h = max(entity.height.toDouble(), minBoxHeight)
        return Box(x - w, y, z - w, x + w, y + h, z + w).expand(0.05)
    }

    /** Strip Minecraft formatting codes (§ + one char). */
    protected fun stripFormatting(input: String): String {
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
}
