package gg.saturn.mixin;

import gg.saturn.ScoreboardRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt das normale Scoreboard durch unseren Renderer, damit Hintergrund,
 * Ecken, Breite, Höhe und Zahlen frei wählbar sind.
 * require = 0: passt die Methode in dieser MC-Version nicht, läuft alles normal.
 */
@Mixin(InGameHud.class)
public class ScoreboardMixin {

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$customSidebar(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (objective == null) return;
        ScoreboardRenderer.render(ctx, objective, ctx.getScaledWindowWidth());
        ci.cancel();
    }
}