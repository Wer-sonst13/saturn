package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Misst, was der Client pro Bild wirklich kostet.
 *
 * Grundgedanke: ohne Zahlen optimiert man blind. Manches sieht harmlos aus
 * und frisst trotzdem Millisekunden, anderes sieht teuer aus und ist
 * belanglos. DieserProfiler misst jede Stelle einzeln und zeigt die
 * teuersten zuerst.
 *
 * Bedienung: F9 blendet die Anzeige ein und aus. "S" schreibt einen
 * Bericht in den Konfigurationsordner, damit er sich auslesen laesst.
 *
 * Wichtig: die Zeit wird nur gemessen, wenn das Fenster offen ist (oder
 * beim Schreiben). Ohne Anzeige kostet der Profiler fast nichts.
 */
public final class Profiler {

    private Profiler() {}

    /** Wie viele Bilder die Werte mitteln, bevor sie gezeigt werden. */
    private static final int MESSE = 120;

    private static boolean an;
    private static boolean misst;
    private static boolean speichern;

    private static int frames;
    private static long ersterFrame;
    private static double fpsSumme;
    private static int fpsProben;

    private static final Map<String, Long> SUMME = new LinkedHashMap<>();
    private static final Map<String, Integer> ANZAHL = new LinkedHashMap<>();

    /** Markiert eine Messstrecke. */
    public static void start(String ort) {
        if (!misst) return;
        long jetzt = System.nanoTime();
        if (jetzt < 0) return;
        // Verschachtelte Aufrufe: die groessere Klammer gewinnt.
        offen.put(ort, jetzt);
    }

    private static final Map<String, Long> offen = new LinkedHashMap<>();

    public static void stop(String ort) {
        if (!misst) return;
        Long start = offen.remove(ort);
        if (start == null) return;
        long dauer = System.nanoTime() - start;
        if (dauer < 0) return;
        SUMME.merge(ort, dauer, Long::sum);
        ANZAHL.merge(ort, 1, Integer::sum);
    }

    /** Einmal pro Bild aufrufen, ganz am Ende des HUD-Zeichnens. */
    public static void frameEnde(MinecraftClient mc) {
        if (!an && !speichern) return;
        misst = true;
        if (ersterFrame == 0) ersterFrame = System.nanoTime();

        frames++;
        if (mc != null) {
            fpsSumme += 1.0 / Math.max(1e-6, (mc.getCurrentFps() == 0 ? 60 : mc.getCurrentFps()));
            fpsProben++;
        }

        // Das Schreiben nicht auf volle Messfenster warten lassen: wer S
        // drueckt, will jetzt die Zahlen sehen und nicht nach einer Minute.
        if (speichern && frames >= 20) {
            schreibe(mc);
            speichern = false;
            if (!an) misst = false;
            frames = 0;
            zuruecksetzen();
            return;
        }
        if (!an || frames < MESSE) return;

        // Weiter messen, damit man auch beim Zusehen ein Gefuehl bekommt.
        if (frames >= MESSE * 20) {
            frames = 0;
            zuruecksetzen();
        }
    }

    private static void zuruecksetzen() {
        SUMME.clear();
        ANZAHL.clear();
        offen.clear();
        fpsSumme = 0;
        fpsProben = 0;
    }

    /** Anzeige umschalten. */
    public static boolean toggle() {
        an = !an;
        if (!an) {
            zuruecksetzen();
            frames = 0;
            misst = false;
        }
        return an;
    }

    public static boolean sichtbar() {
        return an;
    }

    /** Bericht auf die Platte schreiben. */
    public static void anfordern() {
        speichern = true;
        misst = true;
    }

    /** Tastendruck im Overlay abfragen. */
    public static boolean tastatur(int keyCode, int scanCode, int modifiers) {
        if (!an) return false;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            anfordern();
            return true;
        }
        return false;
    }

    private static double durchschnitt(String ort) {
        long s = SUMME.getOrDefault(ort, 0L);
        int n = ANZAHL.getOrDefault(ort, 0);
        if (n == 0) return 0;
        return s / (double) n / 1_000_000.0;   // Nanosekunden -> Millisekunden
    }

    /** Uebersicht aufzeichnen. Wird am Ende des HUD gerufen. */
    public static void zeichnen(DrawContext ctx, MinecraftClient mc) {
        if (!an) return;
        var tr = mc.textRenderer;
        var t = Ui.theme();

        List<String> namen = new ArrayList<>(SUMME.keySet());
        namen.sort(Comparator.comparingDouble(Profiler::durchschnitt).reversed());

        int h = 14 + namen.size() * 10 + 40;
        int x = 4, y = 4;
        Ui.fill(ctx, x, y, x + 168, y + h, 0xE60A0A0A);
        Ui.outline(ctx, x, y, 168, h, Ui.withAlpha(t.accent, 0.5f));

        double fps = fpsProben == 0 ? 0 : fpsSumme / fpsProben;
        ctx.drawText(tr, "SATURN PROFILER", x + 5, y + 5, t.accent, false);
        ctx.drawText(tr, "Bilder gemessen: " + frames, x + 5, y + 16, 0xFFCCCCCC, false);
        ctx.drawText(tr, "FPS: " + String.format(java.util.Locale.ROOT, "%.0f", fps),
                x + 5, y + 26, 0xFFCCCCCC, false);
        int ly = y + 38;
        for (String n : namen) {
            if (ly > y + h - 8) break;
            double ms = durchschnitt(n);
            int c = ms > 2.0 ? 0xFFFF6B6B : ms > 0.5 ? 0xFFFFD24A : 0xFF9E9E9E;
            ctx.drawText(tr, n, x + 5, ly, c, false);
            String wert = String.format(java.util.Locale.ROOT, "%.3f ms", ms);
            ctx.drawText(tr, wert, x + 163 - tr.getWidth(wert), ly, c, false);
            ly += 10;
        }
    }

    private static void schreibe(MinecraftClient mc) {
        List<String> namen = new ArrayList<>(SUMME.keySet());
        namen.sort(Comparator.comparingDouble(Profiler::durchschnitt).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("Saturn-Profil\n");
        sb.append("Bilder: ").append(frames).append('\n');
        double fps = fpsProben == 0 ? 0 : fpsSumme / fpsProben;
        sb.append(String.format(java.util.Locale.ROOT, "FPS im Mittel: %.1f%n", fps));
        sb.append("Alle Zeiten in Millisekunden pro Aufruf\n\n");

        double summe = 0;
        for (String n : namen) {
            double ms = durchschnitt(n);
            summe += ms;
            sb.append(String.format(java.util.Locale.ROOT, "%-24s %8.3f ms  (%d Aufrufe)%n",
                    n, ms, ANZAHL.getOrDefault(n, 0)));
        }
        sb.append(String.format(java.util.Locale.ROOT, "%nSumme gemessen: %.3f ms%n", summe));

        try {
            // In den Spielordner schreiben, nicht in das Arbeitsverzeichnis:
            // da landet die Datei sonst je nach Startmethode an einem
            // unvorhersehbaren Ort.
            Path basis = mc == null ? Path.of(".").toAbsolutePath() : mc.runDirectory.toPath();
            Path p = basis.resolve("saturn-profile.txt");
            Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
            System.err.println("[Saturn] Profil geschrieben: " + p);
        } catch (IOException ex) {
            System.err.println("[Saturn] Profil konnte nicht geschrieben werden: " + ex);
        }
    }
}