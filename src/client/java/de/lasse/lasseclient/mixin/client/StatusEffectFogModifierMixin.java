package de.lasse.lasseclient.mixin.client;

import de.lasse.lasseclient.client.modules.visual.NoVisualEffects;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Render-layer suppression of the blindness/darkness fog. Both the blindness and darkness fog
 * modifiers extend {@link StatusEffectFogModifier}; forcing {@code shouldApply} to return false
 * makes {@code FogRenderer} skip that modifier entirely, so the fog is never drawn.
 *
 * The status effect itself is left on the player — this only changes what we render — so nothing
 * is sent to the server and anticheat sees nothing.
 */
@Mixin(StatusEffectFogModifier.class)
public abstract class StatusEffectFogModifierMixin {

    @Shadow
    public abstract RegistryEntry<StatusEffect> getStatusEffect();

    @Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
    private void lasseclient$suppressFog(
        CameraSubmersionType submersionType, Entity entity, CallbackInfoReturnable<Boolean> cir
    ) {
        if (NoVisualEffects.suppresses(getStatusEffect())) cir.setReturnValue(false);
    }
}
