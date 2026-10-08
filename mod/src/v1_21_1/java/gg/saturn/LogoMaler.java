package gg.saturn;

import net.minecraft.client.gui.DrawContext;

/**
 * Zeichnet das Saturn-Logo.
 *
 * Fuer 1.21.1. Dort gibt es noch kein RenderLayer und keine
 * Funktions-Variante von drawTexture - der Aufruf nimmt nur die Identifier.
 */
final class LogoMaler {

    private LogoMaler() {}

    static void malen(DrawContext ctx, int x, int y, int groesse) {
        ctx.drawTexture(Logo.LOGO, x, y, 0.0f, 0.0f,
                groesse, groesse, groesse, groesse);
    }
}