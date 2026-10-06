package gg.nolimite.mixin;

import gg.nolimite.Module;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FOV CHANGER: nutzt den Wert aus dem Modul statt der Spiel-Einstellung.
 * changingFov = true wird beim Zoomen (Spyglass) nicht überschrieben.
 */
@Mixin(GameRenderer.class)
public class FovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void nolimite$fov(Camera camera, float tickDelta, boolean changingFov,
                              CallbackInfoReturnable<Float> cir) {
        Module m = Module.get("fov");
        if (m == null || !m.enabled || changingFov) return;
        cir.setReturnValue((float) m.num("value", 70.0));
    }
}