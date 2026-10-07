package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Modul-Menü: ein Fenster in der Mitte, nicht über den ganzen Bildschirm.
 *
 * Aufbau: links eine schmale Leiste mit den Bereichen, oben die Kategorien
 * und die Suche, darunter zwei breite Kartenspalten, unten eine Fußzeile.
 */
public class SaturnMenuScreen extends SaturnScreen {

    // --- Fenster ---
    private static final int FENSTER_B = 620;
    private static final int FENSTER_H = 340;
    private static final int LEISTE_B = 58;     // linke Icon-Leiste
    private static final int KOPF_H = 34;       // Kategorien + Suche
    private static final int FUSS_H = 22;       // Fußzeile
    private static final int RAND = 8;

    // --- Karten ---
    private static final int SPALTEN = 2;
    private static final int KARTEN_B = 178;
    private static final int KARTEN_H = 44;
    private static final int LUECKE = 6;

    // Bereiche der linken Leiste. Freunde, Emotes, Kosmetik und Norisk+ gibt
    // es hier bewusst nicht - die gehoeren nicht zu diesem Client.
    private static final String[] BEREICHE = {"MODS", "PROFIL", "EINSTELLUNGEN"};
    private static final String[] BEREICH_ICON = {"layers", "user", "grid"};
    private static final int[] TREFFER = new int[3 * 4];

    private String kategorie = "ALL";
    private String suche = "";
    private boolean sucheAktiv;
    private boolean profilOffen;
    private boolean settingsOffen;

    private List<Module> gezeigt = new ArrayList<>();
    private double scroll;
    private int[] kartenTreffer = new int[0];
    private int[] tabTreffer;
    private List<String> tabNamen = new ArrayList<>();
    private final int[] sucheFeld = new int[4];
    private final int[] profilFeld = new int[4];
    private int profilAnzahl;

    public SaturnMenuScreen(Screen parent) {
        super("Mods", parent);
    }

    // ------------------------------------------------------------------ Layout

    private int fensterX() { return width / 2 - FENSTER_B / 2; }
    private int fensterY() { return Math.max(6, height / 2 - FENSTER_H / 2); }
    private int inhaltX() { return fensterX() + LEISTE_B; }
    private int inhaltB() { return FENSTER_B - LEISTE_B; }
    private int rasterOben() { return fensterY() + KOPF_H + 4; }
    private int rasterUnten() { return fensterY() + FENSTER_H - FUSS_H - 4; }

    @Override
    protected void init() {
        super.init();
        sammeln();
        for (int i = 0; i < BEREICHE.length; i++) {
            int y = fensterY() + KOPF_H + 10 + i * 44;
            TREFFER[i * 4] = fensterX() + 6;
            TREFFER[i * 4 + 1] = y;
            TREFFER[i * 4 + 2] = LEISTE_B - 12;
            TREFFER[i * 4 + 3] = 38;
        }
    }

    // ------------------------------------------------------------------ Auswahl

    private void sammeln() {
        gezeigt = new ArrayList<>();
        String such = suche.toLowerCase(java.util.Locale.ROOT).trim();
        for (Module m : Module.all()) {
            if (!kategorie.equals("ALL") && !m.category.equalsIgnoreCase(kategorie)) continue;
            if (!such.isEmpty()
                    && !m.name.toLowerCase(java.util.Locale.ROOT).contains(such)
                    && !m.desc.toLowerCase(java.util.Locale.ROOT).contains(such)) continue;
            gezeigt.add(m);
        }
    }

    private int spalten() {
        int nutzbar = inhaltB() - RAND * 2;
        return Math.max(1, Math.min(SPALTEN, nutzbar / KARTEN_B));
    }

    private int kartenB() {
        int nutzbar = inhaltB() - RAND * 2;
        int n = spalten();
        return (nutzbar - LUECKE * (n - 1)) / n;
    }

    private int inhaltH() {
        int zeilen = (gezeigt.size() + spalten() - 1) / spalten();
        return zeilen * (KARTEN_H + LUECKE);
    }

