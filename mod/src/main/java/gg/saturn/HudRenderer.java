package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
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
        for (Module m : Modules.hudModules()) {
            if (!m.enabled || m.id.equals("scoreboard")) continue;
            draw(ctx, mc, m);
        }
        if (mc.currentScreen == null) if (module("nametags")) NametagRenderer.render(ctx);
        Effects.renderOverlay(ctx, mc);
    }

    private static boolean module(String id) {
        Module m = Module.get(id);
        return m != null && m.enabled;
    }

    public static void draw(DrawContext ctx, MinecraftClient mc, Module m) {
        MatrixStack s = ctx.getMatrices();
        s.push();
        s.translate(m.x, m.y, 0);
        float sc = (float) Math.max(0.2, m.scale);
        s.scale(sc, sc, 1f);
        try {
            paint(ctx, mc, m);
        } catch (Exception ex) {
            // Ein kaputtes Modul darf das HUD nicht mitreißen.
            ex.printStackTrace();
        }
        s.pop();
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
            if (l.length() > 1 && l.charAt(1) == '~') {
                // "~c<hex>" = eigene Farbe, z. B. ~c55FF55
                ctx.drawText(mc.textRenderer, l.substring(2), 0, y, parseColor(l.substring(2)), true);
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
        int y = 0;
        for (ItemStack s : armorOf(mc.player)) {
            if (s.isEmpty()) {
                y += 12;
                continue;
            }
            ctx.drawItemWithoutEntity(s, 0, y);
            if (s.getMaxDamage() > 0) {
                int left = s.getMaxDamage() - s.getDamage();
                int color = left * 4 > s.getMaxDamage() ? 0xFF55FF55 : (left * 2 > s.getMaxDamage() ? 0xFFFFFF55 : 0xFFFF5555);
                ctx.fill(19, y, 21, y + 16, color);
                if (percent) ctx.drawText(mc.textRenderer, s.getMaxDamage() - s.getDamage() + "", 23, y + 4, color, true);
            }
            y += 12;
        }
        return y;
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
        int h = sz * 2 + gap * (m.flag("jump", true) ? 2 : 1);
        ctx.fill(-2, -2, w + 2, h + 2, 0x80000000);

        box(ctx, mc, sz + gap, 0, sz, Trackers.keystroke(forward));
        box(ctx, mc, 0, sz + gap, sz, Trackers.keystroke(left));
        box(ctx, mc, sz * 2 + gap * 2, sz + gap, sz, Trackers.keystroke(right));
        box(ctx, mc, sz + gap, sz * 2 + gap * 2, sz, Trackers.keystroke(back));
        if (m.flag("jump", true)) {
            box(ctx, mc, sz + gap, sz * 3 + gap * 3, sz, Trackers.keystroke(jump));
            return sz * 4 + gap * 4;
        }
        return sz * 3 + gap * 3;
    }

    private static void box(DrawContext ctx, MinecraftClient mc, int x, int y, int s, boolean down) {
        int c = down ? 0xFF55FF55 : 0xFF555555;
        ctx.fill(x, y, x + s, y + s, down ? 0xFF203020 : 0x80101010);
        ctx.drawBorder(x, y, s, s, down ? 0xFF80FF80 : 0xFF808080);
        if (down) ctx.fill(x + 2, y + 2, x + s - 2, y + s - 2, c);
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
            float sc = (float) m.num("scale", 1.0);
            int w = Math.round(160 * sc);
            int h = Math.round(70 * sc);
            int right = c.getWindow().getScaledWidth() + (int) m.x;
            int cy = c.getWindow().getScaledHeight() / 2 + (int) m.y;
            return new int[]{right - w, cy - h / 2, w, h};
        }
        List<String> lines = lines(m, mc);
        int textW = 0;
        for (String l : lines) textW = Math.max(textW, mc.textRenderer.getWidth(l.startsWith("~c") && l.length() > 3 ? l.substring(3) : l));
        int w = Math.max(8, textW);
        int h = Math.max(10, lines.size() * 10);
        return new int[]{(int) m.x, (int) m.y,
                (int) Math.round(w * m.scale), (int) Math.round(h * m.scale)};
    }
}