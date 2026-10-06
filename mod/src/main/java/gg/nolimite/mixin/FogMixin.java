package gg.nolimite.mixin;

import gg.nolimite.Effects;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NOFOG: schiebt den Nebelbeginn auf den eingestellten Abstand hinaus.
 * Fog ist ein Record mit finalen Feldern, also wird ein neuer Fog
 * mit denselben Farben, aber größerer Distanz zurückgegeben.
 */
@Mixin(BackgroundRenderer.class)
public class FogMixin {

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void nolimite$noFog(net.minecraft.client.render.Camera camera,
                                        BackgroundRenderer.FogType fogType,
                                        Vector4f fogColor,
                                        float tickDelta,
                                        boolean sky,
                                        float viewDistance,
                                        CallbackInfoReturnable<Fog> cir) {
        if (!Effects.fogOn) return;
        Fog fog = cir.getReturnValue();
        if (fog == null) return;
        float d = Effects.fogDistance;
        if (fog.start() >= d) return;
        cir.setReturnValue(new Fog(d, Math.max(d + 1f, fog.end() * 2f), fog.shape(),
                fog.red(), fog.green(), fog.blue(), fog.alpha()));
    }
}