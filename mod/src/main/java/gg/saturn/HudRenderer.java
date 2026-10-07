package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import org.joml.Matrix3x2fStack;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet alle HUD-Elemente (FPS, Koordinaten, Armor, CPS, ...).
 * Position und Größe kommen aus dem Modul, das Scoreboard zeichnet der Mixin.
 */
public final class HudRenderer {

    private HudRenderer() {}

    // ---------------- Platzhalter ----------------

    /** Ersetzt {fps}, {x}, {ping}, {cps}, {count:diamond} ... in einer Formatvorlage. */
    public static String format(String tpl, MinecraftClient mc) {
        if (tpl == null || tpl.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < tpl.length()) {
            int open = tpl.indexOf('{', i);
            if (open < 0) {
                out.append(tpl, i, tpl.length());
                break;
            }
            int close = tpl.indexOf('}', open);
            if (close < 0) {
                out.append(tpl, i, tpl.length());
                break;
            }
            out.append(tpl, i, open);
            String key = tpl.substring(open + 1, close);
            out.append(value(key, mc));
            i = close + 1;
        }
        return out.toString();
    }

    private static String value(String key, MinecraftClient mc) {
        if (mc == null) return "0";
        if (key.startsWith("count:")) return String.valueOf(Trackers.countItem(mc, key.substring(6).trim()));
        switch (key.toLowerCase(Locale.ROOT)) {
            case "fps": return String.valueOf(mc.getCurrentFps());
            case "ping": {
                var entry = mc.getCurrentServerEntry();
                return entry == null ? "0" : String.valueOf(entry.ping);
            }
            case "x": return mc.player == null ? "0" : String.valueOf(Math.round(mc.player.getX()));
            case "y": return mc.player == null ? "0" : String.valueOf(Math.round(mc.player.getY()));
            case "z": return mc.player == null ? "0" : String.valueOf(Math.round(mc.player.getZ()));
            case "biome": return Trackers.biome(mc);
            case "dim": return Trackers.dimension(mc);
            case "chunk": return mc.player == null ? "0" : String.valueOf(mc.player.getChunkPos().x);
            case "light": return String.valueOf(Trackers.blockLight(mc));
            case "skylight": return String.valueOf(Trackers.skyLight(mc));
            case "cps": return String.valueOf(Trackers.cps());
            case "aps": return String.valueOf(Trackers.aps());
            case "combo": return String.valueOf(Trackers.combo());
            case "speed": return String.format(Locale.ROOT, "%.1f", Trackers.speed(mc));
            case "block": return Trackers.lookedBlock(mc);
            case "session": return SaturnClient.formatDuration(Trackers.sessionTime());
            case "server": {
                var e = mc.getCurrentServerEntry();
                return e == null ? "Singleplayer" : e.address;
            }
            case "time": return Trackers.clock(true, false);
            default: return "{" + key + "}";
        }
    }

    // ---------------- Zeichnen ----------------

    public static void renderAll(DrawContext ctx, MinecraftClient mc) {
        if (mc.options.hudHidden || SaturnClient.menuOpen()) return;

        // Solange ein Saturn-Fenster offen ist, nicht mitzeichnen. Vorher
        // lagen FPS, Ping und Coords im Hub und auf dem Mod-Menue oben drauf
        // und die Schrift lief ineinander.
        if (mc.currentScreen instanceof SaturnScreen) return;
        for (Module m : Modules.hudModulesCached()) {
            if (!m.enabled || m.id.equals("scoreboard")) continue;
            Profiler.start(m.id);
            draw(ctx, mc, m);
            Profiler.stop(m.id);
        }
        Profiler.start("nametags");
        if (mc.currentScreen == null) if (module("nametags")) NametagRenderer.render(ctx);
        Profiler.stop("nametags");
        Profiler.start("overlay");
        Effects.renderOverlay(ctx, mc);
        Profiler.stop("overlay");
        Profiler.frameEnde(mc);
        Profiler.zeichnen(ctx, mc);
    }

    private static boolean module(String id) {
        Module m = Module.get(id);
        return m != null && m.enabled;
    }

