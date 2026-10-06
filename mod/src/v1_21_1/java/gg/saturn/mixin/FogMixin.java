package gg.saturn.mixin;

import gg.saturn.Effects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NOFOG fuer Minecraft 1.21.1.
 *
 * Bis einschliesslich 1.21.1 gibt es den Fog-Record noch nicht - der Nebel
 * wird direkt aus der Sichtweite berechnet und an die Grafikkarte gereicht.
 * Deshalb greift hier ein anderer Weg als ab 1.21.2: die Sichtweite wird
 * hochgesetzt, wodurch der Nebel weit nach hinten rueckt.
 *
 * Ab 1.21.2 liefert applyFog() einen Fog-Record zurueck, dort wird statt
 * dessen dessen Wert ersetzt - siehe ../v1_21_2plus/FogMixin.java
 */
@Mixin(MinecraftClient.class)
public class FogMixin {

    @Shadow
    private net.minecraft.client.option.GameOptions options;

    /** Die Sichtweite, die vorher eingestellt war, damit NOFOG sie wiederherstellen kann. */
    private int saturn$sichtweiteVorher = -1;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void saturn$noFog(CallbackInfo ci) {
        if (options == null) return;
        SimpleOption<Integer> sichtweite = options.getViewDistance();
        if (sichtweite == null) return;

        if (Effects.fogOn) {
            if (saturn$sichtweiteVorher < 0) {
                saturn$sichtweiteVorher = sichtweite.getValue();
            }
            int gewuenscht = Math.max(sichtweite.getValue(), (int) Math.max(8, Effects.fogDistance));
            if (sichtweite.getValue() != gewuenscht) sichtweite.setValue(gewuenscht);
        } else if (saturn$sichtweiteVorher >= 0) {
            // NOFOG ausgeschaltet: urspruengliche Sichtweite zuruecksetzen
            sichtweite.setValue(saturn$sichtweiteVorher);
            saturn$sichtweiteVorher = -1;
        }
    }
}