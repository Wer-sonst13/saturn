package gg.saturn;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
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
        int h = 26;
        ctx.fill(0, 0, width, h, 0xE60B0D12);
        Ui.outline(ctx, 0, h - 1, width, 1, Ui.withAlpha(t.accent, 0.35f));

        // Kleines Logo direkt vor dem Namen, wie bei Norisk.
        //
        // Ueber das Modul "icon" ein- und ausschaltbar. Ist es aus, rueckt
        // der Titel nach links und der Platz bleibt frei.
        int x = 8;
        Module icon = Module.get("icon");
        if (icon == null || icon.enabled) {
            int lg = 14;
            int lgX = x, lgY = (h - lg) / 2;
            Ui.logo(ctx, lgX, lgY, lg, t);
            x = lgX + lg + 3 + 5;
        }
        String gross = Ui.up(title);
        int titelBreite = textRenderer.getWidth(gross);

        // "Zurueck" rechts reservieren, damit der Untertitel nicht darunter laeuft
        int limit = width - 8;
        if (back) limit = Math.min(limit, width - 54 - 8);

        if (x + titelBreite <= limit) {
            Ui.text(ctx, textRenderer, gross, x, 9, t.text);
            x += titelBreite + 8;
        }

        // Untertitel nur zeichnen, wenn er zwischen Titel und "Zurueck" passt
        if (subtitle != null && !subtitle.isEmpty()
                && x + textRenderer.getWidth(subtitle) <= limit) {
            Ui.text(ctx, textRenderer, subtitle, x, 10, t.dim);
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
        skalierungBegrenzen();
        mipmapsAbschalten();
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

    // ------------------------------------------------------------- GUI-Skalierung
    //
    // Minecraft zeichnet jedes Fenster in einen Puffer, der genau ein
    // Fenstergross ist, und skaliert ihn dann auf das Fache der eingestellten
    // GUI-Skalierung hoch. Bei Faktor 4 wird das Menue also in 480x260 Pixel
    // gezeichnet und vierfach hochgezogen - die Pixelbuchstaben werden dabei
    // weich, und es passt kaum etwas auf den Bildschirm.
    //
    // Deshalb wird die Skalierung auf hoechstens SATURN_MAX_GUI heruntergesetzt,
    // solange ein Saturn-Fenster offen ist. Beim Schliessen kommt der
    // ursprüngliche Wert wieder zurueck, damit die Einstellung des Spielers
    // unangetastet bleibt.

    private static final int SATURN_MAX_GUI = 3;
    private static int gespeicherteSkalierung = -1;
    private static int gespeicherteMipmaps = -1;

    /**
     * Mipmaps abschalten, solange ein Saturn-Fenster offen ist.
     *
     * Das ist vermutlich die eigentliche Ursache der unscharfen Schrift:
     * Minecraft schaltet die Texturfilterung ab, sobald "Mipmap-Ebenen" groesser
     * als 0 ist, von NEAREST auf LINEAR um. Pixel-Schrift, die dann noch
     * hochskaliert wird, wird dabei weich - und zwar unabhaengig von der
     * GUI-Skalierung, weshalb jede Stufe gleich unscharf aussah.
     */
    private void mipmapsAbschalten() {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        net.minecraft.client.option.SimpleOption<Integer> opt = mc.options.getMipmapLevels();
        if (opt == null) return;
        int jetzt = opt.getValue();
        if (jetzt <= 0) return;
        if (gespeicherteMipmaps < 0) gespeicherteMipmaps = jetzt;
        opt.setValue(0);
        // Die Texturen einmal neu aufbauen - ohne das wirkt die Aenderung erst
        // nach einem Neustart.
        try {
            mc.setMipmapLevels(0);
        } catch (Throwable t) {
            // Manche Versionen kennen die Methode nicht. Dann bleibt es bei der
            // Option - schlimmstenfalls ist es nur etwas weicher.
        }
    }

    /** Stellt die Mipmap-Einstellung des Spielers wieder her. */
    private void mipmapsZurueck() {
        if (gespeicherteMipmaps < 0) return;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        int wert = gespeicherteMipmaps;
        gespeicherteMipmaps = -1;
        if (mc == null || mc.options == null) return;
        try {
            mc.setMipmapLevels(wert);
        } catch (Throwable t) {
            // siehe oben
        }
    }

    /**
     * Setzt die GUI-Skalierung auf einen Wert, bei dem das Menue scharf bleibt.
     *
     * Nicht einfach auf SATURN_MAX_GUI begrenzen: die Skalierung wirkt als
     * Teiler der Bildschirmbreite, und Minecraft rundet dabei ab. Bei einer
     * Breite, die durch den gewaehlten Wert nicht glatt teilbar ist, entsteht
     * ein Rest, den Minecraft zusaetzlich auf einmal Pixel rundet - und genau
     * dieser eine Pixel Versatz macht die Pixelbuchstaben unscharf. Deshalb
     * wird der groesste Wert gesucht, der die Breite glatt teilt.
     */
    private void skalierungBegrenzen() {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        net.minecraft.client.option.SimpleOption<Integer> opt = mc.options.getGuiScale();
        if (opt == null) return;

        int jetzt = opt.getValue();
        int fensterBreite = mc.getWindow().getScaledWidth() * jetzt;
        int ziel = guenstigsteSkalierung(fensterBreite);
        if (ziel >= jetzt) return;
        if (gespeicherteSkalierung < 0) gespeicherteSkalierung = jetzt;
        opt.setValue(ziel);
    }

    /**
     * Der beste GUI-Faktor fuer eine Fensterbreite.
     *
     * Gesucht wird der groesste Wert von SATURN_MAX_GUI abwaerts bis 1, bei
     * dem die Breite ohne Rest teilbar ist. Ein Rest ist genau das, was die
     * Buchstaben unscharf macht. Passt kein Wert, wird der Faktor genommen, der
     * der Breite am naechsten kommt - unscharf ist dann zwar moeglich, aber
     * niemals so schlimm wie bei 4.
     */
    private static int guenstigsteSkalierung(int fensterBreite) {
        int bester = 1;
        int kleinsterRest = Integer.MAX_VALUE;
        for (int s = SATURN_MAX_GUI; s >= 1; s--) {
            int rest = fensterBreite % s;
            if (rest == 0) return s;                 // geht genau: sofort nehmen
            if (rest < kleinsterRest) {
                kleinsterRest = rest;
                bester = s;
            }
        }
        return bester;
    }

    /** stellt die Skalierung des Spielers wieder her. */
    private void skalierungZurueck() {
        if (gespeicherteSkalierung < 0) return;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        int wert = gespeicherteSkalierung;
        gespeicherteSkalierung = -1;
        if (mc == null || mc.options == null) return;
        net.minecraft.client.option.SimpleOption<Integer> opt = mc.options.getGuiScale();
        if (opt == null) return;
        opt.setValue(wert);
    }

    @Override
    public void removed() {
        skalierungZurueck();
        mipmapsZurueck();
        super.removed();
    }
}