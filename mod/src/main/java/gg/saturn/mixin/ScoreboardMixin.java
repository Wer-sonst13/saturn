package gg.saturn.mixin;

import gg.saturn.ScoreboardRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt das normale Scoreboard durch unseren Renderer, damit Hintergrund,
 * Ecken, Breite, Hoehe und Zahlen frei waehlbar sind.
 *
 * Wichtig: es gibt ZWEI Varianten von renderScoreboardSidebar, und die eine
 * ruft die andere auf (method_55803 -> method_1757). Haengt der Mixin nur an
 * der inneren, zeichnet die aeussere anschliessend trotzdem noch einmal -
 * das Scoreboard stand doppelt im Bild, jede Zeile leicht versetzt.
 *
 * Deshalb hier an beide, mit einem Schalter, der nur einmal je Bild zeichnet.
 *
 * require = 0: passt eine der Methoden in dieser MC-Version nicht, laeuft
 * alles normal.
 */
@Mixin(InGameHud.class)
public class ScoreboardMixin {

    /** Wurde in diesem Bild schon gezeichnet? */
    private static boolean saturn$gezeichnet = false;

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$sidebarAussen(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        saturn$zeichne(ctx, ci);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$customSidebar(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        saturn$zeichne(ctx, ci);
    }

    private static void saturn$zeichne(DrawContext ctx, CallbackInfo ci) {
        if (saturn$gezeichnet) {
            ci.cancel();      // zweite Variante: nur abbrechen, nicht nochmal malen
            return;
        }
        saturn$gezeichnet = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        ScoreboardObjective objective = mc.player == null || mc.player.networkHandler == null
                ? null
                : objectiveAusSidebar(mc);
        if (objective != null) {
            ScoreboardRenderer.render(ctx, objective, ctx.getScaledWindowWidth());
        }
        ci.cancel();
    }

    /** Das Ziel, das gerade in der Seitenleiste angezeigt wird. */
    private static ScoreboardObjective objectiveAusSidebar(MinecraftClient mc) {
        var board = mc.player.networkHandler.getScoreboard();
        return board.getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
    }

    /** Am Ende des Bildes zuruecksetzen, damit im naechsten wieder gezeichnet wird. */
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void saturn$zuruecksetzen(DrawContext ctx, float tickDelta, CallbackInfo ci) {
        saturn$gezeichnet = false;
    }
}