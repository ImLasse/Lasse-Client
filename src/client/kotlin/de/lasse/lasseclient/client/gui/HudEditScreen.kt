package de.lasse.lasseclient.client.gui

import de.lasse.lasseclient.client.config.RcConfig
import de.lasse.lasseclient.client.hud.HudElement
import de.lasse.lasseclient.client.hud.HudManager
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * Full-screen editor for repositioning and resizing every registered [HudElement].
 *
 *  - Hold and drag an element to move it.
 *  - Scroll, or +/-, over an element to resize it (relative to the screen).
 *  - ESC exits back to the game.
 */
@Environment(EnvType.CLIENT)
class HudEditScreen(private val parent: Screen? = null) : Screen(Text.literal("Edit HUD")) {

    private var dragging: HudElement? = null
    private var dragOffX = 0
    private var dragOffY = 0
    private var selected: HudElement? = null

    override fun shouldPause(): Boolean = false

    /** Persist any moves/resizes (which wrote the hidden RC hud* fields) when leaving the editor. */
    override fun removed() {
        RcConfig.save()
        super.removed()
    }

    /** Return to the screen we were opened from (the config screen), or the game. */
    override fun close() {
        client?.setScreen(parent)
    }

    private fun elementAt(mouseX: Int, mouseY: Int): HudElement? {
        // Topmost first so overlapping elements pick the one drawn last.
        for (e in HudManager.elements.asReversed()) {
            val x = e.pixelX(width)
            val y = e.pixelY(height)
            if (mouseX in x..(x + e.scaledWidth()) && mouseY in y..(y + e.scaledHeight())) return e
        }
        return null
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0x99000000.toInt())
        val tr = this.textRenderer
        context.drawText(
            tr,
            "Edit HUD — drag to move · scroll or +/- to resize · ESC to exit",
            8, 8, Theme.TEXT, false,
        )

        val hovered = dragging ?: elementAt(mouseX, mouseY)

        for (e in HudManager.elements) {
            e.render(context, width, height, editing = true)
            val x = e.pixelX(width)
            val y = e.pixelY(height)
            val w = e.scaledWidth()
            val h = e.scaledHeight()
            val border = if (e === hovered) Theme.ACCENT else Theme.CARD_BORDER
            context.drawStrokedRectangle(x - 1, y - 1, w + 2, h + 2, border)
            context.drawText(tr, e.displayName, x, (y - 10).coerceAtLeast(0), Theme.TEXT_DIM, false)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        val e = elementAt(mx, my)
        if (e != null) {
            dragging = e
            selected = e
            dragOffX = mx - e.pixelX(width)
            dragOffY = my - e.pixelY(height)
            SoundUtil.click()
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        val e = dragging ?: return super.mouseDragged(click, offsetX, offsetY)
        e.setPixelPosition(click.x().toInt() - dragOffX, click.y().toInt() - dragOffY, width, height)
        return true
    }

    override fun mouseReleased(click: Click): Boolean {
        if (dragging != null) {
            dragging = null
            return true
        }
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val e = elementAt(mouseX.toInt(), mouseY.toInt()) ?: selected
        if (e != null && vertical != 0.0) {
            e.nudgeScale(vertical * 0.1)
            selected = e
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        when (input.key()) {
            GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                selected?.nudgeScale(0.1); return true
            }
            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                selected?.nudgeScale(-0.1); return true
            }
        }
        return super.keyPressed(input)
    }
}
