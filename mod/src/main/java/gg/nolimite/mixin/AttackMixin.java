package gg.nolimite.mixin;

import gg.nolimite.Trackers;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** COMBO COUNTER / APS: zählt erfolgreiche Treffer. */
@Mixin(ClientPlayerInteractionManager.class)
public class AttackMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void nolimite$combo(ClientPlayerEntity player, Entity target, CallbackInfo ci) {
        Trackers.hit();
        Trackers.registerComboHit();
    }
}