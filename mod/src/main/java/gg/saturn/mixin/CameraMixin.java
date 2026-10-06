package gg.saturn.mixin;

import gg.saturn.Effects;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FREELOOK: die Kamera folgt einer eigenen Blickrichtung,
 * während der Spieler ganz normal weiterläuft.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$freelook(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                   boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!Effects.freelook) return;
        Effects.freelookSync(focusedEntity);
        setPos(focusedEntity.getX(), focusedEntity.getEyeY(), focusedEntity.getZ());
        setRotation(Effects.freeYaw, Effects.freePitch);
        ci.cancel();
    }
}