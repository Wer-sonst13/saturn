package gg.nolimite;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Zeichen-Helfer und Widget-Bausteine für das NoLimite-Menü. */
public final class Ui {

    // ---------------- Themes ----------------

    public static final class Theme {
        public final int accent, bg, card, cardHover, border, borderHover, text, dim, good, bad, header;

        Theme(int accent, int bg, int card, int cardHover, int border, int borderHover,
              int text, int dim, int good, int bad, int header) {
            this.accent = accent;
            this.bg = bg;
            this.card = card;
            this.cardHover = cardHover;
            this.border = border;
            this.borderHover = borderHover;
            this.text = text;
            this.dim = dim;
            this.good = good;
            this.bad = bad;
            this.header = header;
        }
    }

    private static final java.util.Map<String, Theme> THEMES = new java.util.LinkedHashMap<>();

    static {
        THEMES.put("BLUE", new Theme(0xFF3D7BFF, 0xC00A0C12, 0xB0141820, 0xC01B2431, 0x552B3646, 0xAA3D7BFF, 0xFFE8EDF5, 0xFF8A94A6, 0xFF3ECF8E, 0xFFFF5C5C, 0xFF6E7C93));
        THEMES.put("CYAN", new Theme(0xFF17C8D6, 0xC0060F12, 0xB0121B20, 0xC0192831, 0x55263440, 0xAA17C8D6, 0xFFE6F7F8, 0xFF7FA0A6, 0xFF3ECF8E, 0xFFFF5C5C, 0xFF5C8E94));
        THEMES.put("PURPLE", new Theme(0xFF8A5CFF, 0xC00D0914, 0xB0151122, 0xC0201A33, 0x55303C56, 0xAA8A5CFF, 0xFFEFEAFD, 0xFF9086B0, 0xFF3ECF8E, 0xFFFF5C5C, 0xFF7B6BA8));
        THEMES.put("GREEN", new Theme(0xFF2FBF71, 0xC006120C, 0xB0112018, 0xC0172F22, 0x55243E30, 0xAA2FBF71, 0xFFE8F6EE, 0xFF7FA392, 0xFF3ECF8E, 0xFFFF5C5C, 0xFF5E9676));
        THEMES.put("ORANGE", new Theme(0xFFFF8A3D, 0xC00F0B06, 0xB01F1610, 0xC02E1F13, 0x553E2E1E, 0xFFFF8A3D, 0xFFFDF0E6, 0xFFAA8F79, 0xFF3ECF8E, 0xFFFF5C5C, 0xFFB07B4C));
        THEMES.put("RED", new Theme(0xFFE24B4B, 0xC00C0707, 0xB01C1212, 0xC02A1919, 0x553A2424, 0xAAE24B4B, 0xFFFCEBEB, 0xFFA88383, 0xFF3ECF8E, 0xFFFF5C5C, 0xFFA85656));
        THEMES.put("LIGHT", new Theme(0xFF2C6BE0, 0xF2EFF3F7, 0xFAFFFFFF, 0xFFF0F4FA, 0x666C7A8C, 0xAA2C6BE0, 0xFF1B2230, 0xFF6A7686, 0xFF1E9E63, 0xFFD33B3B, 0xFF6A7686));
        THEMES.put("DARK", new Theme(0xFF00E5FF, 0xD0000000, 0xB00B0B0B, 0xB0151515, 0x55333333, 0xAA00E5FF, 0xFFEDEDED, 0xFF8E8E8E, 0xFF3ECF8E, 0xFFFF5C5C, 0xFF8E8E8E));
    }

    public static Theme theme() {
        Module m = Module.get("theme");
        String name = m == null ? "BLUE" : m.choice("name", "BLUE");
        Theme t = THEMES.get(name.toUpperCase(Locale.ROOT));
        return t == null ? THEMES.get("BLUE") : t;
    }

    public static Theme theme(String name) {
        return THEMES.getOrDefault(name.toUpperCase(Locale.ROOT), THEMES.get("BLUE"));
    }

    // ---------------- Grundzeichnen ----------------

    public static float scale() {
        Module m = Module.get("guiscale");
        return m == null ? 1.0f : (float) m.num("scale", 1.0);
    }

