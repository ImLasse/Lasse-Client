package de.lasse.lasseclient.client.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.RenderSetup
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.render.VertexRendering
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

/**
 * World-space ESP drawing helpers for the 1.21.11 render system.
 *
 * Through-walls layers are built at mod init from the same snippets vanilla uses
 * (RENDERTYPE_LINES_SNIPPET, POSITION_COLOR_SNIPPET), with depth-test overridden to
 * NO_DEPTH_TEST. They are registered via `RenderPipelines.register` so their shaders get
 * compiled during the first resource reload.
 *
 * These references are all *direct* (no reflection): the runtime can use intermediary mappings
 * (`net.minecraft.class_310` etc.), and Fabric only remaps direct compiled references — string
 * literals passed to `Class.forName`/`getDeclaredField` are NOT remapped and blow up with
 * `ClassNotFoundException`, silently dropping us back to depth-tested layers.
 *
 * Why custom layers at all: in 1.21.11 `RenderLayers.LINES_TRANSLUCENT` is misleadingly named —
 * it only disables depth *write*, not depth *test*, so lines drawn with it cannot be seen
 * through walls. There is no public through-walls layer for either lines or quads.
 */
@Environment(EnvType.CLIENT)
object RenderUtil {

    @Volatile private var linesThroughWalls: RenderLayer? = null
    @Volatile private var filledThroughWalls: RenderLayer? = null
    @Volatile private var attemptedInit: Boolean = false

    /** Call from ClientModInitializer.onInitializeClient. Safe to call repeatedly. */
    fun initialize() {
        if (attemptedInit) return
        attemptedInit = true

        // `withLocation(String)` takes only the path part of an Identifier (namespace is fixed
        // to "minecraft" via Identifier.ofVanilla). Path must match [a-z0-9/._-] — no colons.
        linesThroughWalls = tryBuildThroughLayer(
            snippet = RenderPipelines.RENDERTYPE_LINES_SNIPPET,
            drawMode = null,
            pipelineLocation = "pipeline/lasseclient_lines_through",
            layerName = "lasseclient_lines_through",
        )
        filledThroughWalls = tryBuildThroughLayer(
            snippet = RenderPipelines.POSITION_COLOR_SNIPPET,
            drawMode = VertexFormat.DrawMode.QUADS,
            pipelineLocation = "pipeline/lasseclient_filled_through",
            layerName = "lasseclient_filled_through",
        )

        println(
            "[LasseClient] Through-walls layers: " +
                "lines=${if (linesThroughWalls != null) "OK" else "FAIL"}, " +
                "filled=${if (filledThroughWalls != null) "OK" else "FAIL"}"
        )
    }

    /**
     * Build a no-depth-test variant of an existing vanilla pipeline snippet, register it, and
     * wrap in a RenderLayer. Returns null (and logs) on any failure so callers can fall back.
     */
    private fun tryBuildThroughLayer(
        snippet: RenderPipeline.Snippet,
        drawMode: VertexFormat.DrawMode?,
        pipelineLocation: String,
        layerName: String,
    ): RenderLayer? = runCatching {
        val builder = RenderPipeline.builder(snippet)
            .withLocation(pipelineLocation)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)

        // Fills need an explicit draw mode; lines inherit it from their snippet.
        if (drawMode != null) {
            builder.withVertexFormat(VertexFormats.POSITION_COLOR, drawMode)
        }

        val pipeline = builder.build()

        // Register so shader compilation picks it up during resource reload.
        RenderPipelines.register(pipeline)

