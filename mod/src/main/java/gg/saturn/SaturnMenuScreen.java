package gg.saturn;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Saturn-Hauptmenü: Karten-Raster wie bei NoRisk.
 * Klick auf eine Karte schaltet das Modul an/aus, Klick auf "..." öffnet
 * die Einstellungen. Alle Module lassen sich einzeln umschalten.
 */
public class SaturnMenuScreen extends SaturnScreen {

    // Mindestbreite einer Karte. Alles darunter wird unlesbar, dann lieber
    // eine Spalte mit breiteren Karten als drei zu schmale.
    private static final int CARD_W = 150;
    private static final int CARD_H = 46;
    private static final int GAP = 6;

    private String category = "ALL";
    private String search = "";
    private boolean searchOpen;

    private List<String> cats = new ArrayList<>();
    private List<Module> shown = new ArrayList<>();
    private int gridTop, gridBottom;

    public SaturnMenuScreen(Screen parent) {
        super("Saturn Client", parent);
    }

    @Override
    protected void init() {
        super.init();
        gridTop = 52;
        gridBottom = height - 30;
        cats = new ArrayList<>();
        cats.add("ALL");
        cats.addAll(Modules.categories());
        filter();
    }

    private void filter() {
        shown = new ArrayList<>();
        String q = search.toLowerCase();
        for (Module m : Module.all()) {
            if (!category.equals("ALL") && !m.category.equals(category)) continue;
            if (!q.isEmpty() && !(m.name.toLowerCase().contains(q) || m.desc.toLowerCase().contains(q))) continue;
            shown.add(m);
        }
    }

    private int columns() {
        int usable = width - 16;
        return Math.max(1, Math.min(4, usable / (CARD_W + GAP)));
    }

    private int rows() {
        int c = columns();
        return (int) Math.ceil(shown.size() / (double) c);
    }

    private int contentHeight() {
        return rows() * (CARD_H + GAP);
    }

    // ------------------------------------------------------------------ Malen

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        cacheMouse(mx, my);
        Ui.Theme t = Ui.theme();

        drawBackdrop(ctx);
        drawHeader(ctx, "Saturn Client", "v" + version() + "  ·  " + Modules.count() + " Module", true);
        drawTabs(ctx, t);

        int colW = width - 16;
        int listX = 8;

        ctx.enableScissor(listX, gridTop - 1, width - 8, gridBottom + 1);
        scroll = clampScroll(scroll, contentHeight(), gridBottom - gridTop);
        drawGrid(ctx, t, listX, colW);
        ctx.disableScissor();

        Ui.scrollbar(ctx, width - 6, gridTop, gridBottom - gridTop, contentHeight(), gridBottom - gridTop, scroll, t);
        drawFooter(ctx, t, colW);

