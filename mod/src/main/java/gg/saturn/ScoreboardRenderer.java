package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Zeichnet das Scoreboard selbst, damit Hintergrund, Ecken, Grösse,
 * Zahlen, Abstand und Position wirklich frei einstellbar sind.
 */
public final class ScoreboardRenderer {

    private ScoreboardRenderer() {}

    /**
     * Eine Zeile des Scoreboards.
     *
     * Frueher lagen Name, Zahl, Text und Farbe in vier parallelen Listen und
     * wurden ueber den Index zugeordnet. Sobald eine davon nicht passte, kam
     * mitten im Zeichnen ein Fehler heraus - der Hintergrund war schon
     * weg, der Text nicht, und weil dabei auch ms.pop() uebersprungen wurde,
     * saessen danach alle folgenden Elemente an versetzter Stelle.
     *
     * Deshalb gehoeren die vier Angaben zu einer Zeile zusammen.
     */
    private static final class Zeile {
        final Text text;
        final int farbe;
        final int score;
        Zeile(Text text, int farbe, int score) {
            this.text = text;
            this.farbe = farbe;
            this.score = score;
        }
    }

    public static void render(DrawContext ctx, ScoreboardObjective objective, int scaledWidth) {
        Module mod = Module.get("scoreboard");
        if (mod == null || !mod.enabled || mod.flag("hideScoreboard", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (mc.options.hudHidden) return;

        Scoreboard board = mc.player.networkHandler.getScoreboard();
        if (board == null || objective == null) return;

        float scale = (float) Math.max(0.3, mod.num("scale", 1.0));
        int forcedW = mod.numInt("width", 0);
        int forcedH = mod.numInt("height", 0);
        boolean numbers = mod.flag("showNumbers", true);
        boolean shadow = mod.flag("fontShadow", true);
        boolean dynamic = mod.flag("dynamicPadding", true);
        boolean corners = mod.flag("corners", false);
        int cornerSize = mod.numInt("cornerSize", 4);
        String bgKind = mod.choice("background", "VANILLA");
        int customBg = mod.color("bgColor", 0xB0000000);

        // Zeilen sammeln. ScoreboardEntry ist seit 1.21 ein Record mit owner()
        // und value() und implementiert KEIN ScoreHolder.
        List<Zeile> zeilen = new ArrayList<>();
        try {
            for (ScoreboardEntry eintrag : board.getScoreboardEntries(objective)) {
                if (eintrag == null || eintrag.hidden()) continue;
                String name = eintrag.owner();
                if (name == null || name.isEmpty()) continue;
                // Nur den Namen nehmen: entry.display() enthaelt fuer die
                // Seitenleiste schon die Zahl, die wir zusaetzlich zeichnen -
                // sonst stand jede Zeile doppelt da.
                Text t = eintrag.name();
                zeilen.add(new Zeile(t == null ? Text.literal(name) : t,
                        teamFarbe(board, name, 0xFFFFFFFF), eintrag.value()));
            }
        } catch (Throwable t) {
            return;   // lieber nichts als halb gezeichnet
        }
        if (zeilen.isEmpty()) return;

        // absteigend nach Punktzahl, bei Gleichstand stabil nach Name
        List<Zeile> sortiert = new ArrayList<>(zeilen);
        sortiert.sort(Comparator.comparingInt((Zeile z) -> -z.score)
                .thenComparing(z -> z.text.getString()));

        // Titel als Text behalten: getString() liefert die §-Codes roh mit,
        // und die werden seit 1.21 nicht mehr als Formatierung erkannt.
        Text titel = objective.getDisplayName();
        if (titel == null) titel = Text.literal("");

        TextRenderer tr = mc.textRenderer;
        int pad = dynamic ? 2 : 1;
        int titleH = 9;
        int rowH = 9;

        int contentW = tr.getWidth(titel);
        int maxNumW = 0;
        for (Zeile z : sortiert) {
            contentW = Math.max(contentW, tr.getWidth(z.text));
            maxNumW = Math.max(maxNumW, tr.getWidth(String.valueOf(z.score)));
        }
        if (numbers) contentW += maxNumW + 4;

        int w = forcedW > 0 ? forcedW : contentW + pad * 2;
        int bodyH = sortiert.size() * rowH;
        int h = forcedH > 0 ? forcedH : titleH + bodyH + pad;

        // rechts verankert, vertikal mittig
        int right = scaledWidth + (int) mod.x;
        int top = mc.getWindow().getScaledHeight() / 2 + (int) mod.y - h / 2;
        int left = right - w;

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        try {
            ms.translate(left, top, 0);
            ms.scale(scale, scale, 1f);

            int bgColor = switch (bgKind) {
                case "TRANSPARENT" -> 0x00000000;
                case "BLUR" -> 0x60000000;
                case "CUSTOM" -> customBg;
                default -> 0x90000000;
            };
            if (bgColor != 0) ctx.fill(0, 0, w, h, bgColor);

            if (corners && cornerSize > 0) {
                int cs = Math.min(cornerSize, Math.max(1, Math.min(w, h) / 2));
                int cc = Ui.withAlpha(0xFFFFFF, 0.35f);
                ctx.fill(0, 0, cs, 1, cc);
                ctx.fill(0, 0, 1, cs, cc);
                ctx.fill(w - cs, h - 1, w, h, cc);
                ctx.fill(w - 1, h - cs, w, h, cc);
            }
            ctx.drawBorder(0, 0, w, h, Ui.withAlpha(0xFFFFFF, 0.18f));

            int y = pad;
            ctx.drawText(tr, titel, pad, y, 0xFFFFFFFF, shadow);
            y += titleH;

            for (Zeile z : sortiert) {
                ctx.drawText(tr, z.text, pad, y, z.farbe, shadow);
                if (numbers) {
                    String num = String.valueOf(z.score);
                    ctx.drawText(tr, num, w - pad - tr.getWidth(num), y, 0xFF55FF55, shadow);
                }
                y += rowH;
            }
        } finally {
            // pop() gehoert in ein finally: sonst blieb die Matrix verschoben,
            // wenn mitten im Zeichnen etwas flog - und dann saessen alle
            // folgenden HUD-Elemente an der falschen Stelle.
            ms.pop();
        }
    }

    /**
     * Farbe des Teams eines Spielers, sonst {@code fallback}.
     *
     * Wichtig: nicht {@code team.getPrefix().getString()}. Seit 1.21 steckt in
     * einem per getString() geholten Text kein Formatierungs-Parser mehr -
     * die §-Codes standen deshalb als Buchstaben im Bild und die Farben
     * fehlten. Die Farbe holt man sich hier direkt aus dem Team.
     */
    private static int teamFarbe(Scoreboard board, String name, int fallback) {
        try {
            Team team = board.getScoreHolderTeam(name);
            if (team == null) return fallback;
            Formatting f = team.getColor();
            return f == null ? fallback : 0xFF000000 | f.getColorValue();
        } catch (Throwable t) {
            return fallback;
        }
    }
}