    /** Karte an Position {x, y}. */
    private int[] karte(int index) {
        int n = spalten();
        int b = kartenB();
        int sp = index % n, zeile = index / n;
        return new int[]{inhaltX() + RAND + sp * (b + LUECKE),
                rasterOben() - (int) scroll + zeile * (KARTEN_H + LUECKE), b, KARTEN_H};
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        cacheMouse(mx, my);
        Ui.Theme t = Ui.theme();
        drawBackdrop(ctx);

        int fx = fensterX(), fy = fensterY();

        // Fensterrahmen
        Ui.panel(ctx, fx, fy, FENSTER_B, FENSTER_H, t);
        Ui.outline(ctx, fx, fy, FENSTER_B, FENSTER_H, Ui.withAlpha(t.accent, 0.30f));

        drawLeiste(ctx, t, mx, my);
        drawKopf(ctx, t, mx, my);

        // Raster
        int oben = rasterOben(), unten = rasterUnten();
        scroll = clampScroll(scroll, inhaltH(), unten - oben);
        ctx.enableScissor(inhaltX(), oben - 1, fx + FENSTER_B - 1, unten + 1);
        drawRaster(ctx, t, mx, my);
        ctx.disableScissor();
        Ui.scrollbar(ctx, fx + FENSTER_B - 6, oben, unten - oben,
                inhaltH(), unten - oben, scroll, t);

        drawFuss(ctx, t);

        if (profilOffen) drawProfil(ctx, t, mx, my);
        if (settingsOffen) drawSettings(ctx, t, mx, my);

        super.render(ctx, mx, my, delta);
    }

    private void drawLeiste(DrawContext ctx, Ui.Theme t, int mx, int my) {
        int x = fensterX(), y = fensterY();
        ctx.fill(x, y, x + LEISTE_B, y + FENSTER_H, Ui.withAlpha(t.accent, 0.06f));
        Ui.outline(ctx, x + LEISTE_B - 1, y, 1, FENSTER_H, Ui.withAlpha(t.accent, 0.20f));

        // Saturn-Logo oben links in der Ecke des Fensters.
        //
        // Die Textur ist 64x64 und fast schwarz (gemessen: RGB 0,0,0 bis
        // 25,24,20). Ohne Rahmen verschwindet sie auf dem dunklen Grund -
        // sie wurde lange fuer "nicht geladen" gehalten. Ein heller Rahmen
        // macht die Kanten sichtbar.
        Module iconMod = Module.get("icon");
        if (iconMod == null || iconMod.enabled) Ui.logo(ctx, x + 9, y + 10, 22, t);

        for (int i = 0; i < BEREICHE.length; i++) {
            int bx = TREFFER[i * 4], by = TREFFER[i * 4 + 1];
            int bw = TREFFER[i * 4 + 2], bh = TREFFER[i * 4 + 3];
            boolean hover = Ui.inside(mx, my, bx, by, bw, bh);
            boolean an = i == 0;
            if (an) ctx.fill(bx, by, bx + bw, by + bh, Ui.withAlpha(t.accent, 0.18f));
            else if (hover) ctx.fill(bx, by, bx + bw, by + bh, Ui.withAlpha(t.text, 0.07f));
            Icons.draw(ctx, BEREICH_ICON[i], bx + 6, by + 6, 2,
                    an || hover ? t.accent : Ui.withAlpha(t.text, 0.75f), "LINE");
            Ui.textCentered(ctx, textRenderer, BEREICHE[i], bx + bw / 2, by + 26,
                    an || hover ? t.text : Ui.withAlpha(t.dim, 0.9f));
        }
    }

