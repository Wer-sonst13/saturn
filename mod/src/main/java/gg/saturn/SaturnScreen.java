package gg.saturn;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Basis für alle Saturn-Fenster: gemeinsame Zeichenhilfen,
 * Vollbild-Hintergrund und ein schöner Titelkopf.
 */
public abstract class SaturnScreen extends Screen {

    protected final Screen parent;
    protected double scroll;
    protected int mouseX, mouseY;

    protected SaturnScreen(String title, Screen parent) {
        super(Text.literal(title));
        this.parent = parent;
    }

    /** Der Saturn-Hintergrund: abgedunkeltes Spiel + feines Raster. */
    protected void drawBackdrop(DrawContext ctx) {
        ctx.fillGradient(0, 0, width, height, 0xF2050608, 0xF2050608);
        // dezentes Raster wie im Saturn-Menü
        int step = 34;
        for (int y = 0; y < height; y += step) ctx.fill(0, y, width, y + 1, 0x0AFFFFFF);
        for (int x = 0; x < width; x += step) ctx.fill(x, 0, x + 1, height, 0x0AFFFFFF);
        // Ecken abdunkeln
        ctx.fillGradient(0, 0, width, 60, 0x66000000, 0x00000000);
    }

    /** Kopfzeile mit Titel, Untertitel und zurück-Knopf. */
    protected void drawHeader(DrawContext ctx, String title, String subtitle, boolean back) {
        Ui.Theme t = Ui.theme();
        ctx.fill(0, 0, width, 26, 0xE60B0D12);
        Ui.outline(ctx, 0, 25, width, 1, Ui.withAlpha(t.accent, 0.35f));
        Icons.draw(ctx, "bolt", 8, 9, 1, Ui.withAlpha(t.accent, 0.95f), "LINE");
        Ui.text(ctx, textRenderer, Ui.up(title), 22, 9, t.text);
        if (subtitle != null && !subtitle.isEmpty()) {
            Ui.text(ctx, textRenderer, subtitle, 22 + textRenderer.getWidth(Ui.up(title)) + 8, 10, t.dim);
        }
        if (back) {
            Ui.text(ctx, textRenderer, "< ZURÜCK", width - 54, 9, t.dim);
        }
    }

    protected boolean backClicked(double mx, double my) {
        return Ui.inside(mx, my, width - 60, 0, 60, 26);
    }

    protected void goBack() {
        ConfigStore.save();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        mouseX = -1;
        mouseY = -1;
    }

    // Maus wird auch beim Bewegen gebraucht (Tooltip/Hover), darum cachen wir sie.
    protected void cacheMouse(int mx, int my) {
        this.mouseX = mx;
        this.mouseY = my;
    }

    protected boolean hovering(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Scrollt eine Liste mit dem Mausrad. */
    protected static double applyScroll(double scroll, int contentH, int viewH, double amount) {
        if (contentH <= viewH) return 0;
        return Math.max(0, Math.min(contentH - viewH, scroll - amount * 22));
    }

    protected static double clampScroll(double scroll, int contentH, int viewH) {
        if (contentH <= viewH) return 0;
        return Math.max(0, Math.min(contentH - viewH, scroll));
    }

    protected static List<String> asList(List<String> in) {
        return new ArrayList<>(in);
    }
}