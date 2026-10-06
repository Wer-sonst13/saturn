package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Der HUD-Editor (Taste F6).
 * Alle sichtbaren HUD-Elemente werden als Rahmen gezeichnet.
 * Ziehen verschiebt, Mausrad ändert die Größe.
 */
public class HudEditorScreen extends SaturnScreen {

    private String dragging;
    private int dragDX, dragDY;

    public HudEditorScreen(net.minecraft.client.gui.screen.Screen parent) {
        super("HUD-Editor", parent);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        cacheMouse(mx, my);
        MinecraftClient mc = MinecraftClient.getInstance();
        Ui.Theme t = Ui.theme();

        // Spiel bleibt sichtbar, nur ein leichter Schleier
        ctx.fill(0, 0, width, height, 0x66000000);
        drawGrid(ctx);
        drawHeader(ctx, "HUD-Editor", "ZIEHEN = VERSCHIEBEN  ·  MAUSRAD = GRÖSSE", true);

        // Toolbar
        drawToolbar(ctx, t);

        // Elemente zeichnen
        for (Module m : Modules.hudModules()) {
            if (!m.enabled) continue;
            int[] b = HudRenderer.bounds(m.id, mc);
            boolean hover = hit(mx, my, b);
            boolean sel = m.id.equals(dragging);
            int col = sel ? t.accent : (hover ? 0xFFFFFFFF : Ui.withAlpha(t.text, 0.6f));
            ctx.fill(b[0] - 2, b[1] - 2, b[0] + b[2] + 2, b[1] + b[3] + 2, 0x66000000);
            ctx.drawBorder(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, col);
            // echten Inhalt leicht transparent darüber, damit man die Position sieht
            ctx.enableScissor(b[0], b[1], b[0] + b[2], b[1] + b[3]);
            ctx.fill(0, 0, width, height, 0x33000000);
            HudRenderer.draw(ctx, mc, m);
            ctx.disableScissor();
            Ui.text(ctx, textRenderer, Ui.up(m.name), b[0] - 2, b[1] - 12, col);
        }

        super.render(ctx, mx, my, delta);
    }

    private void drawGrid(DrawContext ctx) {
        int step = 16;
        for (int x = 0; x < width; x += step) ctx.fill(x, 0, x + 1, height, 0x0AFFFFFF);
        for (int y = 0; y < height; y += step) ctx.fill(0, y, width, y + 1, 0x0AFFFFFF);
    }

    private void drawToolbar(DrawContext ctx, Ui.Theme t) {
        int y = 30;
        Ui.button(ctx, textRenderer, 8, y, 100, 18, "Alles anzeigen", t, false, false);
        Ui.button(ctx, textRenderer, 112, y, 100, 18, "Alles ausblenden", t, false, false);
        Ui.button(ctx, textRenderer, 216, y, 100, 18, "Positionen reset", t, false, false);
    }

    private static boolean hit(double mx, double my, int[] b) {
        return mx >= b[0] - 3 && mx <= b[0] + b[2] + 3 && my >= b[1] - 3 && my <= b[1] + b[3] + 3;
    }

    private String hitModule(MinecraftClient mc, double mx, double my) {
        for (Module m : Modules.hudModules()) {
            if (!m.enabled) continue;
            if (hit(mx, my, HudRenderer.bounds(m.id, mc))) return m.id;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (backClicked(mx, my)) {
            goBack();
            return true;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (Ui.inside(mx, my, 8, 30, 100, 18)) {
            for (Module m : Modules.hudModules()) m.enabled = true;
            ConfigStore.save();
            return true;
        }
        if (Ui.inside(mx, my, 112, 30, 100, 18)) {
            for (Module m : Modules.hudModules()) m.enabled = false;
            ConfigStore.save();
            return true;
        }
        if (Ui.inside(mx, my, 216, 30, 100, 18)) {
            ConfigStore.resetAll();
            ConfigStore.save();
            return true;
        }
        dragging = hitModule(mc, mx, my);
        if (dragging != null) {
            Module m = Module.get(dragging);
            dragDX = (int) (mx - m.x);
            dragDY = (int) (my - m.y);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            Module m = Module.get(dragging);
            if (dragging.equals("scoreboard")) {
                // Scoreboard ist rechts verankert
                m.x = (int) (mx - width + dragDX + HudRenderer.bounds("scoreboard", MinecraftClient.getInstance())[2]);
                m.y = (int) (my - height / 2.0 + dragDY);
            } else {
                m.x = mx - dragDX;
                m.y = my - dragDY;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        ConfigStore.save();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String id = dragging != null ? dragging : hitModule(mc, mx, my);
        if (id != null) {
            Module m = Module.get(id);
            m.scale = Math.max(0.3, Math.min(3.0, m.scale + (float) vAmount * 0.08));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public void close() {
        ConfigStore.save();
        client.setScreen(parent);
    }
}