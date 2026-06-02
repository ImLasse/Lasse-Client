package de.lasse.lasseclient.mixin.client;

import de.lasse.lasseclient.client.modules.visual.NoVisualEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Render-layer suppression of nausea and darkness for the local player.
 *
 * {@code getEffectFadeFactor} is the shared intensity signal the renderer reads for both the
 * nausea screen wobble ({@code GameRenderer}) and the darkness lightmap dimming
 * ({@code LightmapTextureManager}). Forcing it to 0 makes those effects render as if they had
 * fully faded out, while the status effect stays on the entity untouched.
 *
 * Scoped to the local player so other entities' logic is never affected, and nothing is sent to
 * the server — purely a local rendering change.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getEffectFadeFactor", at = @At("HEAD"), cancellable = true)
    private void lasseclient$suppressEffectFade(
        RegistryEntry<StatusEffect> effect, float tickDelta, CallbackInfoReturnable<Float> cir
    ) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        if (NoVisualEffects.suppresses(effect)) cir.setReturnValue(0.0F);
    }
}
