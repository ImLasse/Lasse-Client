package de.lasse.lasseclient.client.hud

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext

/**
 * A movable, resizable on-screen HUD element (notifications, info readouts, …).
 *
 * Position is stored as a fraction (0..1) of the scaled screen so the element keeps its
 * relative spot when the window is resized. Scale is an independent multiplier driven by the
 * mouse wheel / +/- in the HUD editor. All three are backed by getter/setter lambdas the owning
 * module wires to its hidden Resourceful Config `hud*` fields, so edits persist through RC.
 */
@Environment(EnvType.CLIENT)
abstract class HudElement(
    val id: String,
    val displayName: String,
    private val getX: () -> Double,
    private val setX: (Double) -> Unit,
    private val getY: () -> Double,
    private val setY: (Double) -> Unit,
    private val getScaleRaw: () -> Double,
    private val setScaleRaw: (Double) -> Unit,
) {
    /** Unscaled content size in GUI pixels (before [scale]). */
    abstract fun contentWidth(): Int
    abstract fun contentHeight(): Int

    /**
     * Draw the element at local origin (0,0); the caller has already applied the
     * translate + scale transform. [editing] is true inside the HUD editor, where the element
     * should render a static full-opacity preview regardless of its live state.
     */
    abstract fun renderContent(context: DrawContext, editing: Boolean)

    /** Whether the element should draw during the normal in-game HUD (outside the editor). */
    open fun visibleInGame(): Boolean = true

    val scale: Float get() = getScaleRaw().toFloat()

    fun scaledWidth(): Int = (contentWidth() * scale).toInt()
    fun scaledHeight(): Int = (contentHeight() * scale).toInt()

    fun pixelX(screenW: Int): Int = (getX() * screenW).toInt()
    fun pixelY(screenH: Int): Int = (getY() * screenH).toInt()

    fun setPixelPosition(px: Int, py: Int, screenW: Int, screenH: Int) {
        val maxX = (screenW - scaledWidth()).coerceAtLeast(0)
        val maxY = (screenH - scaledHeight()).coerceAtLeast(0)
        setX(px.coerceIn(0, maxX).toDouble() / screenW)
        setY(py.coerceIn(0, maxY).toDouble() / screenH)
    }

    fun nudgeScale(delta: Double) {
        setScaleRaw((getScaleRaw() + delta).coerceIn(0.5, 4.0))
    }

    fun render(context: DrawContext, screenW: Int, screenH: Int, editing: Boolean) {
        val px = pixelX(screenW).toFloat()
        val py = pixelY(screenH).toFloat()
        val m = context.matrices
        m.pushMatrix()
        m.translate(px, py)
        m.scale(scale, scale)
        renderContent(context, editing)
        m.popMatrix()
    }
}
