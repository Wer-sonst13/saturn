package gg.saturn.mixin;

import gg.saturn.Effects;
import gg.saturn.SaturnScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CLEAR BACKGROUND: blendet die Hintergrundebene aus - aber nur in unseren
 * eigenen Fenstern.
 *
 * Vorher galt das fuer JEDEN Bildschirm. Dadurch hatte die
 * Server-Liste keinen Hintergrund mehr und die Welt schien durch - Text und
 * Lade-Anzeige lagen dann uebereinander.
 *
 * Unsere Fenster zeichnen ihren Hintergrund selbst (SaturnScreen.drawBackdrop),
 * deshalb fehlt dort nur die Vanilla-Ebene. Fenster von Minecraft
 * bekommen ihr normales Aussehen.
 */
@Mixin(Screen.class)
public class ScreenMixin {

    /** Nur fuer Saturn-Fenster. */
    private boolean saturn$unserFenster() {
        return (Object) this instanceof SaturnScreen;
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$clearBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Effects.isOn("clearbg") && saturn$unserFenster()) ci.cancel();
    }

    @Inject(method = "renderDarkening", at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$clearDarkening(DrawContext ctx, CallbackInfo ci) {
        if (Effects.isOn("clearbg") && saturn$unserFenster()) ci.cancel();
    }

    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;IIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$clearDarkeningRect(DrawContext ctx, int x, int y, int w, int h, CallbackInfo ci) {
        if (Effects.isOn("clearbg") && saturn$unserFenster()) ci.cancel();
    }
}