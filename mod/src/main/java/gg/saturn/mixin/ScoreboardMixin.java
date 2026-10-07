package gg.saturn.mixin;

import gg.saturn.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.joml.Matrix3x2fStack;
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

                // m.scale benutzen, NICHT die Einstellung "scale".
        //
        // Das waren zwei verschiedene Zahlen: der Editor setzt m.scale, das
        // Mausrad aendert m.scale - und gezeichnet wurde die Einstellung.
        // Scrollen tat also gar nichts. Bei allen anderen HUD-Elementen ist
        // es ebenfalls m.scale, das der Editor anfasst.
        float scale = (float) Math.max(0.3, Math.min(3.0, mod.scale));
        double dx = mod.x;
        double dy = mod.y;

        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();

        // Um die eigene Mitte strecken, nicht um den Bildursprung.
        //
        // Skalieren dehnt immer um (0,0), also die linke obere Ecke. Das
        // Scoreboard sitzt aber am rechten Rand - bei 1,2 rutschte es einfach
        // aus dem Bild und die Groesseneinstellung wirkte wie tot.
        //
        // Dreimal um denselben Punkt herum: hin, strecken, zurueck. Dann
        // bleibt der rechte Rand stehen und das Feld waechst nach links.
        int pivotX = ctx.getScaledWindowWidth();
        int pivotY = MinecraftClient.getInstance().getWindow().getScaledHeight() / 2;
        ms.translate(pivotX, pivotY);
        if (scale != 1.0f) ms.scale(scale, scale);
        ms.translate(-pivotX, -pivotY);

        // Und danach den Wunschversatz des Spielers.
        ms.translate((float) dx, (float) dy);
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
            ctx.getMatrices().popMatrix();
        } catch (Throwable ignoriert) {
            // dann eben nicht
        }
    }
}