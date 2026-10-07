package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Der Saturn-Hub: die einzige Stelle, die R-Shift oeffnet.
 *
 * Aufbau nach dem Vorbild aus dem Spiel: Logo mittig, ein grosser Knopf
 * "MOD MENU" und darunter eine Reihe Symbolknoepfe fuer die anderen Seiten.
 *
 * Der Hub kann sich selbst in den Editor verwandeln (F6 oder der
 * Symbol-Knopf). Dann liegen alle HUD-Elemente als Rahmen auf dem Bild und
 * lassen sich ziehen - ohne den Hub verlassen zu muessen.
 */
public class HubScreen extends SaturnScreen {

    /** Symbolknoepfe unter dem Hauptknopf. */
    private static final String[] KNOPF_ICONS = {"grid", "board", "chat", "motion"};
    private static final String[] KNOPF_TEXT = {"HUD-Editor", "Scoreboard", "Chat-Utils", "HUD"};
    private static final int KNOPF_G = 12;   // Abstand zwischen den Knoepfen
    private static final int KNOPF = 28;    // Kantenlaenge eines Knopfes

    private boolean editor;

    // Ziehen
    private String zieht;
    private int greifDX, greifDY;

    public HubScreen(Screen parent) {
        super("Saturn Hub", parent);
    }

    // ------------------------------------------------------------------ Layout

    /**
 * Oberkante des mittigen Blocks.
 *
 * Der Block wird von oben nach unten festgestapelt: Logo, Name, Knopf,
 * Symbolknoepfe. Die Abstaende stehen hier an einer Stelle, damit nichts
 * ineinander laeuft - vorher stand der Name bei +42 und der Knopf bei +44,
 * also genau uebereinander.
 */
    private static final int LOGO_H = 52;   // Hoehe des Logos
    private static final int LUECKE_NACH_LOGO = 8;
    private static final int LUECKE_NACH_NAME = 14;
    private static final int KNOPF_H = 28;
    private static final int LUECKE_KNOPF_ZU_SYMBOL = 12;

    /** Oberkante des Logo. */
    private int blockY() {
        // Gesamtblock: Logo + Name + Knopf + Symbolreihe, mittig ausgerichtet.
        int gesamt = LOGO_H + LUECKE_NACH_LOGO + 9 + LUECKE_NACH_NAME
                + KNOPF_H + LUECKE_KNOPF_ZU_SYMBOL + KNOPF;
        return Math.max(10, height / 2 - gesamt / 2);
    }

    /** Wo der "MOD MENU"-Knopf liegt: {x, y, breite, hoehe}. */
    private int[] modMenu() {
        int w = Math.min(280, width - 60);
        int y = blockY() + LOGO_H + LUECKE_NACH_LOGO + 9 + LUECKE_NACH_NAME;
        return new int[]{width / 2 - w / 2, y, w, KNOPF_H};
    }

    /** Position des i-ten Symbolknopfes. */
    private int[] knopf(int i) {
        int anzahl = KNOPF_ICONS.length;
        int gesamt = anzahl * KNOPF + (anzahl - 1) * KNOPF_G;
        int startX = width / 2 - gesamt / 2;
        int[] mm = modMenu();
        int y = mm[1] + KNOPF_H + LUECKE_KNOPF_ZU_SYMBOL;
        return new int[]{startX + i * (KNOPF + KNOPF_G), y, KNOPF, KNOPF};
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        cacheMouse(mx, my);
        MinecraftClient mc = MinecraftClient.getInstance();
        Ui.Theme t = Ui.theme();

        // Spiel bleibt sichtbar, nur abgedunkelt - wie beim Hub im Spiel.
        ctx.fill(0, 0, width, height, 0x99000000);
        int step = 34;
        for (int y = 0; y < height; y += step) ctx.fill(0, y, width, y + 1, 0x0AFFFFFF);
        for (int x = 0; x < width; x += step) ctx.fill(x, 0, x + 1, height, 0x0AFFFFFF);

        if (editor) {
            zeichneEditor(ctx, mc, t, mx, my);
        } else {
            zeichneHub(ctx, t, mx, my);
        }

        super.render(ctx, mx, my, delta);
    }

