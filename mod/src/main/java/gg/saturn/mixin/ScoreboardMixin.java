package gg.saturn.mixin;

import gg.saturn.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Laesst Minecraft sein eigenes Scoreboard zeichnen und veraendert nur noch
 * Groesse und Position.
 *
 * Warum nicht selbst zeichnen: eine eigene Anlage muss Farben, §-Codes, die
 * Schrift aus dem Texturpaket des Servers, Team-Praefixe und die
 * Team-Farben selbst erledigen. Das war die Ursache fuer leere Zeilen,
 * fehlende Icons, grauen Text und doppelte Darstellung. Vanilla kann das
 * alles von Haus aus - also macht Vanilla das auch.
 *
 * Groesse und Position kommen als Matrix-Transformation um die
 * Zeichnung herum: vor dem Zeichnen verschieben und strecken, danach
 * wieder zuruecknehmen. Es wird bewusst NICHT abgebrochen - dann
 * zeichnet Vanilla genau einmal.
 *
 * require = 0: passt die Methode in dieser MC-Version nicht, laeuft alles normal.
 */
@Mixin(InGameHud.class)
public class ScoreboardMixin {

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$beginn(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (objective == null) return;

        Module mod = Module.get("scoreboard");
        if (mod == null) return;

        if (!mod.enabled || mod.flag("hideScoreboard", false)) {
            ci.cancel();          // ausgeschaltet: gar nichts zeichnen
            return;
        }

        float scale = (float) Math.max(0.3, Math.min(3.0, mod.num("scale", 1.0)));
        double dx = mod.x;
        double dy = mod.y;

        MatrixStack ms = ctx.getMatrices();
        ms.push();

        // Um die eigene Mitte strecken, nicht um den Bildursprung.
        //
        // ms.scale() dehnt immer um (0,0), also die linke obere Ecke. Das
        // Scoreboard sitzt aber am rechten Rand - bei 1,2 rutschte es einfach
        // aus dem Bild und die Groesseneinstellung wirkte wie tot.
        //
        // Dreimal um denselben Punkt herum: hin, strecken, zurueck. Dann
        // bleibt der rechte Rand stehen und das Feld waechst nach links.
        int pivotX = ((DrawContext) (Object) ctx).getScaledWindowWidth();
        int pivotY = MinecraftClient.getInstance().getWindow().getScaledHeight() / 2;
        ms.translate(pivotX, pivotY, 0);
        if (scale != 1.0f) ms.scale(scale, scale, 1f);
        ms.translate(-pivotX, -pivotY, 0);

        // Und danach den Wunschversatz des Spielers.
        ms.translate(dx, dy, 0);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("RETURN"), require = 0)
    private void saturn$ende(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        Module mod = Module.get("scoreboard");
        if (mod == null) return;
        if (!mod.enabled || mod.flag("hideScoreboard", false)) return;
        if (objective == null) return;

        // Muss zu HEAD passen, sonst waere die Matrix schief. Fehlt der
        // Gegenpart, faellt das hier auf - ein pop() zu viel waere schlimmer.
        try {
            ctx.getMatrices().pop();
        } catch (Throwable ignoriert) {
            // dann eben nicht
        }
    }
}