    public static void draw(DrawContext ctx, MinecraftClient mc, Module m) {
        Matrix3x2fStack s = ctx.getMatrices();
        s.pushMatrix();
        s.translate((float) m.x, (float) m.y);
        float sc = (float) Math.max(0.2, m.scale);
        s.scale(sc, sc);
        try {
            paint(ctx, mc, m);
        } catch (Exception ex) {
            // Ein kaputtes Modul darf das HUD nicht mitreißen.
            ex.printStackTrace();
        }
        s.popMatrix();
    }

    /** Inhalt eines Moduls - gibt die verbrauchte Höhe zurück. */
    public static int paint(DrawContext ctx, MinecraftClient mc, Module m) {
        switch (m.id) {
            case "armorstatus": return armorBars(ctx, mc, m);
            case "armorhud": return armorIcons(ctx, mc, m);
            case "potionstatus": return potionList(ctx, mc, m);
            case "keystrokes": return keyBoxes(ctx, mc, m);
            case "itemcounter": return counterList(ctx, mc, m);
            default: return textBlock(ctx, mc, m);
        }
    }

    private static int textBlock(DrawContext ctx, MinecraftClient mc, Module m) {
        List<String> lines = lines(m, mc);
        if (lines.isEmpty()) return 0;
        int color = m.color("color", 0xFFFFFFFF);
        int y = 0;
        boolean shadow = true;
        for (String l : lines) {
            if (l.isEmpty()) {
                y += 10;
                continue;
            }
            // "~c<hex>" = eigene Farbe, z. B. ~c55FF55
            //
            // Wichtig: nach "~c" suchen, nicht an Stelle 1 auf '~' pruefen.
            // An Stelle 1 steht bei "~c55FF55" das 'c', nie ein '~'. Die alte
            // Pruefung traf also nie zu - dann wurde der Code wörtlich mit
            // gezeichnet ("~c55FF55" stand im Bild), und bounds()(), das
            // sauber auf substring(3) schneidet, mass den Kasten viel zu klein.
            // Genau das sah nach kaputten Modulen aus.
            if (l.startsWith("~c") && l.length() > 3) {
                String hex = l.substring(3);
                ctx.drawText(mc.textRenderer, hex, 0, y, parseColor(hex), true);
            } else {
                ctx.drawText(mc.textRenderer, l, 0, y, color, shadow);
            }
            y += 10;
        }
        return y;
    }

    private static int parseColor(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (Exception ex) {
            return 0xFFFFFFFF;
        }
    }

    /** Textzeilen eines Moduls (Farbcodes möglich). */
    public static List<String> lines(Module m, MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        switch (m.id) {
            case "fps": out.add(format(m.str("format", "{fps} FPS"), mc)); break;
            case "coords": {
                out.add("XYZ: " + value("x", mc) + " " + value("y", mc) + " " + value("z", mc));
                if (m.flag("biome", true)) out.add("Biome: " + value("biome", mc));
                if (m.flag("chunk", false)) out.add("Chunk: " + value("chunk", mc));
                if (m.flag("block", true)) out.add("Block: " + value("block", mc));
                break;
            }
            case "lightlevel": {
                if (m.flag("blockLight", true)) out.add("Block: " + value("light", mc));
                if (m.flag("skyLight", false)) out.add("Sky: " + value("skylight", mc));
                break;
            }
            case "cps": out.add(m.flag("avg", true)
                    ? "CPS: " + Trackers.cps() + "  APS: " + Trackers.aps()
                    : "CPS: " + Trackers.cps()); break;
            case "combo": out.add("Combo: " + Trackers.combo()); break;
            case "clock": out.add(Trackers.clock(m.flag("seconds", true), m.flag("date", false))); break;
            case "speed": out.add(String.format(Locale.ROOT, "%.2f m/s", Trackers.speed(mc))); break;
            case "biome": {
                if (m.flag("dimension", true)) out.add(value("dim", mc));
                out.add(value("biome", mc));
                break;
            }
            case "serverip": out.add("IP: " + value("server", mc)); break;
            case "helditem": {
                ItemStack hand = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();
                if (hand.isEmpty()) break;
                if (m.flag("count", true)) out.add(hand.getCount() + "x " + hand.getName().getString());
                else if (m.flag("name", true)) out.add(hand.getName().getString());
                break;
            }
            default: {
                String tpl = m.setting("format") == null ? "" : m.str("format", "");
                if (!tpl.isEmpty()) out.add(format(tpl, mc));
            }
        }
        return out;
    }

