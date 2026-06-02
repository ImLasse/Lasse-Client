package de.lasse.lasseclient.client.gui.config

import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigSeparatorElement
import com.teamresourceful.resourcefulconfig.client.UIConstants
import com.teamresourceful.resourcefulconfig.client.components.ModSprites
import com.teamresourceful.resourcefulconfig.client.components.base.BaseWidget
import com.teamresourceful.resourcefulconfig.client.components.base.ListWidget
import com.teamresourceful.resourcefulconfig.client.components.options.Options
import com.teamresourceful.resourcefulconfig.client.components.options.OptionsListWidget
import de.lasse.lasseclient.client.config.RcConfig
import de.lasse.lasseclient.client.gui.HudEditScreen
import de.lasse.lasseclient.client.gui.SoundUtil
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * Lasse Client config screen.
 *
 * Resourceful Config's default screen drills into a new screen whenever a sidebar category is
 * clicked. We want the group list to stay pinned on the left and the settings to swap in place on
 * the right, so this is a thin shell that reuses RC's own rendering: the panels are RC's
 * {@link ModSprites} 9-slice sprites, the colors are {@link UIConstants}, the font is vanilla, and
 * the right pane is RC's real {@link OptionsListWidget} populated with the selected group's
 * elements — so toggles, list editors, color pickers, separators and the per-module "Edit" popups
 * are all native and look exactly like the default theme.
 *
 * The "Edit HUD Layout" button lives in its own slot at the bottom of the sidebar.
 */
@Environment(EnvType.CLIENT)
class LasseConfigScreen(private val parent: Screen?) : Screen(Text.literal("Lasse Client")) {

    private lateinit var root: ResourcefulConfig
    private val groups = ArrayList<ResourcefulConfig>()

    private var optionsList: OptionsListWidget? = null

    // geometry (recomputed each init)
    private var contentX = 0; private var contentY = 0; private var contentW = 0
    private var headerH = 34
    private var bodyY = 0; private var bodyH = 0
    private var sidebarX = 0; private var sidebarW = 0

    private val groupRects = ArrayList<Rect>()
    private var hudRect = Rect(0, 0, 0, 0)

    private class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun has(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
    }

    override fun init() {
        root = RcConfig.configurator.getConfig(de.lasse.lasseclient.config.LasseConfig::class.java)
        groups.clear()
        for (cat in root.categories().values) {
            if (!cat.info().isHidden) groups.add(cat)
        }
        if (State.group >= groups.size) State.group = 0

        val pad = UIConstants.PAGE_PADDING
        contentX = pad
        contentY = pad
        contentW = width - pad * 2
        bodyY = contentY + headerH + pad
        bodyH = height - pad - bodyY
        sidebarX = contentX
        sidebarW = maxOf(120, contentW / 4)

        val optionsX = sidebarX + sidebarW + pad
        val optionsW = contentX + contentW - optionsX

        val list = OptionsListWidget(optionsW, bodyH)
        list.setPosition(optionsX, bodyY)
        addDrawableChild(list)
        optionsList = list

        // sidebar hit rects
        groupRects.clear()
        val rowH = 24
        var ry = bodyY + 8
        for (i in groups.indices) {
            groupRects.add(Rect(sidebarX + 8, ry, sidebarW - 16, rowH))
            ry += rowH + 4
        }
        val hudH = 22
        hudRect = Rect(sidebarX + 8, bodyY + bodyH - hudH - 8, sidebarW - 16, hudH)

        populate()
    }

    private fun group(): ResourcefulConfig? = groups.getOrNull(State.group)

    /**
     * Populate the right pane for the selected group. Each module starts with a (named) separator
     * that draws a divider line under its name; to separate modules we add a blank, line-less
     * [SpacerItem] before every module after the first — giving a gap with no line above the name.
     */
    private fun populate() {
        val list = optionsList ?: return
        list.clear()
        val g = group() ?: return

        val chunk = ArrayList<ResourcefulConfigElement>()
        var first = true
        fun flush() {
            if (chunk.isEmpty()) return
            if (!first) list.add(SpacerItem(MODULE_GAP))
            Options.populateOptions(list, ArrayList(chunk))
            first = false
            chunk.clear()
        }
        for (el in g.elements()) {
            if (el is ResourcefulConfigSeparatorElement) flush() // a named separator starts a module
            chunk.add(el)
        }
        flush()

        applyScroll(State.scroll[State.group] ?: 0.0)
    }

    // ---- scroll persistence ---------------------------------------------------------------------