    private void drawKopf(DrawContext ctx, Ui.Theme t, int mx, int my) {
        int x = inhaltX(), y = fensterY();
        Ui.outline(ctx, x, y + KOPF_H - 1, inhaltB(), 1, Ui.withAlpha(t.accent, 0.18f));

        // Kategorien
        //
        // Wichtig: die Trefferkoordinaten werden mit gespeichert. Vorher wurden
        // die Tabs nur gezeichnet, aber nie abgefragt - ein Klick auf ALL,
        // RENDER, HUD usw. blieb deshalb wirkungslos.
        List<String> cats = new ArrayList<>();
        cats.add("ALL");
        cats.addAll(Modules.categories());
        tabNamen = cats;
        tabTreffer = new int[cats.size() * 4];
        int cx = x + RAND;
        for (int i = 0; i < cats.size(); i++) {
            String c = cats.get(i);
            String label = Ui.up(c);
            int bw = textRenderer.getWidth(label) + 14;
            boolean hover = Ui.inside(mx, my, cx, y + 7, bw, 18);
            boolean an = c.equalsIgnoreCase(kategorie);
            if (an) {
                ctx.fill(cx, y + 7, cx + bw, y + 25, Ui.withAlpha(t.accent, 0.22f));
                Ui.outline(ctx, cx, y + 7, bw, 18, Ui.withAlpha(t.accent, 0.7f));
            } else if (hover) {
                ctx.fill(cx, y + 7, cx + bw, y + 25, Ui.withAlpha(t.text, 0.07f));
            }
            Ui.text(ctx, textRenderer, label, cx + 7, y + 13, an ? t.text : t.dim);
            tabTreffer[i * 4] = cx;
            tabTreffer[i * 4 + 1] = y + 7;
            tabTreffer[i * 4 + 2] = bw;
            tabTreffer[i * 4 + 3] = 18;
            cx += bw + 4;
        }

        // Suche rechts
        int sw = 120;
        int sx = x + inhaltB() - sw - RAND;
        ctx.fill(sx, y + 7, sx + sw, y + 25, Ui.withAlpha(t.text, 0.07f));
        Ui.outline(ctx, sx, y + 7, sw, 18, sucheAktiv
                ? Ui.withAlpha(t.accent, 0.8f) : Ui.withAlpha(t.text, 0.18f));
        String anzeige = suche.isEmpty() ? "SUCHE..." : suche;
        Ui.text(ctx, textRenderer, anzeige, sx + 6, y + 13,
                suche.isEmpty() ? Ui.withAlpha(t.dim, 0.8f) : t.text);
        sucheFeld[0] = sx; sucheFeld[1] = y + 7;
        sucheFeld[2] = sw; sucheFeld[3] = 18;
    }

    private void drawRaster(DrawContext ctx, Ui.Theme t, int mx, int my) {
        kartenTreffer = new int[gezeigt.size() * 4];
        String stil = iconStil();
        for (int i = 0; i < gezeigt.size(); i++) {
            int[] k = karte(i);
            if (k[1] + KARTEN_H < rasterOben() - 4 || k[1] > rasterUnten() + 4) continue;
            Module m = gezeigt.get(i);
            boolean hover = Ui.inside(mx, my, k[0], k[1], k[2], KARTEN_H);
            Ui.card(ctx, k[0], k[1], k[2], KARTEN_H, t, hover, m.enabled);

            Icons.draw(ctx, m.icon, k[0] + 7, k[1] + 7, 2,
                    m.enabled ? t.accent : Ui.withAlpha(t.text, 0.7f), stil);

            int tx = k[0] + 7 + Icons.SIZE * 2 + 7;
            int dreiB = 10;
            // Rechts Platz fuer die drei Punkte, sonst laeuft die Beschreibung
            // darunter.
            int tw = k[2] - (tx - k[0]) - 10 - (dreiB + 6);

            Ui.text(ctx, textRenderer, Ui.up(m.name), tx, k[1] + 7,
                    m.enabled ? t.text : Ui.withAlpha(t.dim, 0.95f));

            // Die y-Position muss mit jeder Zeile steigen. Vorher standen
            // beide Zeilen an derselben Stelle und ueberlagerten sich.
            int ty = k[1] + 18;
            for (String zeile : Ui.wrap(textRenderer, m.desc, Math.max(20, tw), 2)) {
                Ui.text(ctx, textRenderer, zeile, tx, ty, Ui.withAlpha(t.dim, 0.85f));
                ty += 9;
            }

            // Drei-Punkte-Knopf fuer die Einstellungen. Der Schalter ist
            // bewusst weg: die ganze Karte schaltet das Modul.
            int dx = k[0] + k[2] - dreiB - 6, dy = k[1] + KARTEN_H / 2 - 5;
            int dFarbe = Ui.withAlpha(hover ? t.text : t.dim, hover ? 0.95f : 0.7f);
            ctx.fill(dx, dy, dx + dreiB, dy + 1, dFarbe);
            ctx.fill(dx, dy + 4, dx + dreiB, dy + 5, dFarbe);
            ctx.fill(dx, dy + 8, dx + dreiB, dy + 9, dFarbe);

            kartenTreffer[i * 4] = k[0];
            kartenTreffer[i * 4 + 1] = k[1];
            kartenTreffer[i * 4 + 2] = k[2];
            kartenTreffer[i * 4 + 3] = KARTEN_H;
        }
    }