    // ---------------- Spezial-Elemente ----------------

    /**
     * Die Ruestung des Spielers, von Fuss nach Kopf.
     *
     * Yarn hat zwischen den Versionen umgestellt: frueher lieferte
     * getArmorItems() die fertige Liste, ab 1.21.5 gibt es diese Methode
     * nicht mehr. Die einzelnen Slots gibt es dagegen in allen Versionen -
     * damit ist die Reihenfolge ueberall dieselbe.
     */
    private static List<ItemStack> armorOf(ClientPlayerEntity p) {
        List<ItemStack> out = new ArrayList<>();
        out.add(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
        out.add(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
        out.add(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
        out.add(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
        return out;
    }

    private static int armorBars(DrawContext ctx, MinecraftClient mc, Module m) {
        if (mc.player == null) return 0;
        boolean percent = m.flag("percent", true);
        int ok = m.color("okColor", 0xFF55FF55);
        int low = m.color("lowColor", 0xFFFF5555);
        List<ItemStack> gear = armorOf(mc.player);
        gear.add(mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.OFFHAND));

        int y = 0;
        for (ItemStack s : gear) {
            if (s.isEmpty()) {
                y += 11;
                continue;
            }
            ctx.drawItemWithoutEntity(s, 0, y);
            int max = Math.max(1, s.getMaxDamage());
            int left = max - s.getDamage();
            float f = Math.max(0f, Math.min(1f, (float) left / max));
            int color = mixColor(low, ok, f);
            int bw = 62;
            ctx.fill(20, y + 8, 20 + bw, y + 10, 0x90000000);
            ctx.fill(20, y + 8, 20 + Math.max(1, Math.round(bw * f)), y + 10, color);
            if (percent) {
                String t = Math.round(f * 100) + "%";
                ctx.drawText(mc.textRenderer, t, 22 + bw, y + 2, color, true);
            }
            y += 11;
        }
        return y;
    }

    private static int armorIcons(DrawContext ctx, MinecraftClient mc, Module m) {
        if (mc.player == null) return 0;
        boolean percent = m.flag("percent", false);
        float sc = (float) Math.max(0.5, Math.min(2.0, m.num("scale", 1.0)));

        // Ein Slot ist 16 Pixel hoch - immer, auch nach dem Skalieren, denn
        // die Skalierung passiert ja schon ueber die Matrix. Mit Abstand 12
        // lagen die Icons vorher uebereinander.
        final int SLOT = 16;

        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();
        ms.scale(sc, sc);

        int y = 0;
        for (ItemStack s : armorOf(mc.player)) {
            if (s.isEmpty()) {
                y += SLOT;
                continue;
            }
            ctx.drawItemWithoutEntity(s, 0, y);
            int max = s.getMaxDamage();
            if (max > 0) {
                int left = max - s.getDamage();
                // Farbe wie gehabt nach Fuellstand.
                int color = left * 4 > max ? 0xFF55FF55 : (left * 2 > max ? 0xFFFFFF55 : 0xFFFF5555);
                // Balken als echter Fuellstand: die Laenge waechst mit der
                // Resthaltbarkeit. Vorher stand hier ein 2 x 16 grosser
                // Vollrechteck - der sah wie ein fremder Streifen aus und
                // sagte nichts ueber den Zustand aus.
                int trackX = 18, trackW = 2, trackY = y + 1, trackH = 14;
                ctx.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x60000000);
                int fuell = Math.max(1, Math.round(trackH * (left / (float) max)));
                // Fuellstand von unten nach oben fuellen, wie in Minecraft ueblich.
                ctx.fill(trackX, trackY + trackH - fuell, trackX + trackW, trackY + trackH, color);
                if (percent) {
                    int pct = Math.round(left * 100f / max);
                    ctx.drawText(mc.textRenderer, pct + "%", trackX + trackW + 2, y + 4, color, true);
                }
            }
            y += SLOT;
        }
        ms.popMatrix();
        // Hoehe in unskalierten Pixeln zurueckgeben, damit der Kasten passt.
        return Math.round(y * sc);
    }

    private static int potionList(DrawContext ctx, MinecraftClient mc, Module m) {
        if (mc.player == null) return 0;
        boolean showTime = m.flag("time", true);
        boolean icons = m.flag("icons", false);
        int y = 0;
        for (StatusEffectInstance e : mc.player.getActiveStatusEffects().values()) {
            String name = shortName(e.getTranslationKey());
            String t = name + (showTime ? " " + SaturnClient.formatDuration(e.getDuration() * 50L) : "");
            if (icons) ctx.drawText(mc.textRenderer, t, 0, y, 0xFF9BE7FF, true);
            else ctx.drawText(mc.textRenderer, t, 0, y, 0xFF55FFFF, true);
            y += 10;
        }
        return y;
    }

    private static String shortName(String translationKey) {
        String s = translationKey;
        if (s.startsWith("effect.")) s = s.substring(7);
        s = s.replace('.', ' ');
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return sb.toString();
    }

    private static int keyBoxes(DrawContext ctx, MinecraftClient mc, Module m) {
        KeyBinding forward = SaturnClient.keyForward;
        KeyBinding left = SaturnClient.keyLeft;
        KeyBinding back = SaturnClient.keyBack;
        KeyBinding right = SaturnClient.keyRight;
        KeyBinding jump = SaturnClient.keyJump;

        // Die Vanilla-Tasten werden erst nach CLIENT_STARTED geholt. Sollte das
        // Modul doch einmal vorher gezeichnet werden, lieber nichts anzeigen
        // als mit einem Null-Fehler das ganze HUD zu verlieren.
        if (forward == null || left == null || back == null || right == null || jump == null) {
            return 0;
        }

        int sz = 18, gap = 2;
        int w = sz * 3 + gap * 2;
        boolean mitSprung = m.flag("jump", true);
        // Zwei Reihen (W ueber A/S/D) plus die Leertaste darunter.
        int h = mitSprung ? sz * 3 + gap * 3 : sz * 2 + gap * 2;
        ctx.fill(-2, -2, w + 2, h + 2, 0x80000000);

        // Klassische Anordnung: W oben, darunter A S D.
        //
        // Vorher stand in der mittleren Reihe A, W, D - W also zweimal, und S
        // eine Reihe zu tief. Sieht aus, als fehle eine Taste.
        box(ctx, mc, sz + gap, 0, sz, sz, Trackers.keystroke(forward));
        box(ctx, mc, 0, sz + gap, sz, sz, Trackers.keystroke(left));
        box(ctx, mc, sz + gap, sz + gap, sz, sz, Trackers.keystroke(back));
        box(ctx, mc, sz * 2 + gap * 2, sz + gap, sz, sz, Trackers.keystroke(right));
        if (mitSprung) {
            // Die Leertaste ist breit, nicht quadratisch - ein quadratischer
            // Kasten daneben wirkt wie eine fuenfte Taste.
            box(ctx, mc, 0, sz * 2 + gap * 2, w, sz, Trackers.keystroke(jump));
        }
        return h;
    }

    private static void box(DrawContext ctx, MinecraftClient mc, int x, int y, int b, int h, boolean down) {
        ctx.fill(x, y, x + b, y + h, down ? 0xFF203020 : 0x80101010);
        ctx.drawBorder(x, y, b, h, down ? 0xFF80FF80 : 0xFF808080);
        if (down) ctx.fill(x + 2, y + 2, x + b - 2, y + h - 2, 0xFF55FF55);
    }

    private static int counterList(DrawContext ctx, MinecraftClient mc, Module m) {
        boolean showEmpty = m.flag("showEmpty", false);
        int y = 0;
        for (String id : Ui.options(m.str("items", ""))) {
            int n = Trackers.countItem(mc, id);
            if (n == 0 && !showEmpty) continue;
            ctx.drawText(mc.textRenderer, n + "x " + pretty(id), 0, y, 0xFFFFFF55, true);
            y += 10;
        }
        return y;
    }

    public static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : id.toCharArray()) {
            if (c == '_' || c == ' ') cap = true;
            else {
                if (cap) sb.append(Character.toUpperCase(c));
                else sb.append(c);
                cap = false;
            }
        }
        return sb.toString();
    }

