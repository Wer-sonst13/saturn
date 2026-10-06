package gg.nolimite;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Die SCOREBOARD-Seite aus dem Menü.
 * Links die Karte "Scoreboard", darunter die Sektionen DISPLAY und SETTINGS
 * mit HUD-Skala, Hintergrund, Ecken, dynamischem Abstand, Breite, Höhe,
 * Zahlen, Schriftschatten und der Option, das Scoreboard ganz zu verstecken.
 */
public class ScoreboardScreen extends NoLimiteScreen {

    private Setting scale, background, bgColor, corners, cornerSize, dynamicPadding,
            setWidth, setHeight, showNumbers, fontShadow, hideScoreboard;

    private boolean draggingScale, draggingWidth, draggingHeight;
    private List<String> bgOptions;
    private boolean openBackground;
    private boolean editingWidth, editingHeight;

    public ScoreboardScreen(Screen parent) {
        super("Scoreboard", parent);
    }

    private Setting setting(String key) {
        Module m = Module.get("scoreboard");
        return m == null ? null : m.setting(key);
    }

    @Override
    protected void init() {
        super.init();
        scale = setting("scale");
        background = setting("background");
        bgColor = setting("bgColor");
        corners = setting("corners");
        cornerSize = setting("cornerSize");
        dynamicPadding = setting("dynamicPadding");
        setWidth = setting("width");
        setHeight = setting("height");
        showNumbers = setting("showNumbers");
        fontShadow = setting("fontShadow");
        hideScoreboard = setting("hideScoreboard");
        bgOptions = new ArrayList<>();
        if (background != null) bgOptions.addAll(background.choices);
    }

    // Geometrie ---------------------------------------------------------
    private int cardX, cardY, cardW, cardH;
    private int displayX, displayY, displayW;
    private int setX, setY, setW;

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        cacheMouse(mx, my);
        Ui.Theme t = Ui.theme();
        Module m = Module.get("scoreboard");

        drawBackdrop(ctx);
        drawHeader(ctx, "Scoreboard", m != null && m.enabled ? "AKTIV" : "INAKTIV", true);

        // Kopfzeile mit AN / RESET / Reload
        drawTopButtons(ctx, t);

        // ---- Karte links: Vorschau ----
        cardX = 10;
        cardY = 56;
        cardW = 150;
        cardH = 96;
        drawPreviewCard(ctx, t);

        // ---- Sektion DISPLAY ----
        displayX = cardX + cardW + 12;
        displayY = 56;
        displayW = width - displayX - 10;
        section(ctx, t, "DISPLAY", displayX, displayY, displayW, 232);
        Ui.text(ctx, textRenderer, "HUD SCALE", displayX + 8, displayY + 20, t.text);
        drawSlider(ctx, t, displayX + 8, displayY + 36, displayW - 16, scale, scaleHit());
        Ui.text(ctx, textRenderer, "BACKGROUND", displayX + 8, displayY + 58, t.text);
        drawDropdown(ctx, t, displayX + 8, displayY + 72, displayW - 16, background, bgOptions, openBackground);

        // Corners: Schalter + Größe
        Ui.settingRow(ctx, textRenderer, displayX + 8, displayY + 100, displayW - 16, "CORNERS",
                corners != null && corners.asBool(), t, false);
        drawStepper(ctx, t, displayX + 8, displayY + 120, displayW - 16, cornerSize);

        Ui.settingRow(ctx, textRenderer, displayX + 8, displayY + 144, displayW - 16, "DYNAMIC PADDING",
                dynamicPadding != null && dynamicPadding.asBool(), t, false);

        drawSlider(ctx, t, displayX + 8, displayY + 168, displayW - 16, setWidth, widthHit());
        drawSlider(ctx, t, displayX + 8, displayY + 194, displayW - 16, setHeight, heightHit());

        // ---- Sektion SETTINGS ----
        setX = displayX;
        setY = displayY + 244;
        setW = displayW;
        section(ctx, t, "SETTINGS", setX, setY, setW, 104);
        Ui.settingRow(ctx, textRenderer, setX + 8, setY + 20, setW - 16, "SHOW NUMBERS",
                showNumbers != null && showNumbers.asBool(), t, false);
        Ui.settingRow(ctx, textRenderer, setX + 8, setY + 40, setW - 16, "FONT SHADOW",
                fontShadow != null && fontShadow.asBool(), t, false);
        Ui.settingRow(ctx, textRenderer, setX + 8, setY + 60, setW - 16, "HIDE SCOREBOARD",
                hideScoreboard != null && hideScoreboard.asBool(), t, false);

