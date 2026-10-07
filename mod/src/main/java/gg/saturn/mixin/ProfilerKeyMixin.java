package gg.saturn.mixin;

import gg.saturn.Profiler;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fängt Tastendruecke ab, waehrend kein Saturn-Fenster offen ist.
 *
 * "S" schreibt den Profilbericht. Der normale Ablauf prueft die Taste nur,
 * solange das Profiler-Fenster offen ist - da laeuft aber die
 * Bildschleife des Menues und der Bericht wuerde nicht ausgeschrieben.
 */
@Mixin(Screen.class)
public abstract class ProfilerKeyMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void saturn$profilerTaste(int keyCode, int scanCode, int modifiers,
                                     CallbackInfoReturnable<Boolean> info) {
        if (Profiler.sichtbar()
                && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            Profiler.anfordern();
            info.setReturnValue(true);
        }
    }
}