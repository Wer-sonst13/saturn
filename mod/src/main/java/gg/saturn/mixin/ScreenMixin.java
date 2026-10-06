package gg.saturn.mixin;

import gg.saturn.Effects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CLEAR BACKGROUND: blendet die Hintergrundebene von Menüs aus.
 */
@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$clearBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Effects.isOn("clearbg")) ci.cancel();
    }

    @Inject(method = "renderDarkening", at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$clearDarkening(DrawContext ctx, CallbackInfo ci) {
        if (Effects.isOn("clearbg")) ci.cancel();
    }

    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;IIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$clearDarkeningRect(DrawContext ctx, int x, int y, int w, int h, CallbackInfo ci) {
        if (Effects.isOn("clearbg")) ci.cancel();
    }
}