package de.lasse.lasseclient.mixin.client;

import de.lasse.lasseclient.client.event.EventBus;
import de.lasse.lasseclient.client.event.NameChangeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only observer of CUSTOM_NAME tracked-data updates. Fires {@link NameChangeEvent} after
 * vanilla's onTrackedDataSet has applied the change, so listeners can read the new name via
 * {@code entity.getCustomName()}.
 *
 * Pure observer: no packet is modified, nothing is cancelled — safe for anti-cheat envs.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void lasseclient$fireNameChange(TrackedData<?> data, CallbackInfo ci) {
        if (data != EntityAccessor.lasseclient$getCustomNameSlot()) return;
        Entity self = (Entity) (Object) this;
        Text name = self.getCustomName();
        String str = name != null ? name.getString() : "";
        EventBus.INSTANCE.fire(new NameChangeEvent(self.getId(), str));
    }
}
