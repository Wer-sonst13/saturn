package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
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
    /**
     * Baut den Text einer Zeile.
     *
     * GEMESSEN (im Spielprotokoll):
     *   owner = "§0§6§r"        <- nur Farbcodes, kein Text
     *   prefix = "⛨ ʀᴀɴᴋ » MANAGER "   <- der eigentliche Text
     *
     * Der Name besteht also fast nur aus Formatierung. Ein Text.literal() auf
     * so einen String zeichnet Zeichen ohne Glyphe - die Zeile blieb leer.
     * Genau das war der Fehler, den ich vorher nicht gefunden habe.
     *
     * Deshalb: die Codes aus dem Namen werden zu einem echten Style, und der
     * Text aus dem Team-Prefix wird mit diesem Style gezeichnet. Der Prefix
     * bringt seine eigenen Farben fuer einzelne Woerter mit (§7, §a ...), die
     * dann die Grundfarbe uebersteuern - so wie beim Original.
     */
    private static Text[] textDerZeile(Scoreboard board, ScoreboardEntry eintrag, String name) {
        Text eigener = eintrag.name();
        String roh = eigener != null ? eigener.getString() : name;

        String prefix = "";
        String suffix = "";
        try {
            Team team = board.getScoreHolderTeam(name);
            if (team != null) {
                if (team.getPrefix() != null) prefix = team.getPrefix().getString();
                if (team.getSuffix() != null) suffix = team.getSuffix().getString();
            }
        } catch (Throwable ignoriert) {
            // dann eben ohne Team
        }

        Style grund = stilAusCodes(roh);

        List<Text> fertig = new ArrayList<>();
        // Der Name kann selbst Text enthalten (manche Server). Dann wird er
        // mit seinem eigenen Style gezeichnet.
        String sichtbarerName = ohneCodes(roh);
        if (!sichtbarerName.isBlank()) {
            for (String teil : sichtbarerName.split("\n", -1)) {
                if (!teil.isBlank()) fertig.add(Text.literal(teil).setStyle(grund));
            }
        }
        // Der Prefix enthaelt den eigentlichen Text samt eigener Farben.
        // Die werden zu echtem Text mit Formatierung gebaut und dann auf die
        // Grundfarbe aus dem Namen gesetzt - so wie im Original.
        String beitrag = prefix + suffix;
        if (!ohneCodes(beitrag).isBlank()) {
            for (Text teil : legy(beitrag, grund)) {
                fertig.add(teil.copy());
            }
        }
        if (fertig.isEmpty()) return new Text[0];
        return fertig.toArray(new Text[0]);
    }

    /** Der Text ohne die §-Codes. */
    private static String ohneCodes(String s) {
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) { i++; continue; }
            r.append(c);
        }
        return r.toString();
    }

    /**
     * Wandelt eine Folge von §-Codes in einen echten Minecraft-Style.
     *
     * Genau das fehlte vorher: die Codes waren Text, dabei sind sie
     * Formatierung. §0§6§r heisst "schwarz, dann gold, dann zurueck" und
     * ergibt als Style die goldene Farbe fuer das, was danach kommt.
     */
    private static Style stilAusCodes(String s) {
        Style stil = Style.EMPTY;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '\u00A7' || i + 1 >= s.length()) continue;
            char f = s.charAt(++i);
            if (f == '#' && i + 6 <= s.length() - 1) {
                String hex = s.substring(i + 1, i + 7);
                try {
                    stil = codeAnwenden(stil, TextColor.parse(hex));
                    i += 6;
                    continue;
                } catch (RuntimeException ignoriert) {
                    // kein Hex-Code
                }
            }
            Formatting fmt = formatierungZu(f);
            if (fmt == null) continue;
            stil = codeAnwenden(stil, fmt);
        }
        return stil;
    }

    /** §0..§f, §k..§o, §r in die passende Formatierung. */
    private static Formatting formatierungZu(char f) {
        return switch (Character.toLowerCase(f)) {
            case '0' -> Formatting.BLACK;
            case '1' -> Formatting.DARK_BLUE;
            case '2' -> Formatting.DARK_GREEN;
            case '3' -> Formatting.DARK_AQUA;
            case '4' -> Formatting.DARK_RED;
            case '5' -> Formatting.DARK_PURPLE;
            case '6' -> Formatting.GOLD;
            case '7' -> Formatting.GRAY;
            case '8' -> Formatting.DARK_GRAY;
            case '9' -> Formatting.BLUE;
            case 'a' -> Formatting.GREEN;
            case 'b' -> Formatting.AQUA;
            case 'c' -> Formatting.RED;
            case 'd' -> Formatting.LIGHT_PURPLE;
            case 'e' -> Formatting.YELLOW;
            case 'f' -> Formatting.WHITE;
            case 'k' -> Formatting.OBFUSCATED;
            case 'l' -> Formatting.BOLD;
            case 'm' -> Formatting.STRIKETHROUGH;
            case 'n' -> Formatting.UNDERLINE;
            case 'o' -> Formatting.ITALIC;
            case 'r' -> Formatting.RESET;
            default -> null;
        };
    }

    /**
     * Macht aus einem alten §-String echten Text mit Formatierung.
     *
     * Der entscheidende Schritt: §-Codes werden zu Styles, nicht zu Zeichen.
     * Vorher wurden sie mitgezeichnet - und genau deshalb waren die Zeilen
     * leer, denn der Name besteht laut Messung fast nur aus Codes.
     *
     * Gibt je Zeile (Umbruch) einen eigenen Text zurueck, damit mehrere
     * Zeilen getrennt gezeichnet werden koennen.
     */
    private static List<Text> legy(String s, Style start) {
        List<Text> raus = new ArrayList<>();
        MutableText zeile = Text.empty();
        // Mit der Grundfarbe aus dem Namen beginnen: Abschnitte ohne eigenen
        // Code erben sie, Abschnitte mit Code uebersteuern sie.
        Style stil = (start == null) ? Style.EMPTY : start;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) {
                char f = s.charAt(++i);
                if (f == '#' && i + 7 <= s.length()) {
                    String hex = s.substring(i + 1, i + 7);
                    try {
                        stil = codeAnwenden(stil, TextColor.parse(hex));
                        i += 6;
                        continue;
                    } catch (RuntimeException ignoriert) {
                        // kein Hex-Code
                    }
                }
                Formatting fmt = formatierungZu(f);
                if (fmt == null) continue;
                stil = codeAnwenden(stil, fmt);
                continue;
            }
            if (c == '\n') {
                raus.add(zeile);
                zeile = Text.empty();
                continue;
            }
            zeile.append(Text.literal(String.valueOf(c)).setStyle(stil));
        }
        raus.add(zeile);
        return raus;
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
    /**
     * Einen §-Code auf den Style anwenden.
     *
     * In 1.21 gibt es weder Style.applyFormat() noch TextColor.getName();
     * beides war aelter und wird vom Compiler abgelehnt. Farben laufen ueber
     * withColor, die Auszeichnungen ueber die einzelnen Setter.
     */
    private static Style codeAnwenden(Style stil, Object fmt) {
        if (fmt instanceof TextColor farbe) return stil.withColor(farbe);
        if (!(fmt instanceof Formatting f)) return stil;
        if (f == Formatting.RESET) return Style.EMPTY;
        if (f.isColor()) return stil.withColor(f);
        if (f == Formatting.BOLD) return stil.withBold(true);
        if (f == Formatting.ITALIC) return stil.withItalic(true);
        if (f == Formatting.UNDERLINE) return stil.withUnderline(true);
        if (f == Formatting.STRIKETHROUGH) return stil.withStrikethrough(true);
        if (f == Formatting.OBFUSCATED) return stil.withObfuscated(true);
        return stil;
    }
}
