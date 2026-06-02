package de.lasse.lasseclient.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvents

/**
 * Tiny helper for playing UI feedback sounds from the ClickGUI.
 */
@Environment(EnvType.CLIENT)
object SoundUtil {

    /** Play the standard Minecraft UI click sound. */
    fun click() {
        val mc = MinecraftClient.getInstance()
        mc.player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f)
    }
}