    private void drawFuss(DrawContext ctx, Ui.Theme t) {
        int x = inhaltX(), y = fensterY() + FENSTER_H - FUSS_H;
        Ui.outline(ctx, x, y, inhaltB(), 1, Ui.withAlpha(t.accent, 0.14f));
        int an = 0;
        for (Module m : Module.all()) if (m.enabled) an++;
        Ui.text(ctx, textRenderer, an + " VON " + Modules.count() + " MODULEN AKTIV", x + RAND, y + 7, t.dim);
        String profil = ConfigStore.activeProfile();
        String rechts = "PROFIL: " + (profil == null || profil.isEmpty() ? "STANDARD" : Ui.up(profil));
        Ui.text(ctx, textRenderer, rechts, x + inhaltB() - RAND - Ui.width(textRenderer, rechts), y + 7, t.dim);
    }

    /**
     * Profilauswahl.
     *
     * Sie klappt rechts neben der linken Leiste auf, nicht ueber ihr - vorher
     * lag sie ueber den Karten und wirkte wie ein schwarzer Fleck.
     */
    private void drawProfil(DrawContext ctx, Ui.Theme t, int mx, int my) {
        List<String> namen = ConfigStore.profileNames();
        int eintragH = 18;
        int w = 150;
        int h = 20 + namen.size() * eintragH + 8;
        int x = fensterX() + LEISTE_B + 6;
        int y = fensterY() + KOPF_H + 8;
        // Nicht aus dem Fenster herausschieben.
        x = Math.min(x, fensterX() + FENSTER_B - w - 6);
        y = Math.min(y, fensterY() + FENSTER_H - h - 6);

        Ui.panel(ctx, x, y, w, h, t);
        Ui.outline(ctx, x, y, w, h, Ui.withAlpha(t.accent, 0.45f));
        Ui.text(ctx, textRenderer, Ui.up("PROFIL WECHSELN"), x + 8, y + 6, Ui.withAlpha(t.dim, 0.95f));
        Ui.outline(ctx, x + 6, y + 16, w - 12, 1, Ui.withAlpha(t.accent, 0.18f));

        String aktiv = ConfigStore.activeProfile();
        for (int i = 0; i < namen.size(); i++) {
            String n = namen.get(i);
            int ey = y + 20 + i * eintragH;
            boolean hover = Ui.inside(mx, my, x + 4, ey, w - 8, eintragH - 2);
            boolean an = n.equals(aktiv);
            if (an || hover) {
                ctx.fill(x + 4, ey, x + w - 4, ey + eintragH - 2,
                        Ui.withAlpha(t.accent, an ? 0.26f : 0.12f));
            }
            Ui.text(ctx, textRenderer, (an ? "▸ " : "  ") + Ui.up(n), x + 9, ey + 5,
                    an ? t.accent : (hover ? t.text : Ui.withAlpha(t.dim, 0.95f)));
        }
        if (namen.isEmpty()) {
            Ui.text(ctx, textRenderer, "KEINE PROFILE", x + 9, y + 26, Ui.withAlpha(t.dim, 0.8f));
        }

        profilFeld[0] = x; profilFeld[1] = y + 20; profilFeld[2] = w; profilFeld[3] = namen.size() * eintragH;
    }

    private void drawSettings(DrawContext ctx, Ui.Theme t, int mx, int my) {
        // Kein eigener Einstellungs-Bildschirm vorhanden; deshalb die
        // vorhandene generische Seite fuer das Theme-Modul nehmen.
    }

    private String iconStil() {
        Module m = Module.get("icon");
        return m == null ? "LINE" : m.choice("style", "LINE");
    }

    // ------------------------------------------------------------------ Maus

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (profilOffen) {
            List<String> namen = ConfigStore.profileNames();
            int x = profilFeld[0], y = profilFeld[1], w = profilFeld[2];
            for (int i = 0; i < namen.size(); i++) {
                if (Ui.inside(mx, my, x + 4, y + i * 18, w - 8, 16)) {
                    ConfigStore.activateProfile(namen.get(i));
                    ConfigStore.saveActiveProfile();
                    sammeln();
                    return true;
                }
            }
            // Ein Klick daneben schliesst es wieder.
            profilOffen = false;
            return true;
        }

        // Linke Leiste
        for (int i = 0; i < BEREICHE.length; i++) {
            if (!Ui.inside(mx, my, TREFFER[i * 4], TREFFER[i * 4 + 1], TREFFER[i * 4 + 2], TREFFER[i * 4 + 3])) continue;
            switch (i) {
                case 1 -> profilOffen = !profilOffen;
                case 2 -> client.setScreen(new ModuleSettingsScreen(Module.get("theme"), this));
                default -> { profilOffen = false; }
            }
            return true;
        }

