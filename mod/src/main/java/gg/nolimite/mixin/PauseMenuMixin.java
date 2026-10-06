package gg.nolimite.mixin;

import gg.nolimite.NoLimiteMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fügt im Pause-Menü einen "NoLimite"-Knopf ein
 * (bei NoRisk stand dort "NoRisk Client").
 */
@Mixin(GameMenuScreen.class)
public class PauseMenuMixin {

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void nolimite$button(CallbackInfo ci) {
        GameMenuScreen self = (GameMenuScreen) (Object) this;
        ButtonWidget button = ButtonWidget.builder(Text.literal("NoLimite"), b ->
                        MinecraftClient.getInstance().setScreen(new NoLimiteMenuScreen(self)))
                .dimensions(self.width / 2 - 100, self.height / 4 + 96, 200, 20)
                .build();
        ((ScreenAccessor) (Object) self).nolimite$addDrawableChild(button);
    }
}