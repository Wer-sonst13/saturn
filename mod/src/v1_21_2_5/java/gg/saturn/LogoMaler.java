package gg.saturn;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;

/**
 * Zeichnet das Saturn-Logo.
 *
 * Fuer 1.21.2 bis 1.21.5. Ab 1.21.6 nimmt drawTexture statt einer Funktion
 * ein RenderPipeline an - siehe die gleichnamige Datei dort.
 */
final class LogoMaler {

    private LogoMaler() {}

    static void malen(DrawContext ctx, int x, int y, int groesse) {
        ctx.drawTexture(RenderLayer::getGuiTextured, Logo.LOGO, x, y,
                0.0f, 0.0f, groesse, groesse, groesse, groesse);
    }
}