        val setup = RenderSetup.builder(pipeline).translucent().build()
        RenderLayer.of(layerName, setup)
    }.onFailure {
        System.err.println("[LasseClient] Failed to build through-walls layer '$layerName': ${it.message}")
        it.printStackTrace()
    }.getOrNull()

    /**
     * Flush buffered ESP geometry from the world immediate *now*.
     *
     * Must be called at the end of an END_MAIN render callback. `ctx.consumers()` is the entity
     * immediate, which the world renderer otherwise drains during the entity pass — *before* water
     * and translucent terrain. Our through-walls layers disable depth *test*, so the GPU never
     * rejects the fragments, but if the geometry is painted that early it still gets *overpainted*
     * by water/translucent terrain drawn afterwards, making boxes vanish behind walls. Draining
     * here paints our boxes after the opaque/translucent world, so they stay visible through walls.
     */
    fun flush(consumers: VertexConsumerProvider) {
        (consumers as? VertexConsumerProvider.Immediate)?.draw()
    }

    fun drawBoxOutline(
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        box: Box,
        argb: Int,
        lineWidth: Float = 1f,
        throughWalls: Boolean = true,
    ) {
        val layer = when {
            throughWalls -> linesThroughWalls ?: RenderLayers.LINES_TRANSLUCENT
            else -> RenderLayers.LINES
        }
        val consumer = consumers.getBuffer(layer)
        val sizeShape = VoxelShapes.cuboid(
            0.0, 0.0, 0.0,
            box.maxX - box.minX,
            box.maxY - box.minY,
            box.maxZ - box.minZ,
        )
        VertexRendering.drawOutline(
            matrices, consumer, sizeShape,
            box.minX, box.minY, box.minZ,
            argb, lineWidth,
        )
    }

    fun drawFilledBox(
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        box: Box,
        fillArgb: Int,
        outlineArgb: Int,
        outlineWidth: Float = 1f,
        throughWalls: Boolean = true,
    ) {
        val a = ((fillArgb ushr 24) and 0xFF) / 255f
        val r = ((fillArgb ushr 16) and 0xFF) / 255f
        val g = ((fillArgb ushr 8) and 0xFF) / 255f
        val b = (fillArgb and 0xFF) / 255f

        val entry = matrices.peek()
        val fillLayer = when {
            throughWalls -> filledThroughWalls ?: RenderLayers.debugQuads()
            else -> RenderLayers.debugQuads()
        }
        val consumer = consumers.getBuffer(fillLayer)

        fun v(x: Double, y: Double, z: Double) {
            consumer.vertex(entry, x.toFloat(), y.toFloat(), z.toFloat()).color(r, g, b, a)
        }

        // Bottom (-Y)
        v(box.minX, box.minY, box.minZ); v(box.maxX, box.minY, box.minZ)
        v(box.maxX, box.minY, box.maxZ); v(box.minX, box.minY, box.maxZ)
        // Top (+Y)
        v(box.minX, box.maxY, box.minZ); v(box.minX, box.maxY, box.maxZ)
        v(box.maxX, box.maxY, box.maxZ); v(box.maxX, box.maxY, box.minZ)
        // North (-Z)
        v(box.minX, box.maxY, box.minZ); v(box.maxX, box.maxY, box.minZ)
        v(box.maxX, box.minY, box.minZ); v(box.minX, box.minY, box.minZ)
        // South (+Z)
        v(box.minX, box.minY, box.maxZ); v(box.maxX, box.minY, box.maxZ)
        v(box.maxX, box.maxY, box.maxZ); v(box.minX, box.maxY, box.maxZ)
        // West (-X)
        v(box.minX, box.minY, box.minZ); v(box.minX, box.minY, box.maxZ)
        v(box.minX, box.maxY, box.maxZ); v(box.minX, box.maxY, box.minZ)
        // East (+X)
        v(box.maxX, box.maxY, box.minZ); v(box.maxX, box.maxY, box.maxZ)
        v(box.maxX, box.minY, box.maxZ); v(box.maxX, box.minY, box.minZ)

        // Outline on top
        drawBoxOutline(matrices, consumers, box, outlineArgb, outlineWidth, throughWalls)
    }

    fun drawLine(
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        from: Vec3d,
        to: Vec3d,
        argb: Int,
        lineWidth: Float = 1f,
        throughWalls: Boolean = true,
    ) {
        val layer = when {
            throughWalls -> linesThroughWalls ?: RenderLayers.LINES_TRANSLUCENT
            else -> RenderLayers.LINES
        }
        val consumer = consumers.getBuffer(layer)
        val entry = matrices.peek()

        val dir = org.joml.Vector3f(
            (to.x - from.x).toFloat(),
            (to.y - from.y).toFloat(),
            (to.z - from.z).toFloat(),
        )
        if (dir.lengthSquared() < 1e-6f) return
        dir.normalize()

        consumer.vertex(entry, from.x.toFloat(), from.y.toFloat(), from.z.toFloat())
            .color(argb)
            .normal(entry, dir)
            .lineWidth(lineWidth)
        consumer.vertex(entry, to.x.toFloat(), to.y.toFloat(), to.z.toFloat())
            .color(argb)
            .normal(entry, dir)
            .lineWidth(lineWidth)
    }
}
