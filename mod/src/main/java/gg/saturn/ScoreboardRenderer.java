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
     * mitten im Zeichnen ein Fehler heraus.
     *
     * WICHTIG: der Text kommt aus entry.owner(), nicht aus entry.name().
     * name() lieferte bei diesem Server leeren Text - es erschienen nur die
     * gruenen Zahlen, aber keine einzige Beschriftung.
     *
     * Ausserdem kann ein Eintrag mehrere Zeilen enthalten (Umbruch im Text).
     * Die stehen als Umbruchzeichen im Namen und muessen von Hand
     * untereinander gezeichnet werden - drawText ignoriert sie sonst still.
     */
    private static final class Zeile {
        final String[] teile;
        final int farbe;
        final int score;
        Zeile(String[] teile, int farbe, int score) {
            this.teile = teile;
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
                // Den Namen des Servers nehmen und an Umbruechen teilen.
                zeilen.add(new Zeile(name.split("\n", -1),
                        teamFarbe(board, name, 0xFFFFFFFF), eintrag.value()));
            }
        } catch (Throwable t) {
            // Nicht einfach schlucken. Ein still verschluckter Fehler fuehrt
            // dazu, dass gar nichts mehr erscheint und man den Grund nicht
            // sieht. Im Spiel melden, dann ist es nachlesbar.
            System.err.println("[Saturn] Scoreboard-Eintraege nicht lesbar: " + t);
            return;
        }
        if (zeilen.isEmpty()) return;

        // absteigend nach Punktzahl, bei Gleichstand stabil nach Namen.
        //
        // Der Server benutzt die Punktzahl als Reihenfolge (10, 9, 8 ...),
        // deshalb wird danach sortiert. Sonst kaemen die Zeilen in der
        // Reihenfolge des Empfangs statt in der des Servers.
        List<Zeile> sortiert = new ArrayList<>(zeilen);
        sortiert.sort(Comparator.comparingInt((Zeile z) -> -z.score)
                .thenComparing(z -> z.teile[0]));

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
        int zeilenH = 0;
        for (Zeile z : sortiert) {
            for (String teil : z.teile) contentW = Math.max(contentW, tr.getWidth(teil));
            zeilenH += z.teile.length * rowH;
            maxNumW = Math.max(maxNumW, tr.getWidth(String.valueOf(z.score)));
        }
        if (numbers) contentW += maxNumW + 4;

        int w = forcedW > 0 ? forcedW : contentW + pad * 2;
        int h = forcedH > 0 ? forcedH : titleH + zeilenH + pad;

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
                // Jeden Teil einzeln zeichnen: ein Eintrag kann mehrere Zeilen
                // haben, und drawText bricht nicht um.
                int numBreite = numbers ? tr.getWidth(String.valueOf(z.score)) : 0;
                for (String teil : z.teile) {
                    ctx.drawText(tr, teil, pad, y, z.farbe, shadow);
                    if (numbers) {
                        // Die Zahl sitzt an der oberen Zeile des Eintrags.
                        ctx.drawText(tr, String.valueOf(z.score),
                                w - pad - numBreite, y, 0xFF55FF55, shadow);
                    }
                    y += rowH;
                }
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