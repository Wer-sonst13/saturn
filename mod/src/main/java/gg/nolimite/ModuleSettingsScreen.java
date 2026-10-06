package gg.nolimite;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Einstellungsseite eines Moduls.
 * Bool-Schalter, Schieberegler, Auswahlfelder und Textfelder werden
 * automatisch aus der Modul-Definition erzeugt.
 */
public class ModuleSettingsScreen extends NoLimiteScreen {

    private final Module module;
    private Screen after;

    private List<Setting> visible = new ArrayList<>();
    private int top, bottom;
    private String openDropdown;
    private boolean editing;
    private boolean resetPressed;

    public ModuleSettingsScreen(Module module, Screen parent) {
        super(module.name, parent);
        this.module = module;
        this.after = parent;
    }

    @Override
    protected void init() {
        super.init();
        top = 40;
        bottom = height - 26;
        visible = new ArrayList<>();
        // Scoreboard hat eine eigene Seite, dort keine generischen Regler zeigen
        for (Setting s : module.settings) {
            if (s.key.equals("scale") && module.id.equals("scoreboard")) continue;
            if (s.key.equals("profile")) continue;
            visible.add(s);
        }
    }

    private int rowHeight(Setting s) {
        if (s.type == Setting.Type.CHOICE) {
            return openDropdown != null && openDropdown.equals(s.key) ? 18 + s.choices.size() * 16 + 4 : 30;
        }
        if (s.type == Setting.Type.TEXT) return 30;
        return 26;
    }

    private int contentHeight() {
        int h = 0;
        for (Setting s : visible) h += rowHeight(s) + 4;
        return h + 92;
    }

    // ------------------------------------------------------------------ Malen

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        cacheMouse(mx, my);
        Ui.Theme t = Ui.theme();

        drawBackdrop(ctx);
        drawHeader(ctx, module.name, Ui.up(module.category), true);

        // Kopfzeile des Moduls
        int hx = 8, hy = 34, hw = width - 16, hh = 44;
        Ui.card(ctx, hx, hy, hw, hh, t, false, module.enabled);
        Icons.draw(ctx, module.icon, hx + 8, hy + 8, 2, module.enabled ? t.accent : t.dim, "LINE");
        Ui.text(ctx, textRenderer, Ui.up(module.name), hx + 34, hy + 8, t.text);
        List<String> lines = Ui.wrap(textRenderer, module.desc, hw - 150, 2);
        int ly = hy + 20;
        for (String l : lines) {
            if (ly > hy + hh - 4) break;
            Ui.text(ctx, textRenderer, l, hx + 34, ly, Ui.withAlpha(t.dim, 0.9f));
            ly += 9;
        }
        Ui.button(ctx, textRenderer, hx + hw - 70, hy + 12, 60, 20, module.enabled ? "AN" : "AUS",
                t, hovering(hx + hw - 70, hy + 12, 60, 20), module.enabled);

        top = 84;
        bottom = height - 26;

        ctx.enableScissor(0, top - 1, width, bottom + 1);
        scroll = clampScroll(scroll, contentHeight(), bottom - top);
        drawRows(ctx, t);
        ctx.disableScissor();
        Ui.scrollbar(ctx, width - 6, top, bottom - top, contentHeight(), bottom - top, scroll, t);

