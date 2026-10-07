package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

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
                rasterOben() + (int) scroll + zeile * (KARTEN_H + LUECKE), b, KARTEN_H};
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

        // Titel oben in der Leiste
        Icons.draw(ctx, "bolt", x + LEISTE_B / 2 - 4, y + 8, 1, t.accent, "LINE");

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
        List<String> cats = new ArrayList<>();
        cats.add("ALL");
        cats.addAll(Modules.categories());
        int cx = x + RAND;
        for (String c : cats) {
            String label = Ui.up(c);
            int bw = textRenderer.getWidth(label) + 14;
            boolean hover = Ui.inside(mx, my, cx, y + 7, bw, 18);
            boolean an = c.equals(kategorie);
            if (an) {
                ctx.fill(cx, y + 7, cx + bw, y + 25, Ui.withAlpha(t.accent, 0.22f));
                Ui.outline(ctx, cx, y + 7, bw, 18, Ui.withAlpha(t.accent, 0.7f));
            } else if (hover) {
                ctx.fill(cx, y + 7, cx + bw, y + 25, Ui.withAlpha(t.text, 0.07f));
            }
            Ui.text(ctx, textRenderer, label, cx + 7, y + 13, an ? t.text : t.dim);
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
            int schalterB = 30;
            int dreiB = 10;
            int tw = k[2] - (tx - k[0]) - 10 - schalterB - dreiB;

            Ui.text(ctx, textRenderer, Ui.up(m.name), tx, k[1] + 7,
                    m.enabled ? t.text : Ui.withAlpha(t.dim, 0.95f));

            for (String zeile : Ui.wrap(textRenderer, m.desc, Math.max(20, tw), 2)) {
                Ui.text(ctx, textRenderer, zeile, tx, k[1] + 18, Ui.withAlpha(t.dim, 0.85f));
            }

            // Schalter
            int sw = 26, sh = 13;
            Ui.toggle(ctx, k[0] + k[2] - sw - 16, k[1] + KARTEN_H - sh - 8, sw, sh,
                    m.enabled, t, hover);
            // Drei-Punkte-Knopf
            int dx = k[0] + k[2] - dreiB - 6, dy = k[1] + KARTEN_H / 2 - 5;
            ctx.fill(dx, dy, dx + dreiB, dy + 1, Ui.withAlpha(t.text, 0.6f));
            ctx.fill(dx, dy + 4, dx + dreiB, dy + 5, Ui.withAlpha(t.text, 0.6f));
            ctx.fill(dx, dy + 8, dx + dreiB, dy + 9, Ui.withAlpha(t.text, 0.6f));

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

    private void drawProfil(DrawContext ctx, Ui.Theme t, int mx, int my) {
        List<String> namen = ConfigStore.profileNames();
        int w = 160, h = Math.max(30, namen.size() * 16 + 10);
        int x = fensterX() + 6, y = TREFFER[4] + TREFFER[5] + 4;
        ctx.fill(x, y, x + w, y + h, 0xE60B0D12);
        Ui.outline(ctx, x, y, w, h, Ui.withAlpha(t.accent, 0.5f));
        for (int i = 0; i < namen.size(); i++) {
            String n = namen.get(i);
            boolean hover = Ui.inside(mx, my, x + 3, y + 5 + i * 16, w - 6, 14);
            boolean aktiv = n.equals(ConfigStore.activeProfile());
            if (hover || aktiv) ctx.fill(x + 3, y + 5 + i * 16, x + w - 3, y + 19 + i * 16,
                    Ui.withAlpha(t.accent, aktiv ? 0.25f : 0.12f));
            Ui.text(ctx, textRenderer, (aktiv ? "▸ " : "   ") + Ui.up(n), x + 6, y + 8 + i * 16,
                    aktiv ? t.accent : t.text);
        }
        profilFeld[0] = x; profilFeld[1] = y; profilFeld[2] = w; profilFeld[3] = h;
        profilAnzahl = namen.size();
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
                if (Ui.inside(mx, my, x + 3, y + 5 + i * 16, w - 6, 14)) {
                    ConfigStore.activateProfile(namen.get(i));
                    ConfigStore.saveActiveProfile();
                    profilOffen = false;
                    return true;
                }
            }
            profilOffen = false;
            return true;
        }

        // Linke Leiste
        for (int i = 0; i < BEREICHE.length; i++) {
            if (!Ui.inside(mx, my, TREFFER[i * 4], TREFFER[i * 4 + 1], TREFFER[i * 4 + 2], TREFFER[i * 4 + 3])) continue;
            switch (i) {
                case 1 -> profilOffen = true;
                case 2 -> client.setScreen(new ModuleSettingsScreen(Module.get("theme"), this));
                default -> { }
            }
            return true;
        }

        // Suche
        if (Ui.inside(mx, my, sucheFeld[0], sucheFeld[1], sucheFeld[2], sucheFeld[3])) {
            sucheAktiv = !sucheAktiv;
            return true;
        }
        sucheAktiv = false;

        // Karten
        for (int i = 0; i < gezeigt.size(); i++) {
            int[] k = kartenTreffer.length > i * 4 + 3
                    ? new int[]{kartenTreffer[i * 4], kartenTreffer[i * 4 + 1],
                             kartenTreffer[i * 4 + 2], kartenTreffer[i * 4 + 3]}
                    : null;
            if (k == null) continue;
            if (!Ui.inside(mx, my, k[0], k[1], k[2], k[3])) continue;
            Module m = gezeigt.get(i);
            int schalterX = k[0] + k[2] - 26 - 16;
            if (Ui.inside(mx, my, schalterX, k[1] + KARTEN_H - 13 - 8, 26, 13)) {
                m.enabled = !m.enabled;
                ConfigStore.save();
                return true;
            }
            int dx = k[0] + k[2] - 10 - 6;
            if (Ui.inside(mx, my, dx - 4, k[1] + KARTEN_H / 2 - 6, 18, 12)) {
                client.setScreen(new ModuleSettingsScreen(m, this));
                return true;
            }
            // Klick auf die Karte: Bildschirm, falls das Modul einen hat
            if (m.hasMenu && kartenTreffer != null) {
                Module mm = Module.get(m.id);
                if (mm != null && mm.hasMenu) client.setScreen(new ModuleSettingsScreen(mm, this));
                return true;
            }
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
        if (sucheAktiv) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!suche.isEmpty()) suche = suche.substring(0, suche.length() - 1);
                sammeln();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == 257) {
                sucheAktiv = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        ConfigStore.save();
        client.setScreen(parent);
    }
}