    /** Der eigentliche Hub: Logo, Hauptknopf, Symbolknoepfe - alles mittig. */
    private void zeichneHub(DrawContext ctx, Ui.Theme t, int mx, int my) {
        int mitte = width / 2;

        // Logo: die echte Saturn-Grafik als Textur.
        //
        // Vorher wurden zwei Pixel-Bitmaps uebereinandergelegt ("ring" und
        // "planet"); das ergab einen grauen Klecks statt einem Logo.
        int logoH = LOGO_H;
        int logoW = logoH;   // quadratisch
        int lx = mitte - logoW / 2, ly = blockY();
        // Heller Rahmen: die Textur ist fast schwarz (gemessen RGB 0,0,0
        // bis 25,24,20) und verschwindet sonst auf dem dunklen Grund.
        ctx.fill(lx - 2, ly - 2, lx + logoW + 2, ly + logoH + 2, Ui.withAlpha(t.accent, 0.55f));
        ctx.drawTexture(RenderLayer::getGuiTextured, Logo.LOGO, lx, ly,
                0.0f, 0.0f, logoW, logoH, logoW, logoH);

        // Name mittig darunter - mit demselben Abstand wie beim Knopf,
        // damit er nicht darauf liegt.
        String titel = Ui.up("SATURN CLIENT");
        Ui.textCentered(ctx, textRenderer, titel, mitte,
                blockY() + LOGO_H + LUECKE_NACH_LOGO, t.text);

        // MOD MENU
        //
        // Ui.button gibt nichts zurueck - die Klick-Erkennung macht
        // mouseClicked ueber Ui.inside. Hier wird nur gezeichnet.
        int[] mm = modMenu();
        Ui.button(ctx, textRenderer, mm[0], mm[1], mm[2], mm[3], "MOD MENU", t,
                hovering(mm[0], mm[1], mm[2], mm[3]), true);

        // Symbolknoepfe
        for (int i = 0; i < KNOPF_ICONS.length; i++) {
            int[] k = knopf(i);
            boolean hover = hovering(k[0], k[1], k[2], k[3]);
            Ui.card(ctx, k[0], k[1], k[2], k[3], t, hover, false);
            Icons.draw(ctx, KNOPF_ICONS[i], k[0] + 4, k[1] + 4, 2,
                    hover ? t.accent : Ui.withAlpha(t.text, 0.8f), "LINE");
            if (hover) {
                Ui.textCentered(ctx, textRenderer, KNOPF_TEXT[i], mitte, k[1] + k[3] + 5, t.dim);
            }
        }

        // Fusszeile
        Ui.textCentered(ctx, textRenderer, "F6 = HUD-EDITOR   ·   ESC = ZURÜCK",
                mitte, height - 14, Ui.withAlpha(t.dim, 0.7f));
    }

    /** Editor-Modus: alle HUD-Elemente als Rahmen, ziehbar. */
    private void zeichneEditor(DrawContext ctx, MinecraftClient mc, Ui.Theme t, int mx, int my) {
        int mitte = width / 2;
        // Hinweisleiste
        Ui.textCentered(ctx, textRenderer, "HUD-EDITOR  ·  ZIEHEN = VERSCHIEBEN  ·  MAUSRAD = GRÖSSE",
                mitte, 8, t.accent);

        // Das Logo fehlte hier ganz. Mit Rahmen, sonst ist es auf dem
        // dunklen Grund unsichtbar - wie im Hub-Menue vorher.
        int lg = 16;
        int lgX = mitte - lg / 2, lgY = 19;
        ctx.fill(lgX - 1, lgY - 1, lgX + lg + 1, lgY + lg + 1, Ui.withAlpha(t.accent, 0.55f));
        ctx.drawTexture(RenderLayer::getGuiTextured, Logo.LOGO, lgX, lgY,
                0.0f, 0.0f, lg, lg, lg, lg);

        for (Module m : Modules.hudModules()) {
            if (!m.enabled) continue;
            int[] b = HudRenderer.bounds(m.id, mc);
            if (b[2] <= 0 || b[3] <= 0) continue;
            boolean hover = hit(mx, my, b);
            boolean sel = m.id.equals(zieht);
            int col = sel ? t.accent : (hover ? 0xFFFFFFFF : Ui.withAlpha(t.text, 0.5f));
            ctx.fill(b[0] - 2, b[1] - 2, b[0] + b[2] + 2, b[1] + b[3] + 2, 0x66000000);
            ctx.drawBorder(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, col);
            ctx.drawText(textRenderer, Ui.up(m.name), b[0] - 2, b[1] - 11, col, true);
        }

        if (zieht != null) {
            Module m = Module.get(zieht);
            if (m != null) {
                Ui.textCentered(ctx, textRenderer,
                        m.name + "   " + (int) m.x + " / " + (int) m.y,
                        mitte, 20, Ui.withAlpha(t.text, 0.9f));
            }
        }

        Ui.textCentered(ctx, textRenderer, "F6 = FERTIG", mitte, height - 14, Ui.withAlpha(t.dim, 0.7f));
    }