    private static int mixColor(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------------- Rechtecke für den HUD-Editor ----------------

    /** {x, y, breite, höhe} eines Elements. */
    public static int[] bounds(String id, MinecraftClient mc) {
        Module m = Module.get(id);
        if (m == null) return new int[]{0, 0, 0, 0};
        if (id.equals("scoreboard")) {
            MinecraftClient c = mc == null ? MinecraftClient.getInstance() : mc;
            // Jetzt zeichnet Vanilla selbst, also liegt das Feld am rechten
            // Rand und ist mittig. Groesse ist eine brauchbare Schaetzung -
            // genau gemessen werden kann sie hier nicht, weil der Text
            // serverabhaengig ist. Fuer den Greifkasten reicht das.
            float sc = (float) Math.max(0.3, m.scale);
            int breite = mc.getWindow().getScaledWidth();
            int hoehe = mc.getWindow().getScaledHeight();
            int w = Math.round(180 * sc);
            int h = Math.round(100 * sc);
            int links = breite - w + (int) m.x;
            int oben = hoehe / 2 - h / 2 + (int) m.y;
            return new int[]{links, oben, w, h};
        }
        List<String> lines = lines(m, mc);
        if (lines.isEmpty()) {
            // Kein Textmodul. Die Zeichner hier sind aber Icon- und
            // Balkenmodule, und die haben ueberhaupt keine Textzeilen.
            //
            // Vorher fielen sie auf 8 x 10 Pixel zurueck: der Kasten im Editor
            // war also winzig, lag meistens ausserhalb des sichtbaren Bereichs
            // und liess sich praktisch nicht greifen. Deshalb bekommen sie
            // hier ihre tatsaechliche Groesse.
            int[] rahmen = iconRahmen(id);
            return new int[]{(int) m.x, (int) m.y,
                    (int) Math.round(rahmen[0] * m.scale), (int) Math.round(rahmen[1] * m.scale)};
        }
        int textW = 0;
        for (String l : lines) textW = Math.max(textW, mc.textRenderer.getWidth(l.startsWith("~c") && l.length() > 3 ? l.substring(3) : l));
        int w = Math.max(8, textW);
        int h = Math.max(10, lines.size() * 10);
        return new int[]{(int) m.x, (int) m.y,
                (int) Math.round(w * m.scale), (int) Math.round(h * m.scale)};
    }

    /**
     * Aussenmasse der Icon- und Balkenmodule: { breite, hoehe } in Pixeln,
     * ohne Skalierung.
     *
     * Werden sie groesser, muss hier nachgezogen werden - sonst passt der
     * Rahmen im Editor nicht mehr auf das, was tatsaechlich gezeichnet wird.
     */
    private static int[] iconRahmen(String id) {
        switch (id) {
            case "armorhud": return new int[]{44, 64};      // 4 Slots + Prozent
            case "armorstatus": return new int[]{22, 44};   // 4 Fuellstaende
            case "keystrokes": return new int[]{54, 40};    // WASD + Maus
            case "potionstatus": return new int[]{24, 88};   // Liste
            case "itemcounter": return new int[]{60, 54};   // Liste
            case "helditem": return new int[]{24, 24};      // ein Icon
            case "splitchat": return new int[]{160, 120};   // Chatfenster
            default: return new int[]{80, 20};
        }
    }
}