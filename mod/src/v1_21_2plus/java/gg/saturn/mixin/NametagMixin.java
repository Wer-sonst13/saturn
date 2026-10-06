package gg.saturn.mixin;

import gg.saturn.Effects;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NAMETAGS: versteckt die normalen Namensschilder.
 * Gezeichnet werden sie stattdessen von gg.saturn.NametagRenderer,
 * damit Hintergrund, Farbe und Größe einstellbar sind.
 *
 * renderLabelIfPresent ist generisch, deshalb wird das Signatur-Präfix nicht
 * angegeben - Mixin sucht die Methode dann nach Name.
 */
@Mixin(EntityRenderer.class)
public class NametagMixin {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$nametags(EntityRenderState state, Text label, MatrixStack matrices,
                                    VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!Effects.nametagsOn) return;
        ci.cancel();
    }
}