    public static void fill(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + h, color);
    }

    /** Panel mit Rahmen - die Basis jedes Fensters. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, Theme t) {
        fill(ctx, x, y, w, h, t.bg);
        outline(ctx, x, y, w, h, t.border);
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.drawBorder(x, y, w, h, color);
    }

    /** Karte wie im Menü: dezenter Rahmen, hover leuchtet in Akzentfarbe. */
    public static void card(DrawContext ctx, int x, int y, int w, int h, Theme t, boolean hover, boolean active) {
        fill(ctx, x, y, w, h, active ? withAlpha(t.accent, 0.16f) : (hover ? t.cardHover : t.card));
        outline(ctx, x, y, w, h, hover || active ? withAlpha(t.accent, 0.85f) : t.border);
        if (hover || active) fill(ctx, x, y, 2, h, t.accent);
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
        ctx.drawText(tr, s, x, y, color, false);
    }

    public static void textShadow(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
        ctx.drawText(tr, s, x, y, color, true);
    }

    public static void textCentered(DrawContext ctx, TextRenderer tr, String s, int cx, int y, int color) {
        ctx.drawCenteredTextWithShadow(tr, s, cx, y, color);
    }

    public static int width(TextRenderer tr, String s) {
        return tr.getWidth(s);
    }

    /** Alles in Grossbuchstaben, damit es zum Pixel-Look passt. */
    public static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    public static int withAlpha(int rgb, float a) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return (rgb & 0xFFFFFF) | (alpha << 24);
    }

    public static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * f);
        int g = Math.round(ag + (bg - ag) * f);
        int bl = Math.round(ab + (bb - ab) * f);
        int al = Math.round(aa + (ba - aa) * f);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    // ---------------- Text ----------------

    /** Bricht einen Text auf die Breite um - für die Beschreibungen der Karten. */
    public static List<String> wrap(TextRenderer tr, String s, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String paragraph : s.split(" ")) {
            if (paragraph.isEmpty()) continue;
            String line = out.isEmpty() ? paragraph : out.get(out.size() - 1) + " " + paragraph;
            if (tr.getWidth(line) <= maxWidth) {
                if (out.isEmpty()) out.add(paragraph);
                else out.set(out.size() - 1, line);
                continue;
            }
            if (!out.isEmpty()) out.add(line.length() > maxWidth ? tr.trimToWidth(line, maxWidth) : line);
            if (maxLines > 0 && out.size() >= maxLines) break;
            out.add(tr.trimToWidth(paragraph, maxWidth));
            if (maxLines > 0 && out.size() >= maxLines) break;
        }
        if (maxLines > 0) {
            while (out.size() > maxLines) out.remove(out.size() - 1);
            if (!out.isEmpty()) out.set(out.size() - 1, tr.trimToWidth(out.get(out.size() - 1) + " ...", maxWidth));
        }
        return out;
    }

    // ---------------- Widgets ----------------

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** AN/AUS-Schalter wie im NoLimite-Menü. */
    public static void toggle(DrawContext ctx, int x, int y, int w, int h, boolean on, Theme t, boolean hover) {
        fill(ctx, x, y, w, h, on ? withAlpha(t.accent, 0.30f) : withAlpha(t.text, 0.08f));
        outline(ctx, x, y, w, h, on ? t.accent : withAlpha(t.text, 0.22f));
        if (hover) outline(ctx, x - 1, y - 1, w + 2, h + 2, withAlpha(t.accent, 0.45f));
        int bw = w / 3, bh = h - 6;
        int bx = on ? x + w - bw - 3 : x + 3;
        fill(ctx, bx, y + 3, bw, bh, on ? t.accent : withAlpha(t.text, 0.35f));
    }

    public static void checkbox(DrawContext ctx, int x, int y, int size, boolean on, Theme t, boolean hover) {
        fill(ctx, x, y, size, size, on ? t.accent : withAlpha(t.text, 0.08f));
        outline(ctx, x, y, size, size, on ? t.accent : withAlpha(t.text, 0.30f));
        if (hover) outline(ctx, x - 1, y - 1, size + 2, size + 2, withAlpha(t.accent, 0.5f));
        if (on) {
            int c = size / 2;
            fill(ctx, x + c - 3, y + c, 2, 2, 0xFFFFFFFF);
            fill(ctx, x + c - 1, y + c - 2, 2, 4, 0xFFFFFFFF);
            fill(ctx, x + c + 1, y + c - 4, 2, 6, 0xFFFFFFFF);
        }
    }

    /** Beschriftung links, Schalter rechts - die Zeile der Modulkarten. */
    public static void settingRow(DrawContext ctx, TextRenderer tr, int x, int y, int w, String label,
                                  boolean on, Theme t, boolean hover) {
        text(ctx, tr, up(label), x, y + 3, t.text);
        toggle(ctx, x + w - 46, y, 46, 14, on, t, hover);
    }

    /** Slider mit Zahlenfeld, exakt wie die Scoreboard-Seite. */
    public static void slider(DrawContext ctx, TextRenderer tr, int x, int y, int w, String label,
                              double value, double min, double max, double step, Theme t, boolean hover) {
        text(ctx, tr, up(label), x, y, t.text);
        int lineY = y + 14;
        int sliderX = x + Math.max(90, Math.round(w * 0.42f));
        int sliderW = w - sliderX - 4;
        // Zahlenfeld
        int boxW = 56;
        fill(ctx, sliderX - boxW - 6, y - 3, boxW, 14, withAlpha(t.text, 0.07f));
        outline(ctx, sliderX - boxW - 6, y - 3, boxW, 14, withAlpha(t.text, 0.18f));
        String v = step >= 1 ? String.valueOf((int) Math.round(value)) : trim(value);
        text(ctx, tr, v, sliderX - boxW - 6 + (boxW - tr.getWidth(v)) / 2, y, hover ? t.accent : t.text);
        // Bahn
        fill(ctx, sliderX, lineY - 1, sliderW, 3, withAlpha(t.text, 0.16f));
        float f = max <= min ? 0f : (float) ((value - min) / (max - min));
        fill(ctx, sliderX, lineY - 1, Math.max(2, Math.round(sliderW * f)), 3, t.accent);
        // Griff
        int knob = 6;
        int kx = sliderX + Math.round(sliderW * f) - knob / 2;
        fill(ctx, kx, lineY - knob / 2, knob, knob, hover ? t.accent : withAlpha(t.text, 0.8f));
    }

    /** Knopf mit Text (z. B. ON / RESET / Done). */
    public static void button(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                              String label, Theme t, boolean hover, boolean active) {
        fill(ctx, x, y, w, h, active ? t.accent : (hover ? withAlpha(t.accent, 0.22f) : withAlpha(t.text, 0.07f)));
        outline(ctx, x, y, w, h, active ? t.accent : (hover ? withAlpha(t.accent, 0.7f) : withAlpha(t.text, 0.20f)));
        String s = up(label);
        textCentered(ctx, tr, s, x + w / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : t.text);
    }

    /** Aufklapp-Menü. Wird nur gerendert; die Logik liegt im Screen. */
    public static void dropdown(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                                String value, List<String> options, boolean open, Theme t, boolean hover) {
        text(ctx, tr, up(value), x, y + (h - 8) / 2, hover ? t.accent : t.text);
        int ax = x + w - 12;
        fill(ctx, ax, y + h / 2 - 2, 7, 2, hover ? t.accent : t.dim);
        fill(ctx, ax + 2, y + h / 2, 3, 2, hover ? t.accent : t.dim);
        fill(ctx, ax + 1, y + h / 2 - 1, 5, 4, hover ? t.accent : t.dim);
        outline(ctx, x, y, w, h, open ? t.accent : withAlpha(t.text, 0.25f));
        fill(ctx, x, y, w, h, hover ? withAlpha(t.accent, 0.10f) : withAlpha(t.text, 0.05f));

        if (!open) return;
        int oy = y + h + 1;
        fill(ctx, x, oy, w, options.size() * h + 2, t.bg);
        for (int i = 0; i < options.size(); i++) {
            int iy = oy + 1 + i * h;
            boolean sel = options.get(i).equalsIgnoreCase(value);
            if (sel) fill(ctx, x, iy, w, h, withAlpha(t.accent, 0.25f));
            text(ctx, tr, up(options.get(i)), x + 6, iy + (h - 8) / 2, sel ? t.accent : t.text);
        }
        outline(ctx, x, oy, w, options.size() * h + 2, withAlpha(t.accent, 0.7f));
    }

    /** Scrollbar - nur zeichnen wenn nötig. */
    public static void scrollbar(DrawContext ctx, int x, int y, int h, int contentH, int viewH,
                                 double scroll, Theme t) {
        if (contentH <= viewH) return;
        int barH = Math.max(16, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int pos = maxScroll <= 0 ? 0 : (int) Math.round((scroll / maxScroll) * (viewH - barH));
        fill(ctx, x, y, 3, h, withAlpha(t.text, 0.08f));
        fill(ctx, x, y + pos, 3, barH, withAlpha(t.accent, 0.85f));
    }

    public static void scrollbarHit(int x, int y, int h, double scroll, int contentH, int viewH,
                                    double amount, Theme t) {
        // nichts - siehe Screen
    }

    public static List<String> options(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            String v = s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    public static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static void vGradient(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    public static String[] split(String s, String sep) {
        return s.split(sep);
    }

    public static List<String> list(String... s) {
        return new ArrayList<>(Arrays.asList(s));
    }
}