    private fun readScroll(): Double =
        optionsList?.let { l -> runCatching { SCROLL_FIELD?.getDouble(l) }.getOrNull() } ?: 0.0

    private fun applyScroll(value: Double) {
        val l = optionsList ?: return
        runCatching { SCROLL_FIELD?.setDouble(l, value) }
    }

    private fun saveScroll() {
        if (SCROLL_FIELD != null) State.scroll[State.group] = readScroll()
    }

    /** A passive, invisible list row that just reserves vertical space (no divider line). */
    private class SpacerItem(height: Int) : BaseWidget(0, height), ListWidget.Item {
        override fun renderWidget(graphics: DrawContext, mouseX: Int, mouseY: Int, partialTicks: Float) {}
        override fun setItemWidth(width: Int) { this.width = width }
        override fun getNavigationFocus(): net.minecraft.client.gui.ScreenRect = super<BaseWidget>.getNavigationFocus()
    }

    // ---- rendering ------------------------------------------------------------------------------

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, UIConstants.BACKGROUND)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta) // background + options list (right pane)

        val tr = textRenderer

        // header
        panel(context, contentX, contentY, contentW, headerH)
        context.drawText(tr, Text.literal("Lasse Client"), contentX + 10, contentY + 8, UIConstants.TEXT_TITLE, false)
        context.drawText(
            tr, Text.literal("Hypixel SkyBlock quality-of-life features."),
            contentX + 10, contentY + 19, UIConstants.TEXT_PARAGRAPH, false,
        )

        // sidebar
        panel(context, sidebarX, bodyY, sidebarW, bodyH)
        for (i in groups.indices) {
            val r = groupRects[i]
            val hovered = r.has(mouseX.toDouble(), mouseY.toDouble())
            val selected = i == State.group
            if (hovered || selected) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ModSprites.BUTTON_HOVER, r.x + 1, r.y, r.w - 2, r.h)
            }
            val label = groups[i].info().title().toComponent()
            val color = if (selected || hovered) UIConstants.TEXT_TITLE else UIConstants.TEXT_PARAGRAPH
            context.drawText(tr, label, r.x + (r.w - tr.getWidth(label)) / 2, r.y + (r.h - 8) / 2, color, false)
        }

        // HUD button pinned to the sidebar bottom
        val hudHover = hudRect.has(mouseX.toDouble(), mouseY.toDouble())
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ModSprites.ofButton(hudHover), hudRect.x, hudRect.y, hudRect.w, hudRect.h)
        val hudLabel = Text.literal("Edit HUD Layout")
        context.drawText(
            tr, hudLabel,
            hudRect.x + (hudRect.w - tr.getWidth(hudLabel)) / 2, hudRect.y + (hudRect.h - 8) / 2,
            if (hudHover) UIConstants.TEXT_TITLE else UIConstants.TEXT_PARAGRAPH, false,
        )
    }

    private fun panel(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ModSprites.CONTAINER, x, y, w, h)
    }

    // ---- input ----------------------------------------------------------------------------------

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x()
        val my = click.y()

        for (i in groups.indices) {
            if (groupRects[i].has(mx, my)) {
                if (i != State.group) {
                    saveScroll()        // remember where we left the current group
                    State.group = i
                    populate()          // repopulate + restore the target group's scroll
                }
                SoundUtil.click()
                return true
            }
        }
        if (hudRect.has(mx, my)) {
            client?.setScreen(HudEditScreen(this))
            SoundUtil.click()
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    override fun shouldPause(): Boolean = false

    override fun removed() {
        saveScroll() // so reopening lands at the same scroll position within the session
        RcConfig.save()
        super.removed()
    }

    override fun close() {
        client?.setScreen(parent)
    }

    /** Remembers the selected group and each group's scroll so reopening lands where you left off. */
    @Environment(EnvType.CLIENT)
    private object State {
        var group = 0
        val scroll = HashMap<Int, Double>()
    }

    private companion object {
        /** Vertical gap inserted between modules in the right pane. */
        const val MODULE_GAP = 16

        /**
         * RC's [OptionsListWidget] keeps its scroll offset in a private `scroll` field with no
         * accessor, and rebuilds lists from scratch (losing scroll). We reflect that field to
         * save/restore the offset; if RC ever renames it this degrades gracefully to no-persistence.
         */
        private val SCROLL_FIELD = runCatching {
            ListWidget::class.java.getDeclaredField("scroll").apply { isAccessible = true }
        }.getOrNull()
    }
}
