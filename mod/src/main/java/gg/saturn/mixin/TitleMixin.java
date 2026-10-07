package gg.saturn.mixin;

import gg.saturn.Effects;
import gg.saturn.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TITLES: verschiebt und skaliert Titel und Untertitel.
 * Beide werden hier selbst gezeichnet, damit Position und Größe frei sind.
 */
@Mixin(InGameHud.class)
public class TitleMixin {

    @Shadow
    private Text title;

    @Shadow
    private Text subtitle;

    @Shadow
    public int getTicks() {
        throw new AssertionError();
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true, require = 0)
    private void saturn$titles(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!Effects.titlesOn) return;
        Module m = Module.get("titles");
        if (m == null) return;

        ci.cancel();

        var tr = MinecraftClient.getInstance().textRenderer;
        int ticks = getTicks();
        if (ticks <= 0 || title == null) return;

        final int fadeIn = 10, stay = 60, fadeOut = 20;
        float opacity;
        if (ticks < fadeIn) {
            opacity = ticks / (float) fadeIn;
        } else if (ticks > fadeIn + stay) {
            opacity = Math.max(0f, (fadeIn + stay + fadeOut - ticks) / (float) fadeOut);
        } else {
            opacity = 1f;
        }
        if (opacity <= 0f) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float sc = (float) Math.max(0.5, Math.min(3.0, m.num("scale", 1.0)));
        int alpha = Math.round(255 * opacity) << 24;

        var ms = ctx.getMatrices();
        ms.pushMatrix();
        ms.translate((float) (w / 2.0 + m.num("x", 0)), (float) (h / 2.0 + m.num("y", 0)));
        ms.scale(sc, sc);
        ms.translate(-w / 2.0f, -h / 2.0f);

        int y = 10;
        ctx.drawText(tr, title, (w - tr.getWidth(title)) / 2, y, 0xFFFFFFFF | alpha, false);
        if (m.flag("showSubtitle", true) && subtitle != null) {
            ctx.drawText(tr, subtitle, (w - tr.getWidth(subtitle)) / 2, y + 12, 0xFF808080 | alpha, false);
        }
        ms.popMatrix();
    }
}