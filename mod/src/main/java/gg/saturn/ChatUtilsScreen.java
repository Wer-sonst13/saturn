package gg.saturn;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * CHAT UTILS (Taste F7).
 * Gespeicherter Chatverlauf, Volltext-Suche, Kopieren mit einem Klick.
 */
public class ChatUtilsScreen extends SaturnScreen {

    private String query = "";
    private boolean editing;
    private final List<Trackers.ChatLine> lines = new ArrayList<>();
    private int visible;

    public ChatUtilsScreen(Screen parent) {
        super("Chat Utils", parent);
    }

    @Override
    protected void init() {
        super.init();
        lines.clear();
        lines.addAll(Trackers.chat());
        visible = Math.max(1, (height - 90) / 10);
    }

    private List<Trackers.ChatLine> filtered() {
        if (query.isEmpty()) return lines;
        String q = query.toLowerCase();
        List<Trackers.ChatLine> out = new ArrayList<>();
        for (Trackers.ChatLine l : lines) if (l.text.toLowerCase().contains(q)) out.add(l);
        return out;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        cacheMouse(mx, my);
        Ui.Theme t = Ui.theme();
        List<Trackers.ChatLine> list = filtered();

        drawBackdrop(ctx);
        drawHeader(ctx, "Chat Utils", list.size() + " NACHRICHTEN", true);

        // Suchfeld
        int sx = 10, sy = 32, sw = width - 20 - 90;
        Ui.fill(ctx, sx, sy, sw, 18, Ui.withAlpha(t.text, 0.06f));
        Ui.outline(ctx, sx, sy, sw, 18, editing ? t.accent : Ui.withAlpha(t.text, 0.18f));
        Ui.text(ctx, textRenderer, editing ? query + "▌" : (query.isEmpty() ? "SUCHEN … (Klick zum Schreiben)" : query),
                sx + 6, sy + 5, query.isEmpty() ? Ui.withAlpha(t.dim, 0.9f) : t.text);
        Ui.button(ctx, textRenderer, sx + sw + 6, sy, 40, 18, "Kop", t, false, false);
        Ui.button(ctx, textRenderer, sx + sw + 50, sy, 34, 18, "clr", t, false, false);

        // Liste
        int y = sy + 26;
        if (list.isEmpty()) {
            Ui.text(ctx, textRenderer, "Noch keine Nachrichten gespeichert.", sx, y + 4, Ui.withAlpha(t.dim, 0.9f));
            super.render(ctx, mx, my, delta);
            return;
        }
        int start = Math.max(0, list.size() - visible);
        for (int i = start; i < list.size(); i++) {
            Trackers.ChatLine l = list.get(i);
            boolean hover = Ui.inside(mouseX, mouseY, sx, y, width - 20, 10);
            if (hover) Ui.fill(ctx, sx, y, width - 20, 10, Ui.withAlpha(t.accent, 0.15f));
            String shown = l.time + "  " + l.text;
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(shown, width - 40),
                    sx + 3, y, hover ? t.text : Ui.withAlpha(t.dim, 0.95f));
            y += 10;
            if (y > height - 24) break;
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (backClicked(mx, my)) {
            goBack();
            return true;
        }
        int sw = width - 20 - 90;
        if (Ui.inside(mx, my, 10, 32, sw, 18)) {
            editing = !editing;
            return true;
        }
        if (Ui.inside(mx, my, 10 + sw + 6, 32, 40, 18)) {
            // alles kopieren
            StringBuilder sb = new StringBuilder();
            for (Trackers.ChatLine l : lines) sb.append('[').append(l.time).append("] ").append(l.text).append('\n');
            if (client != null && client.keyboard != null) client.keyboard.setClipboard(sb.toString());
            return true;
        }
        if (Ui.inside(mx, my, 10 + sw + 50, 32, 34, 18)) {
            Trackers.clearChat();
            lines.clear();
            return true;
        }

        // Einzelne Zeile anklicken zum Kopieren
        int y = 58;
        List<Trackers.ChatLine> list = filtered();
        int start = Math.max(0, list.size() - visible);
        for (int i = start; i < list.size(); i++) {
            if (Ui.inside(mx, my, 10, y, width - 20, 10)) {
                if (client != null && client.keyboard != null) client.keyboard.setClipboard(list.get(i).text);
                return true;
            }
            y += 10;
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editing) {
            if (chr == 8) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
            } else if (chr >= 32 && chr < 127) {
                query = query + chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            editing = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}