        drawFooter(ctx, t);
        super.render(ctx, mx, my, delta);
    }

    private void drawRows(DrawContext ctx, Ui.Theme t) {
        int x = 10;
        int w = width - 24;
        int y = top - (int) scroll;

        for (Setting s : visible) {
            int rh = rowHeight(s);
            if (y + rh >= top - 2 && y <= bottom + 2) {
                Ui.card(ctx, x, y, w, Math.min(rh - 4, bottom - y), t, false, false);
                drawSetting(ctx, t, s, x + 6, y + 6, w - 12);
            }
            y += rh + 4;
        }
    }

    private void drawSetting(DrawContext ctx, Ui.Theme t, Setting s, int x, int y, int w) {
        switch (s.type) {
            case BOOL: {
                Ui.settingRow(ctx, textRenderer, x, y + 2, w, s.label, s.asBool(), t, false);
                break;
            }
            case INT, DOUBLE: {
                Ui.slider(ctx, textRenderer, x, y, w, s.label, s.asDouble(), s.min, s.max,
                        s.type == Setting.Type.INT ? 1 : s.step, t, false);
                break;
            }
            case TEXT: {
                Ui.text(ctx, textRenderer, Ui.up(s.label), x, y, t.text);
                int by = y + 11;
                Ui.fill(ctx, x, by, w, 14, Ui.withAlpha(t.text, 0.06f));
                Ui.outline(ctx, x, by, w, 14, editing ? t.accent : Ui.withAlpha(t.text, 0.2f));
                String v = s.asString();
                Ui.text(ctx, textRenderer, editing ? v + "▌" : v, x + 4, by + 3, t.text);
                break;
            }
            case CHOICE: {
                int dh = 18;
                int dy = y + 11;
                Ui.text(ctx, textRenderer, Ui.up(s.label), x, y, t.text);
                List<String> opts = new ArrayList<>(s.choices);
                Ui.dropdown(ctx, textRenderer, x + w - 150, dy, 150, dh, s.asString(), opts,
                        openDropdown != null && openDropdown.equals(s.key), t, false);
                break;
            }
            case COLOR: {
                Ui.text(ctx, textRenderer, Ui.up(s.label), x, y + 2, t.text);
                int bw = 54;
                int bx = x + w - bw;
                int c = s.asColor();
                Ui.fill(ctx, bx, y, bw, 14, c);
                Ui.outline(ctx, bx, y, bw, 14, Ui.withAlpha(t.text, 0.3f));
                Ui.text(ctx, textRenderer, String.format("%06X", c & 0xFFFFFF), bx + 4, y + 3, 0xFFFFFFFF);
                break;
            }
        }
    }

    private void drawFooter(DrawContext ctx, Ui.Theme t) {
        int y = height - 22;
        Ui.fill(ctx, 0, y, width, 22, 0xE60B0D12);
        Ui.outline(ctx, 0, y, width, 1, Ui.withAlpha(t.accent, 0.35f));
        Ui.button(ctx, textRenderer, 8, y + 3, 90, 16, "Zurücksetzen", t,
                hovering(8, y + 3, 90, 16), resetPressed);
        Ui.button(ctx, textRenderer, 102, y + 3, 70, 16, "Alle aus", t, hovering(102, y + 3, 70, 16), false);
        String h = "Änderungen werden sofort gespeichert";
        Ui.text(ctx, textRenderer, h, width - 8 - textRenderer.getWidth(h), y + 8, Ui.withAlpha(t.dim, 0.8f));
    }

    // ------------------------------------------------------------------ Klicks

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (backClicked(mx, my)) {
            goBack();
            return true;
        }

        // Scoreboard hat eine eigene, eigene Seite
        if (module.id.equals("scoreboard")
                && Ui.inside(mx, my, width - 78, 46, 60, 20)) {
            client.setScreen(new ScoreboardScreen(this));
            return true;
        }

        // Modul an/aus
        if (Ui.inside(mx, my, width - 78, 46, 60, 20)) {
            module.enabled = !module.enabled;
            ConfigStore.save();
            return true;
        }

        // Fußzeile
        int fy = height - 19;
        if (Ui.inside(mx, my, 8, fy, 90, 16)) {
            for (Setting s : module.settings) s.value = s.defaultValue();
            module.enabled = true;
            module.scale = 1.0;
            ConfigStore.save();
            return true;
        }
        if (Ui.inside(mx, my, 102, fy, 70, 16)) {
            module.enabled = false;
            ConfigStore.save();
            return true;
        }

        // Zeilen treffen
        int x = 10;
        int w = width - 24;
        int y = top - (int) scroll;
        for (Setting s : visible) {
            int rh = rowHeight(s);
            if (Ui.inside(mx, my, x, y, w, rh - 4)) {
                handleClick(s, mx - x, my - y, w - 12, x, y);
                return true;
            }
            y += rh + 4;
        }
        return true;
    }

    private void handleClick(Setting s, double lx, double ly, int lw, int ax, int ay) {
        switch (s.type) {
            case BOOL: {
                int sx = ax + lw - 46, sy = ay + 8;
                if (Ui.inside(lx, ly, sx - ax, sy - ay, 46, 14)) s.set(!s.asBool());
                else s.set(!s.asBool());
                ConfigStore.save();
                break;
            }
            case INT, DOUBLE: {
                int sliderX = ax + Math.max(90, Math.round(lw * 0.42f)) - ax;
                int sliderW = lw - sliderX - 4;
                if (lx >= sliderX) {
                    float f = (float) ((lx - sliderX) / Math.max(1f, sliderW));
                    double v = s.min + f * (s.max - s.min);
                    s.set(v);
                    ConfigStore.save();
                }
                break;
            }
            case TEXT: {
                editing = !editing;
                break;
            }
            case CHOICE: {
                boolean open = openDropdown != null && openDropdown.equals(s.key);
                int dh = 18;
                int oy = ay + 17;
                if (open) {
                    int i = (int) ((ly - (oy - ay)) / dh);
                    if (i >= 0 && i < s.choices.size() && ly >= oy - ay) {
                        s.set(s.choices.get(i));
                        ConfigStore.save();
                    }
                    openDropdown = null;
                } else {
                    openDropdown = s.key;
                }
                break;
            }
            case COLOR: {
                // einfaches Umschalten zwischen vier Farben
                String[] presets = {"FFFFFFFF", "FF55FF55", "FFFFAA00", "FFFF5555"};
                int cur = s.asColor() & 0xFFFFFF;
                for (String p : presets) {
                    if (Integer.parseInt(p, 16) == cur) {
                        int i = 0;
                        while (i < presets.length && Integer.parseInt(presets[i], 16) == cur) i++;
                        s.set((int) Long.parseLong(presets[i % presets.length], 16));
                        break;
                    }
                }
                ConfigStore.save();
                break;
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int x = 10;
        int w = width - 24;
        int y = top - (int) scroll;
        for (Setting s : visible) {
            int rh = rowHeight(s);
            if (Ui.inside(mx, my, x, y, w, rh - 4) && (s.type == Setting.Type.INT || s.type == Setting.Type.DOUBLE)) {
                int lw = w - 12;
                int sliderX = Math.max(90, Math.round(lw * 0.42f));
                int sliderW = lw - sliderX - 4;
                float f = (float) ((mx - (x + sliderX)) / Math.max(1f, sliderW));
                s.set(s.min + f * (s.max - s.min));
                ConfigStore.save();
                return true;
            }
            y += rh + 4;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (openDropdown != null) {
            openDropdown = null;
            return true;
        }
        scroll = applyScroll(scroll, contentHeight(), bottom - top, vAmount);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editing) {
            for (Setting s : visible) {
                if (s.type == Setting.Type.TEXT) {
                    if (chr == 8) {
                        String v = s.asString();
                        if (!v.isEmpty()) s.set(v.substring(0, v.length() - 1));
                    } else if (chr >= 32 && chr < 127) {
                        s.set(s.asString() + chr);
                    }
                    ConfigStore.save();
                    return true;
                }
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                editing = false;
                return true;
            }
        }
        if (openDropdown != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            openDropdown = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        ConfigStore.save();
        client.setScreen(after != null ? after : parent);
    }
}