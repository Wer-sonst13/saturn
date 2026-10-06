package gg.saturn.mixin;

import gg.saturn.SaturnMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
 *
 * Die Position wird nicht fest verdrahtet, sondern aus den schon vorhandenen
 * Knöpfen abgeleitet: der Saturn-Knopf landet immer eine Reihe unterhalb der
 * untersten vorhandenen Knopf. Eine feste Angabe wie height/4 + 96 gerät bei
 * jeder Minecraft-Version, die das Menü umbaut, auf einen belegten Platz -
 * dann liegt der Knopf z. B. auf "Disconnect".
 */
@Mixin(GameMenuScreen.class)
public class PauseMenuMixin {

    @Inject(method = "initWidgets", at = @At("TAIL"), require = 0)
    private void saturn$button(CallbackInfo ci) {
        GameMenuScreen self = (GameMenuScreen) (Object) this;

        // Unterkante der untersten Knopf suchen
        int unten = 0;
        for (var child : self.children()) {
            if (child instanceof ButtonWidget b) {
                unten = Math.max(unten, b.getBottom());
            }
        }

        int w = 200;
        int x = self.width / 2 - w / 2;
        int y = unten + 6;

        // Passt es nicht mehr aufs Bild, wird stattdessen ganz oben eingereiht
        // und die Liste bekommt keinen Knopf, der aus dem Fenster ragt.
        if (y + 20 > self.height - 4) {
            // Notfalls über dem Knopf-Stapel, aber unterhalb der Kopfzeile
            y = Math.max(4, self.height / 4 - 26);
        }

        ButtonWidget button = ButtonWidget.builder(Text.literal("Saturn Client"), b ->
                        MinecraftClient.getInstance().setScreen(new SaturnMenuScreen(self)))
                .dimensions(x, y, w, 20)
                .build();
        ((ScreenAccessor) (Object) self).saturn$addDrawableChild(button);
    }
}