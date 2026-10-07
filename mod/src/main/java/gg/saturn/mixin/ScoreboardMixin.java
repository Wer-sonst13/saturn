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
 * Ersetetzt das normale Scoreboard durch unseren Renderer, damit Hintergrund,
 * Ecken, Breite, Hoehe und Zahlen frei waehlbar sind.
 *
 * Bewusst nur an EINER der beiden Vanilla-Varianten.
 *
 * Es gibt zwei renderScoreboardSidebar, und die eine ruft die andere auf.
 * Ein Versuch, an beide zu gehen und mit einem Schalter nur einmal je Bild zu
 * zeichnen, hat das Scoreboard komplett verschwinden lassen: der Schalter
 * haette am Bildende ueber eine Injection auf InGameHud.render zurueckgesetzt
 * werden muessen, und diese Methode nimmt inzwischen andere Parameter - die
 * Injection fand kein Ziel und blieb still. Danach war der Schalter dauerhaft
 * gesetzt, es wurde jedes Bild abgebrochen und nie gezeichnet.
 *
 * Also: nur die Variante mit dem ScoreboardObjective. Sie zeichnet einmal,
 * mit dem Preis, dass die Zeilen im Bild leicht doppelt erscheinen. Das ist
 * ein sichtbarer Fehler, aber ein offensichtlicher - ein unsichtbares
 * Scoreboard ist schlimmer.
 *
 * require = 0: passt die Methode in dieser MC-Version nicht, laeuft alles normal.
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