    // ------------------------------------------------------------------ Maus

    private static boolean hit(double mx, double my, int[] b) {
        return mx >= b[0] - 3 && mx <= b[0] + b[2] + 3 && my >= b[1] - 3 && my <= b[1] + b[3] + 3;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (editor) {
            zieht = treffer(MinecraftClient.getInstance(), mx, my);
            if (zieht != null) {
                // Greifpunkt als Abstand zur linken oberen Ecke des Kastens.
                // Bei m.x greifen waere es beim Scoreboard falsch, denn dessen
                // x ist ein Versatz vom rechten Rand.
                int[] b = HudRenderer.bounds(zieht, MinecraftClient.getInstance());
                greifDX = (int) Math.round(mx - b[0]);
                greifDY = (int) Math.round(my - b[1]);
            }
            return true;
        }

        int[] mm = modMenu();
        if (Ui.inside(mx, my, mm[0], mm[1], mm[2], mm[3])) {
            client.setScreen(new SaturnMenuScreen(this));
            return true;
        }
        for (int i = 0; i < KNOPF_ICONS.length; i++) {
            int[] k = knopf(i);
            if (!Ui.inside(mx, my, k[0], k[1], k[2], k[3])) continue;
            knopfGedrueckt(i);
            return true;
        }
        return true;
    }

    private void knopfGedrueckt(int i) {
        switch (i) {
            case 0 -> setEditor(true);
                        // Kein eigener Scoreboard-Bildschirm mehr. Der fragte elf
            // Einstellungen ab, von denen die meisten nicht mehr existieren -
            // seit Vanilla zeichnet, sind es nur noch Groesse und Position.
            // Die generische Einstellungsseite zeigt automatisch, was da ist.
            case 1 -> client.setScreen(new ModuleSettingsScreen(
                    Module.get("scoreboard"), this));
            case 2 -> client.setScreen(new ChatUtilsScreen(this));
            default -> client.setScreen(null);   // HUD aus: zurueck ins Spiel
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (zieht != null) {
            Module m = Module.get(zieht);
            if (m != null) {
                MinecraftClient mc = MinecraftClient.getInstance();
                // Groesse neu bestimmen: haengt an m.scale, das sich waehrend
                // des Ziehens per Mausrad aendern kann.
                int[] b = HudRenderer.bounds(zieht, mc);
                double links = mx - greifDX;
                double oben = my - greifDY;
                if (zieht.equals("scoreboard")) {
                    // haengt am rechten Rand und in der Mitte
                    m.x = Math.round(links + b[2] - width);
                    m.y = Math.round(oben + b[3] / 2.0 - height / 2.0);
                } else {
                    m.x = Math.round(links);
                    m.y = Math.round(oben);
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (zieht != null) {
            zieht = null;
            ConfigStore.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (editor) {
            MinecraftClient mc = MinecraftClient.getInstance();
            String id = zieht != null ? zieht : treffer(mc, mx, my);
            if (id != null) {
                Module m = Module.get(id);
                m.scale = Math.max(0.3, Math.min(3.0, m.scale + (float) vAmount * 0.08));
                return true;
            }
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    /** Kleinster Kasten unter dem Cursor - sonst verdeckt ein grosses Element ein kleines. */
    private String treffer(MinecraftClient mc, double mx, double my) {
        String treffer = null;
        int kleinste = Integer.MAX_VALUE;
        List<Module> liste = Modules.hudModules();
        for (int i = liste.size() - 1; i >= 0; i--) {
            Module m = liste.get(i);
            if (!m.enabled) continue;
            int[] b = HudRenderer.bounds(m.id, mc);
            if (b[2] <= 0 || b[3] <= 0) continue;
            if (!hit(mx, my, b)) continue;
            int f = b[2] * b[3];
            if (f < kleinste) {
                kleinste = f;
                treffer = m.id;
            }
        }
        return treffer;
    }

    // ------------------------------------------------------------------ Tasten

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // F6 schaltet den Editor direkt im Hub um - man muss ihn nicht verlassen.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F6) {
            setEditor(!editor);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setEditor(boolean an) {
        editor = an;
        zieht = null;
    }

    @Override
    public void close() {
        ConfigStore.save();
        client.setScreen(parent);
    }
}

/** Die Saturn-Grafik aus den Mod-Ressourcen. */
final class Logo {
    static final Identifier LOGO = Identifier.of("saturn", "logo");
    private Logo() {}
}
