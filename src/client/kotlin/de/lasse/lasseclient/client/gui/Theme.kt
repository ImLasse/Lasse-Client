package de.lasse.lasseclient.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
object Theme {
    // ARGB
    const val BACKGROUND = 0xCC0F1117.toInt()
    const val PANEL = 0xEE161922.toInt()
    const val PANEL_LIGHT = 0xEE1C202B.toInt()
    const val SIDEBAR = 0xEE12141B.toInt()
    const val DIVIDER = 0x33FFFFFF
    const val CARD = 0xFF1F2330.toInt()
    const val CARD_HOVER = 0xFF272D3D.toInt()
    const val CARD_BORDER = 0xFF2D3344.toInt()
    const val CARD_BORDER_ACCENT = 0xFF5B8DEF.toInt()

    const val TEXT = 0xFFE6E8EE.toInt()
    const val TEXT_MUTED = 0xFF8B92A5.toInt()
    const val TEXT_DIM = 0xFF5C657A.toInt()

    const val ACCENT = 0xFF5B8DEF.toInt()
    const val ACCENT_DIM = 0xFF394E78.toInt()
    const val DANGER = 0xFFE5484D.toInt()
    const val SUCCESS = 0xFF30A46C.toInt()

    const val TOGGLE_ON = ACCENT
    const val TOGGLE_OFF = 0xFF353B4A.toInt()
    const val TOGGLE_KNOB = 0xFFE6E8EE.toInt()
}
