package de.lasse.lasseclient.mixin.client;

import de.lasse.lasseclient.client.event.EventBus;
import de.lasse.lasseclient.client.event.PacketReceivedEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only observer of inbound packets we care about. Fires {@link PacketReceivedEvent} at
 * HEAD so listeners see the packet before vanilla handles it.
 *
 * Pure observer: no packet is modified or cancelled. This is the same approach Devonian's
 * ClientPacketListenerMixin uses and is safe for Hypixel.
 *
 * If you need more packet types here, add another @Inject — keep them HEAD-only and never
 * pass {@code CallbackInfo ci} to ci.cancel().
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onEntitySpawn", at = @At("HEAD"))
    private void lasseclient$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        EventBus.INSTANCE.fire(new PacketReceivedEvent(packet));
    }

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"))
    private void lasseclient$onEntitiesDestroy(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        EventBus.INSTANCE.fire(new PacketReceivedEvent(packet));
    }
}
