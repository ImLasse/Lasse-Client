package de.lasse.lasseclient.client

import de.lasse.lasseclient.client.config.RcConfig
import de.lasse.lasseclient.client.gui.HudEditScreen
import de.lasse.lasseclient.client.hud.HudManager
import de.lasse.lasseclient.client.module.ModuleManager
import de.lasse.lasseclient.client.modules.command.ChatCommand
import de.lasse.lasseclient.client.modules.debug.DisplayEntityDebug
import de.lasse.lasseclient.client.modules.notification.ChatNotification
import de.lasse.lasseclient.client.modules.notification.SpawnNotification
import de.lasse.lasseclient.client.modules.visual.LilypadHelper
import de.lasse.lasseclient.client.modules.visual.MineshaftUtils
import de.lasse.lasseclient.client.modules.visual.MobHighlighter
import de.lasse.lasseclient.client.modules.visual.NoVisualEffects
import de.lasse.lasseclient.client.modules.visual.PestESP
import de.lasse.lasseclient.config.LasseConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

@Environment(EnvType.CLIENT)
class LasseclientClient : ClientModInitializer {

    private lateinit var openConfigKey: KeyBinding
    private lateinit var editHudKey: KeyBinding

    override fun onInitializeClient() {
        // 0) Build custom render pipelines BEFORE shader compilation (= before resource
        //    reload finishes). Doing this lazily on first frame is too late: the pipeline
        //    has no compiled shaders and the through-walls fill silently no-ops.
        de.lasse.lasseclient.client.render.RenderUtil.initialize()

        // 0.5) Subscribe to the Hypixel Mod API location event so location-gated modules
        //      (e.g. Pest ESP, which only runs on the Garden) know where the player is.
        de.lasse.lasseclient.client.hypixel.HypixelLocation.init()

        // 1) Register + load the Resourceful Config. Must precede module binding so the loaded
        //    `enabled` values are available when each module attaches its listener.
        RcConfig.init()

        // 2) Register modules and bind each one's enabled flag to its config toggle.
        val mobHighlighter = MobHighlighter().also { ModuleManager.register(it) }
        val noVisualEffects = NoVisualEffects().also { ModuleManager.register(it) }
        val pestEsp = PestESP().also { ModuleManager.register(it) }
        val mineshaftUtils = MineshaftUtils().also { ModuleManager.register(it) }
        val lilypadHelper = LilypadHelper().also { ModuleManager.register(it) }
        val displayEntityDebug = DisplayEntityDebug().also { ModuleManager.register(it) }
        val spawnNotification = SpawnNotification().also { ModuleManager.register(it) }
        val chatNotification = ChatNotification().also { ModuleManager.register(it) }
        val chatCommand = ChatCommand().also { ModuleManager.register(it) }

        mobHighlighter.bindEnabled(LasseConfig.Visual.mobHighlighterEnabled)
        noVisualEffects.bindEnabled(LasseConfig.Visual.noVisualEffectsEnabled)
        pestEsp.bindEnabled(LasseConfig.Visual.pestEspEnabled)
        mineshaftUtils.bindEnabled(LasseConfig.Visual.mineshaftUtilsEnabled)
        lilypadHelper.bindEnabled(LasseConfig.Visual.lilypadHelperEnabled)
        displayEntityDebug.bindEnabled(LasseConfig.Utilities.displayEntityDebugEnabled)
        spawnNotification.bindEnabled(LasseConfig.Notifications.spawnNotificationEnabled)
        chatNotification.bindEnabled(LasseConfig.Notifications.chatNotificationEnabled)
        chatCommand.bindEnabled(LasseConfig.Utilities.chatCommandEnabled)

        // 3) Register the in-game HUD overlay layer (draws all registered HUD elements).
        HudManager.init()

        // 4) Keybinds — user-configurable in vanilla Controls menu.
        val category = KeyBinding.Category.create(Identifier.of("lasseclient", "main"))
        openConfigKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.lasseclient.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category,
            )
        )
        editHudKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.lasseclient.edit_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
                category,
            )
        )

        // 5) Tick listener: open screens when a keybind is pressed and no other screen is up.
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            while (openConfigKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(RcConfig.screen(null))
            }
            while (editHudKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(HudEditScreen())
            }
        })
    }

    companion object {
        @Suppress("unused")
        fun client(): MinecraftClient = MinecraftClient.getInstance()

        @Suppress("unused")
        fun sendChat(text: String) {
            client().inGameHud.chatHud.addMessage(Text.literal(text))
        }
    }
}
