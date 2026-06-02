package de.lasse.lasseclient.mixin.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/**
 * Exposes the private static {@code CUSTOM_NAME} tracked-data slot on {@link Entity} so
 * {@link EntityMixin} can compare against it without reflection.
 */
@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("CUSTOM_NAME")
    static TrackedData<Optional<Text>> lasseclient$getCustomNameSlot() {
        throw new AssertionError("@Accessor stub not transformed");
    }
}
