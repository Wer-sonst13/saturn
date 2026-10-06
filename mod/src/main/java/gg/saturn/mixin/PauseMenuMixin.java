package gg.saturn.mixin;

import gg.saturn.SaturnMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fügt im Pause-Menü einen "Saturn Client"-Knopf ein
 * (bei NoRisk stand dort "NoRisk Client").
 *
 * Wichtig: Yarn nennt den Override von GameMenuScreen "initWidgets".
 * "init" wäre die finale Methode init(MinecraftClient,int,int) aus Screen -
 * in eine finale Methode kann nicht injiziert werden, der Knopf bliebe dann weg.
 */
@Mixin(GameMenuScreen.class)
public class PauseMenuMixin {

    @Inject(method = "initWidgets", at = @At("TAIL"), require = 0)
    private void saturn$button(CallbackInfo ci) {
        GameMenuScreen self = (GameMenuScreen) (Object) this;
        ButtonWidget button = ButtonWidget.builder(Text.literal("Saturn Client"), b ->
                        MinecraftClient.getInstance().setScreen(new SaturnMenuScreen(self)))
                .dimensions(self.width / 2 - 100, self.height / 4 + 96, 200, 20)
                .build();
        ((ScreenAccessor) (Object) self).saturn$addDrawableChild(button);
    }
}