        super.render(ctx, mx, my, delta);
    }

    private void drawGrid(DrawContext ctx, Ui.Theme t, int listX, int colW) {
        int cols = columns();
        int cw = (colW - GAP * (cols - 1)) / cols;
        int y = gridTop - (int) scroll;
        int mouseX = this.mouseX, mouseY = this.mouseY;

        for (int idx = 0; idx < shown.size(); idx++) {
            Module m = shown.get(idx);
            int col = idx % cols;
            int row = idx / cols;
            int x = listX + col * (cw + GAP);
            int yy = y + row * (CARD_H + GAP);
            if (yy + CARD_H < gridTop - 4 || yy > gridBottom + 4) continue;

            boolean hover = Ui.inside(mouseX, mouseY, x, yy, cw, CARD_H);
            boolean inList = yy >= gridTop - 4 && yy <= gridBottom + 4;
            if (!inList) continue;

            Ui.card(ctx, x, yy, cw, CARD_H, t, hover, m.enabled);

            // Icon
            String style = iconStyle();
            Icons.draw(ctx, m.icon, x + 8, yy + 8, 2, m.enabled ? t.accent : Ui.withAlpha(t.dim, 0.85f), style);

            int tx = x + 8 + Icons.SIZE * 2 + 8;
            // Rechts bleibt Platz fuer den Schalter, sonst laeuft die
            // Beschreibung mitten in ihn hinein.
            int sw = 34;
            int tw = cw - (tx - x) - 10 - (sw + 8);

            // NEW-Badge
            if (m.isNew) {
                Ui.text(ctx, textRenderer, "NEW", x + cw - 26, yy + 5, 0xFFFFD24A);
                tw -= 26;
            }
            if (tw < 24) tw = 24;

            Ui.text(ctx, textRenderer, Ui.up(m.name), tx, yy + 7,
                    m.enabled ? t.text : Ui.withAlpha(t.dim, 0.95f));

            List<String> lines = Ui.wrap(textRenderer, m.desc, tw, 2);
            int ly = yy + 18;
            for (String l : lines) {
                if (ly > yy + CARD_H - 6) break;
                Ui.text(ctx, textRenderer, l, tx, ly, Ui.withAlpha(t.dim, 0.85f));
                ly += 9;
            }

            // Schalter rechts unten
            int sh = 14;
            Ui.toggle(ctx, x + cw - sw - 7, yy + CARD_H - sh - 6, sw, sh, m.enabled, t, hover);
        }
    }

    private void drawTabs(DrawContext ctx, Ui.Theme t) {
        int x = 8, y = 32;
        for (String c : cats) {
            boolean on = c.equals(category);
            int w = textRenderer.getWidth(Ui.up(c)) + 18;
            Ui.button(ctx, textRenderer, x, y, w, 16, c, t, hovering(x, y, w, 16), on);
            x += w + 4;
        }
        // Suche
        String sw = "SUCHE";
        int bw = textRenderer.getWidth(sw) + 20;
        int bx = width - bw - 8;
        if (!searchOpen) {
            Ui.button(ctx, textRenderer, bx, y, bw, 16, "SUCHE", t, hovering(bx, y, bw, 16), false);
        } else {
            Ui.fill(ctx, bx - 6, y, bw + 46, 16, Ui.withAlpha(t.text, 0.07f));
            Ui.outline(ctx, bx - 6, y, bw + 46, 16, t.accent);
            Ui.text(ctx, textRenderer, search + "▌", bx, y + 4, t.text);
        }
        searchHit[0] = searchOpen ? bx - 6 : bx;
        searchHit[1] = y;
        searchHit[2] = searchOpen ? bw + 46 : bw;
        searchHit[3] = 16;
    }

    private final int[] searchHit = new int[4];

    /**
     * Fusszeile. Die drei Angaben werden von links nach rechts aneinander
     * gereiht statt an festen Stellen hingesetzt - bei kleiner Fensterbreite
     * (Minecraft skaliert je nach Aufloesung bis Faktor 4) laufen sie sonst
     * uebereinander.
     */
    private void drawFooter(DrawContext ctx, Ui.Theme t, int colW) {
        int y = height - 20;
        Ui.fill(ctx, 0, y, width, 20, 0xE60B0D12);
        Ui.outline(ctx, 0, y, width, 1, Ui.withAlpha(t.accent, 0.35f));

        int on = 0;
        for (Module m : Module.all()) if (m.enabled) on++;

        int ty = y + 6;
        int x = 8;
        int limit = width - 8;

        x = drawFooterItem(ctx, x, ty, limit, on + " VON " + Module.all().size() + " MODULEN AKTIV", t.dim);
        x = drawFooterItem(ctx, x, ty, limit, "PROFIL: " + Ui.up(ConfigStore.activeProfile()), t.dim);

        // Die Tastenkuerzel erst zeichnen, wenn sie noch frei Platz haben
        String h = "F6 HUD  ·  F7 CHAT  ·  R-SHIFT MENÜ";
        int hw = textRenderer.getWidth(h);
        if (hw <= limit - x - 6) {
            Ui.text(ctx, textRenderer, h, limit - hw, ty, Ui.withAlpha(t.dim, 0.8f));
        }
    }

    /**
     * Zeichnet einen Fusszeilentext ab {@code x} und gibt die x-Position danach
     * zurueck. Passt der Text nicht mehr bis {@code limit}, wird er
     * weggelassen und x bleibt stehen - so kann nichts ueberlappen.
     */
    private int drawFooterItem(DrawContext ctx, int x, int ty, int limit, String s, int color) {
        int w = textRenderer.getWidth(s);
        if (x + w > limit) return x;
        Ui.text(ctx, textRenderer, s, x, ty, color);
        return x + w + 10;
    }

    private static String iconStyle() {
        Module m = Module.get("icon");
        return m == null ? "LINE" : m.choice("style", "LINE");
    }

    private static String version() {
        String v = SaturnMenuScreen.class.getPackage().getImplementationVersion();
        return v == null ? "1.0.0" : v;
    }

    // ------------------------------------------------------------------ Klicks

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (backClicked(mx, my)) {
            goBack();
            return true;
        }

        // Suche
        if (Ui.inside(mx, my, searchHit[0], searchHit[1], searchHit[2], searchHit[3])) {
            searchOpen = !searchOpen;
            if (!searchOpen) {
                search = "";
                filter();
            }
            return true;
        }
        if (searchOpen) {
            // Klick in die Suchzeile fängt den Fokus
            if (my < 52) return true;
        }

        // Tabs
        int x = 8;
        for (String c : cats) {
            int w = textRenderer.getWidth(Ui.up(c)) + 18;
            if (Ui.inside(mx, my, x, 32, w, 16)) {
                category = c;
                scroll = 0;
                filter();
                return true;
            }
            x += w + 4;
        }

        // Karten
        int cols = columns();
        int cw = (width - 16 - GAP * (cols - 1)) / cols;
        int y = gridTop - (int) scroll;
        for (int i = 0; i < shown.size(); i++) {
            Module m = shown.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = 8 + col * (cw + GAP);
            int cy = y + row * (CARD_H + GAP);
            if (!Ui.inside(mx, my, cx, cy, cw, CARD_H)) continue;

            // Schalter oben rechts öffnet die Einstellungen
            int sw = 34, sh = 14;
            int sx = cx + cw - sw - 7, sy = cy + CARD_H - sh - 7;
            boolean onSettings = Ui.inside(mx, my, sx, sy, sw, sh);
            if (onSettings && m.hasMenu) {
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
        if (searchOpen) return true;
        scroll = applyScroll(scroll, contentHeight(), gridBottom - gridTop, vAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchOpen) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searchOpen = false;
                filter();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                filter();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchOpen) {
            if (chr >= 32 && chr < 127) {
                search = search + chr;
                filter();
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        goBack();
    }
}