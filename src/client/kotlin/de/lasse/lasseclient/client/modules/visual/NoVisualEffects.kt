package de.lasse.lasseclient.client.modules.visual

import de.lasse.lasseclient.client.module.Category
import de.lasse.lasseclient.client.module.Module
import de.lasse.lasseclient.config.LasseConfig.Visual.NoVisualEffects as Cfg
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.entry.RegistryEntry

/**
 * Hides the disorienting visual debuffs (blindness, nausea, darkness) for the local player.
 *
 * This does NOT remove the effect from the player — the status effect stays exactly as the
 * server applied it. Instead it suppresses the effect purely at the *render* layer:
 *  - blindness/darkness fog: [de.lasse.lasseclient.mixin.client.StatusEffectFogModifierMixin].
 *  - nausea wobble & darkness lightmap dimming: [de.lasse.lasseclient.mixin.client.LivingEntityMixin].
 *
 * Because nothing is removed and no packet is ever touched or sent, there is zero server-visible
 * footprint — it is strictly a local rendering change. Settings live in
 * [LasseConfig.Visual.NoVisualEffects].
 */
@Environment(EnvType.CLIENT)
class NoVisualEffects : Module(
    name = "No Visual Effects",
    description = "Hides blindness, nausea and darkness at the render layer (no packets, effect kept).",
    category = Category.VISUAL,
) {
    init {
        // React to per-effect toggles flipped in the config screen.
        Cfg.blindness.addListener { _, _ -> syncGate() }
        Cfg.nausea.addListener { _, _ -> syncGate() }
        Cfg.darkness.addListener { _, _ -> syncGate() }
        syncGate()
    }

    override fun onEnable() = syncGate()
    override fun onDisable() = syncGate()

    private fun syncGate() {
        blockBlindness = enabled && Cfg.blindness.get()
        blockNausea = enabled && Cfg.nausea.get()
        blockDarkness = enabled && Cfg.darkness.get()
    }

    companion object {
        @Volatile private var blockBlindness = false
        @Volatile private var blockNausea = false
        @Volatile private var blockDarkness = false

        /**
         * Whether the given effect's *visual* should be suppressed for the local player. Queried
         * from the render mixins; the effect itself is left untouched on the entity.
         */
        @JvmStatic
        fun suppresses(effect: RegistryEntry<StatusEffect>): Boolean =
            (blockBlindness && effect == StatusEffects.BLINDNESS) ||
                (blockNausea && effect == StatusEffects.NAUSEA) ||
                (blockDarkness && effect == StatusEffects.DARKNESS)
    }
}
