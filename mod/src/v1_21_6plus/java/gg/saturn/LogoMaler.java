package gg.saturn;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

/**
 * Zeichnet das Saturn-Logo.
 *
 * Fuer 1.21.6 bis 1.21.11. Dort nimmt drawTexture ein RenderPipeline an,
 * RenderLayer::getGuiTextured gibt es nicht mehr.
 */
final class LogoMaler {

    private LogoMaler() {}

    static void malen(DrawContext ctx, int x, int y, int groesse) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, Logo.LOGO, x, y,
                0.0f, 0.0f, groesse, groesse, groesse, groesse);
    }
}