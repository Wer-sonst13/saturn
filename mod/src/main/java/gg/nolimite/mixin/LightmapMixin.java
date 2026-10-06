package gg.nolimite.mixin;

import gg.nolimite.Effects;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FULLBRIGHT / BRIGHTNESS: der Dunkelheitsfaktor der Lightmap wird auf 0 gesetzt,
 * dadurch wird nichts mehr abgedunkelt. Zusätzlich setzt Effects die
 * vorhandene Helligkeits-Option (gamma).
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    @Inject(method = "getDarknessFactor", at = @At("HEAD"), cancellable = true, require = 0)
    private void nolimite$noDarknessFactor(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (Effects.darknessForced) cir.setReturnValue(0.0f);
    }

    @Inject(method = "getDarkness", at = @At("HEAD"), cancellable = true, require = 0)
    private void nolimite$noDarkness(net.minecraft.entity.LivingEntity entity, float tickDelta,
                                     float lightmapCoord, CallbackInfoReturnable<Float> cir) {
        if (Effects.darknessForced) cir.setReturnValue(0.0f);
    }
}