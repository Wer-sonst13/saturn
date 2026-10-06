package gg.saturn.mixin;

import gg.saturn.Trackers;
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

    // Ohne Parameter einsetzen.
//
// attackEntity ist eine ueberschriebene Methode: der Refmap beim Bau hat
// ClientPlayerEntity (class_1657) auf PlayerEntity (class_746) uebersetzt.
// Dann passt die Signatur nicht, der Mixin greift nicht - und weil
// require = 0, faellt das nur als Warnung im Log auf. Das Modul zaehlt
// dann nie einen Treffer.
//
// Ohne Parameter kann es keine Signatur-Abweichung geben. Mixin ruft die
// Methode dann mit beliebig vielen Parametern auf.
@Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void saturn$combo(CallbackInfo ci) {
        Trackers.hit();
        Trackers.registerComboHit();
    }
}