        super.render(ctx, mx, my, delta);
    }

    private void drawTopButtons(DrawContext ctx, Ui.Theme t) {
        Module m = Module.get("scoreboard");
        boolean on = m != null && m.enabled;
        int y = 30;
        int bw = 40;
        Ui.button(ctx, textRenderer, width - 8 - bw * 3 - 8, y, bw, 18, "AN", t,
                hovering(width - 8 - bw * 3 - 8, y, bw, 18), on);
        Ui.button(ctx, textRenderer, width - 8 - bw * 2 - 4, y, bw, 18, "RESET", t,
                hovering(width - 8 - bw * 2 - 4, y, bw, 18), false);
        Ui.button(ctx, textRenderer, width - 8 - bw - 12, y, bw, 18, "⟳", t,
                hovering(width - 8 - bw - 12, y, bw, 18), false);
    }

    private void section(DrawContext ctx, Ui.Theme t, String name, int x, int y, int w, int h) {
        Ui.fill(ctx, x, y, w, h, 0x30000000);
        Ui.outline(ctx, x, y, w, h, Ui.withAlpha(t.text, 0.10f));
        Ui.text(ctx, textRenderer, "— " + Ui.up(name), x + 8, y + 6, Ui.withAlpha(t.dim, 0.9f));
    }

    private int[] scaleHit() {
        return new int[]{displayX + 8, displayY + 36, displayW - 16, 16};
    }

    private int[] widthHit() {
        return new int[]{displayX + 8, displayY + 168, displayW - 16, 16};
    }

    private int[] heightHit() {
        return new int[]{displayX + 8, displayY + 194, displayW - 16, 16};
    }

    // Vorschau ------------------------------------------------------------
    private void drawPreviewCard(DrawContext ctx, Ui.Theme t) {
        Ui.card(ctx, cardX, cardY, cardW, cardH, t, false, false);
        Icons.draw(ctx, "board", cardX + 8, cardY + 8, 1, t.accent, "LINE");
        Ui.text(ctx, textRenderer, "SCOREBOARD", cardX + 22, cardY + 8, t.text);
        Ui.text(ctx, textRenderer, "Vorschau", cardX + 8, cardY + 24, Ui.withAlpha(t.dim, 0.8f));

        // kleines Beispiel-Scoreboard
        int bx = cardX + 10;
        int by = cardY + 40;
        int bw = cardW - 20;
        int bh = 44;
        int bg = 0x90000000;
        if (background != null) {
            String bgv = background.asString();
            if ("TRANSPARENT".equals(bgv)) bg = 0x30FFFFFF;
            else if ("BLUR".equals(bgv)) bg = 0x60000000;
            else if ("CUSTOM".equals(bgv)) bg = bgColor == null ? bg : bgColor.asColor();
        }
        Ui.fill(ctx, bx, by, bw, bh, bg);
        Ui.outline(ctx, bx, by, bw, bh, Ui.withAlpha(t.accent, 0.4f));
        if (corners != null && corners.asBool()) {
            int cs = cornerSize == null ? 4 : cornerSize.asInt();
            Ui.fill(ctx, bx, by, bx + cs, by + 2, t.accent);
            Ui.fill(ctx, bx, by, bx + 2, by + cs, t.accent);
        }
        Ui.text(ctx, textRenderer, "Beispiel", bx + 4, by + 4, 0xFFFFFFFF);
        Ui.text(ctx, textRenderer, "Zeile 2", bx + 4, by + 16, 0xFFFFFF55);
        boolean nums = showNumbers == null || showNumbers.asBool();
        if (nums) {
            Ui.text(ctx, textRenderer, "12", bx + bw - 16, by + 4, 0xFF55FF55);
            Ui.text(ctx, textRenderer, "8", bx + bw - 16, by + 16, 0xFF55FF55);
        }
        String scaleStr = "SKALA " + (scale == null ? "1" : Ui.trim(scale.asDouble()));
        Ui.text(ctx, textRenderer, scaleStr, cardX + 8, cardY + cardH - 12, Ui.withAlpha(t.dim, 0.8f));
    }

    // Widgets ------------------------------------------------------------
    private void drawSlider(DrawContext ctx, Ui.Theme t, int x, int y, int w, Setting s, int[] hit) {
        if (s == null) return;
        Ui.slider(ctx, textRenderer, x, y, w, "", s.asDouble(), s.min, s.max, s.step, t,
                Ui.inside(mouseX, mouseY, hit[0], hit[1], hit[2], hit[3]));
    }

    private void drawStepper(DrawContext ctx, Ui.Theme t, int x, int y, int w, Setting s) {
        if (s == null) return;
        Ui.text(ctx, textRenderer, "SIZE", x, y, t.text);
        int bw = 22;
        int bx = x + w - bw * 2 - 60;
        Ui.button(ctx, textRenderer, bx, y, bw, 16, "-", t, false, false);
        int valW = 60;
        Ui.fill(ctx, bx + bw + 2, y, valW, 16, Ui.withAlpha(t.text, 0.06f));
        Ui.outline(ctx, bx + bw + 2, y, valW, 16, Ui.withAlpha(t.text, 0.18f));
        Ui.textCentered(ctx, textRenderer, String.valueOf(s.asInt()), bx + bw + 2 + valW / 2, y + 4, t.text);
        Ui.button(ctx, textRenderer, bx + bw + 2 + valW + 2, y, bw, 16, "+", t, false, false);
    }

    private void drawDropdown(DrawContext ctx, Ui.Theme t, int x, int y, int w, Setting s, List<String> opts, boolean open) {
        if (s == null) return;
        Ui.dropdown(ctx, textRenderer, x, y, w, 18, s.asString(), opts, open, t, false);
    }

    // Klicks -------------------------------------------------------------
    private int[] topBtn(int index) {
        int bw = 40;
        return new int[]{width - 8 - bw * (3 - index) - 4 * (3 - index), 30, bw, 18};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (backClicked(mx, my)) {
            goBack();
            return true;
        }

        int[] on = topBtn(0), reset = topBtn(1);
        Module m = Module.get("scoreboard");
        if (Ui.inside(mx, my, on[0], on[1], on[2], on[3])) {
            if (m != null) m.enabled = true;
            if (hideScoreboard != null) hideScoreboard.set(false);
            ConfigStore.save();
            return true;
        }
        if (Ui.inside(mx, my, reset[0], reset[1], reset[2], reset[3])) {
            if (m != null) {
                m.enabled = true;
                m.scale = 1.0;
                m.x = 0;
                m.y = 0;
                for (Setting s : m.settings) s.value = s.defaultValue();
            }
            ConfigStore.save();
            return true;
        }

        // DISPLAY
        if (Ui.inside(mx, my, scaleHit()[0], scaleHit()[1] - 6, scaleHit()[2], 22)) {
            draggingScale = true;
            return true;
        }
        // Background dropdown
        int bx = displayX + 8, by = displayY + 72;
        if (Ui.inside(mx, my, bx, by, displayW - 16, 18)) {
            if (openBackground) {
                int i = (int) ((my - (by + 19)) / 18);
                if (i >= 0 && i < bgOptions.size() && my > by + 18) {
                    if (background != null) background.set(bgOptions.get(i));
                    ConfigStore.save();
                }
                openBackground = false;
            } else {
                openBackground = true;
            }
            return true;
        }
        if (Ui.inside(mx, my, displayX + 8, displayY + 120, displayW - 16, 18)) {
            if (cornerSize != null) {
                cornerSize.set(Math.max(0, Math.min(16, cornerSize.asInt() + (Math.round((float) mx - (displayX + 8)) / 14))));
                ConfigStore.save();
            }
            return true;
        }
        if (Ui.inside(mx, my, widthHit()[0], widthHit()[1] - 6, widthHit()[2], 22)) {
            draggingWidth = true;
            return true;
        }
        if (Ui.inside(mx, my, heightHit()[0], heightHit()[1] - 6, heightHit()[2], 22)) {
            draggingHeight = true;
            return true;
        }

        // Corners toggle (row at +100), dynamic padding (+144)
        if (Ui.inside(mx, my, displayX + 8, displayY + 100, displayW - 16, 20)) {
            if (corners != null) corners.set(!corners.asBool());
            ConfigStore.save();
            return true;
        }
        if (Ui.inside(mx, my, displayX + 8, displayY + 144, displayW - 16, 20)) {
            if (dynamicPadding != null) dynamicPadding.set(!dynamicPadding.asBool());
            ConfigStore.save();
            return true;
        }

        // SETTINGS
        if (Ui.inside(mx, my, setX + 8, setY + 20, setW - 16, 20)) {
            if (showNumbers != null) showNumbers.set(!showNumbers.asBool());
            ConfigStore.save();
            return true;
        }
        if (Ui.inside(mx, my, setX + 8, setY + 40, setW - 16, 20)) {
            if (fontShadow != null) fontShadow.set(!fontShadow.asBool());
            ConfigStore.save();
            return true;
        }
        if (Ui.inside(mx, my, setX + 8, setY + 60, setW - 16, 20)) {
            if (hideScoreboard != null) hideScoreboard.set(!hideScoreboard.asBool());
            ConfigStore.save();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int[] hit;
        Setting s;
        if (draggingScale) {
            hit = scaleHit();
            s = scale;
        } else if (draggingWidth) {
            hit = widthHit();
            s = setWidth;
        } else if (draggingHeight) {
            hit = heightHit();
            s = setHeight;
        } else {
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        if (s == null) return true;
        float f = (float) ((mx - (hit[0] + 70)) / Math.max(1f, hit[2] - 76));
        s.set(s.min + f * (s.max - s.min));
        ConfigStore.save();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScale = draggingWidth = draggingHeight = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void close() {
        ConfigStore.save();
        client.setScreen(parent);
    }
}