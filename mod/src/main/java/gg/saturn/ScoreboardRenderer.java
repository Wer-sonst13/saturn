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
 * Zeichnet das Scoreboard selbst, damit Hintergrund, Ecken, Groesse,
 * Zahlen, Abstand und Position wirklich frei einstellbar sind.
 */
public final class ScoreboardRenderer {

    private ScoreboardRenderer() {}

    /** Objectives, fuer die schon gemeldet wurde. */
    private static final java.util.Set<String> diagnoseGemeldet = new java.util.HashSet<>();

    /**
     * Eine Zeile des Scoreboards.
     *
     * Text, Farbe und Punktzahl gehoeren zusammen. Frueher lagen sie in vier
     * parallelen Listen und wurden ueber den Index zugeordnet; ein einziger
     * Versatz darin liess die Zeichenroutine mitten im Bild abbrechen.
     *
     * "teile" ist ein Array, weil ein Eintrag mehrere Zeilen haben kann
     * (Umbruche im Namen). drawText bricht nicht um, also muessen die Teile
     * einzeln gezeichnet werden.
     */
    private static final class Zeile {
        final Text[] teile;
        final int farbe;
        final int score;

        Zeile(Text[] teile, int farbe, int score) {
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
        boolean numbers = mod.flag("showNumbers", false);
        boolean shadow = mod.flag("fontShadow", true);
        boolean dynamic = mod.flag("dynamicPadding", true);
        boolean corners = mod.flag("corners", false);
        int cornerSize = mod.numInt("cornerSize", 4);
        String bgKind = mod.choice("background", "VANILLA");
        int customBg = mod.color("bgColor", 0xB0000000);

        List<Zeile> zeilen = new ArrayList<>();
        try {
            // ScoreboardEntry ist seit 1.21 ein Record mit owner() und value()
            // und implementiert KEIN ScoreHolder.
            for (ScoreboardEntry eintrag : board.getScoreboardEntries(objective)) {
                if (eintrag == null || eintrag.hidden()) continue;
                String name = eintrag.owner();
                if (name == null || name.isBlank()) continue;
                zeilen.add(new Zeile(textDerZeile(board, eintrag, name),
                        teamFarbe(board, name, 0xFFFFFFFF), eintrag.value()));
            }
        } catch (Throwable t) {
            // Nicht einfach schlucken: ein still verschluckter Fehler laesst
            // das ganze Scoreboard weg, ohne dass man den Grund sieht.
            System.err.println("[Saturn] Scoreboard-Eintraege nicht lesbar: " + t);
            return;
        }
        if (zeilen.isEmpty()) return;

        // Einmalige Diagnose: was schickt der Server wirklich?
        //
        // Nach mehreren Fehlversuchen ist die Vermutung nicht mehr wert als
        // eine Messung. Es wird genau einmal je Objective gemeldet, nicht in
        // jedem Bild - sonst fuellt das die Log voll.
        if (!diagnoseGemeldet.contains(objective.getName())) {
            diagnoseGemeldet.add(objective.getName());
            System.err.println("[Saturn] Scoreboard '" + objective.getName() + "' mit "
                    + zeilen.size() + " Eintraegen");
            int n = 0;
            for (ScoreboardEntry e : board.getScoreboardEntries(objective)) {
                if (e == null || n >= 6) continue;
                Team t = board.getScoreHolderTeam(e.owner());
                System.err.println("   [" + n + "] owner='" + e.owner()
                        + "' name='" + (e.name() == null ? "null" : e.name().getString())
                        + "' wert=" + e.value()
                        + " team=" + (t == null ? "keins" : t.getName())
                        + " prefix='" + (t == null ? "" : t.getPrefix().getString())
                        + "' suffix='" + (t == null ? "" : t.getSuffix().getString())
                        + "' teamFarbe=" + farbeVon(t));
                n++;
            }
        }

        // Viele Server benutzen die Punktzahl nur als Reihenfolge (10, 9, 8,
        // ...). Deshalb danach sortieren, sonst kaemen die Zeilen in der
        // Reihenfolge des Empfangs statt in der des Servers.
        List<Zeile> sortiert = new ArrayList<>(zeilen);
        sortiert.sort(Comparator.comparingInt((Zeile z) -> -z.score)
                .thenComparing(z -> z.teile[0].getString()));

        Text titel = objective.getDisplayName();
        if (titel == null) titel = Text.literal("");

        TextRenderer tr = mc.textRenderer;
        int pad = dynamic ? 2 : 1;
        int titleH = 9;
        int rowH = 9;

        int contentW = tr.getWidth(titel);
        int maxNumW = 0;
        int bodyH = 0;
        for (Zeile z : sortiert) {
            for (Text teil : z.teile) contentW = Math.max(contentW, tr.getWidth(teil));
            bodyH += z.teile.length * rowH;
            maxNumW = Math.max(maxNumW, tr.getWidth(String.valueOf(z.score)));
        }
        if (numbers) contentW += maxNumW + 4;

        int w = forcedW > 0 ? forcedW : contentW + pad * 2;
        int h = forcedH > 0 ? forcedH : titleH + bodyH + pad;

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
                for (Text teil : z.teile) {
                    ctx.drawText(tr, teil, pad, y, z.farbe, shadow);
                    y += rowH;
                }
                if (numbers) {
                    // Die Zahl sitzt neben der ersten Zeile des Eintrags.
                    String num = String.valueOf(z.score);
                    ctx.drawText(tr, num, w - pad - maxNumW, pad + titleH, 0xFF55FF55, shadow);
                }
            }
        } finally {
            // pop() gehoert in ein finally: sonst bliebe die Matrix
            // verschoben, wenn mitten im Zeichnen etwas flog.
            ms.pop();
        }
    }

    /**
     * Baut den Text einer Zeile.
     *
     * Manche Server schreiben die Zeile in den Team-Ueberschreiben und
     * lassen owner() leer oder als Rangnummer stehen. Deshalb wird
     * beides geprueft.
     *
     * Wichtig: getString() niemals fuer die Anzeige benutzen. Seit 1.21
     * steckt in einem geholten String kein Formatierungs-Parser mehr - die
     * §-Codes stuenden dann als Buchstaben im Bild und die Farben fehlten.
     */
    private static Text[] textDerZeile(Scoreboard board, ScoreboardEntry eintrag, String name) {
        Text eigener = eintrag.name();
        String roh = eigener != null ? eigener.getString() : name;

        String beitrag = "";
        try {
            Team team = board.getScoreHolderTeam(name);
            if (team != null) {
                beitrag = team.getPrefix().getString() + team.getSuffix().getString();
            }
        } catch (Throwable ignoriert) {
            // dann eben ohne Team
        }

        List<Text> fertig = new ArrayList<>();
        for (String teil : roh.split("\n", -1)) {
            String s = teil;
            if (s.isBlank()) {
                if (beitrag.isBlank()) continue;   // wirklich leere Zeile
                s = beitrag;
            }
            if (s.isBlank()) continue;
            fertig.add(Text.literal(s));
        }
        if (fertig.isEmpty()) {
            fertig.add(Text.literal(beitrag.isBlank() ? name : beitrag));
        }
        return fertig.toArray(new Text[0]);
    }

    /**
     * Farbe des Teams eines Spielers, sonst der Ersatzwert.
     */
    private static int teamFarbe(Scoreboard board, String name, int ersatz) {
        try {
            return farbeVon(board.getScoreHolderTeam(name));
        } catch (Throwable t) {
            return ersatz;
        }
    }

    /**
     * Farbwert eines Teams, 0 wenn es keinen gibt.
     *
     * Achtung: getColorValue() liefert ein Integer und das ist null, wenn das
     * Team keine Farbe gesetzt hat - Formatting.RESET ist zwar nicht null,
     * sein Wert aber schon. Ein simples "ist die Farbe null" reicht nicht,
     * das entpackt null und wirft einen NullPointerException. Genau daran ist
     * das Spiel abgestuerzt.
     */
    private static int farbeVon(Team team) {
        if (team == null) return 0;
        Formatting f = team.getColor();
        if (f == null) return 0;
        Integer wert = f.getColorValue();
        if (wert == null) return 0;
        return 0xFF000000 | wert;
    }
}