        // Kategorie-Tabs
        if (tabTreffer != null) {
            for (int i = 0; i < tabNamen.size(); i++) {
                if (!Ui.inside(mx, my, tabTreffer[i * 4], tabTreffer[i * 4 + 1],
                        tabTreffer[i * 4 + 2], tabTreffer[i * 4 + 3])) continue;
                kategorie = tabNamen.get(i);
                scroll = 0;
                sammeln();
                return true;
            }
        }

        // Suche
        if (Ui.inside(mx, my, sucheFeld[0], sucheFeld[1], sucheFeld[2], sucheFeld[3])) {
            sucheAktiv = !sucheAktiv;
            return true;
        }
        if (sucheAktiv && button == 0 && !Ui.inside(mx, my, fensterX(), fensterY(), FENSTER_B, FENSTER_H)) {
            sucheAktiv = false;
        }

        // Karten
        for (int i = 0; i < gezeigt.size(); i++) {
            if (kartenTreffer.length < i * 4 + 4) continue;
            int kx = kartenTreffer[i * 4], ky = kartenTreffer[i * 4 + 1];
            int kb = kartenTreffer[i * 4 + 2], kh = kartenTreffer[i * 4 + 3];
            if (!Ui.inside(mx, my, kx, ky, kb, kh)) continue;
            Module m = gezeigt.get(i);

            // Drei Punkte links die Einstellungen auf, sonst schaltet die Karte.
            int dreiX = kx + kb - 16, dreiY = ky + kh / 2 - 6;
            if (Ui.inside(mx, my, dreiX, dreiY, 16, 12)) {
                client.setScreen(new ModuleSettingsScreen(m, this));
                return true;
            }

            m.enabled = !m.enabled;
            ConfigStore.save();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (!profilOffen && Ui.inside(mx, my, inhaltX(), rasterOben(), inhaltB(),
                rasterUnten() - rasterOben())) {
            scroll = applyScroll(scroll, inhaltH(), rasterUnten() - rasterOben(), vAmount);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!profilOffen && Ui.inside(mx, my, inhaltX(), rasterOben(), inhaltB(),
                rasterUnten() - rasterOben())) {
            scroll = clampScroll(scroll - dy, inhaltH(), rasterUnten() - rasterOben());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape beendet zuerst die Suche, nicht das ganze Menue.
        if (sucheAktiv) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                sucheAktiv = false;
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!suche.isEmpty()) suche = suche.substring(0, suche.length() - 1);
                sammeln();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                sucheAktiv = false;
                return true;
            }
            // Buchstaben und Ziffern anhaengen. Ohne das blieb das
            // Suchfeld leer und die Suche tat nichts.
            String zeichen = tasteZuZeichen(keyCode, modifiers);
            if (zeichen != null) {
                suche += zeichen;
                sammeln();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** GLFW-Tastencode zu einem Zeichen, oder null wenn die Taste egal ist. */
    private static String tasteZuZeichen(int keyCode, int modifiers) {
        boolean gross = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        // A..Z liegen hintereinander, deshalb geht das als Bereich.
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_A
                && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
            return gross ? "" + (char) keyCode : "" + (char) (keyCode + 32);
        }
        // Ziffern liegen NICHT hintereinander, deshalb einzeln auflisten.
        int[] ziffern = {
                org.lwjgl.glfw.GLFW.GLFW_KEY_0, org.lwjgl.glfw.GLFW.GLFW_KEY_1,
                org.lwjgl.glfw.GLFW.GLFW_KEY_2, org.lwjgl.glfw.GLFW.GLFW_KEY_3,
                org.lwjgl.glfw.GLFW.GLFW_KEY_4, org.lwjgl.glfw.GLFW.GLFW_KEY_5,
                org.lwjgl.glfw.GLFW.GLFW_KEY_6, org.lwjgl.glfw.GLFW.GLFW_KEY_7,
                org.lwjgl.glfw.GLFW.GLFW_KEY_8, org.lwjgl.glfw.GLFW.GLFW_KEY_9};
        for (int i = 0; i < ziffern.length; i++) if (keyCode == ziffern[i]) return "" + i;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) return " ";
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS) return gross ? "_" : "-";
        return null;
    }

    @Override
    public void close() {
        ConfigStore.save();
        client.setScreen(parent);
    }
}