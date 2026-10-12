package gg.saturn;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Zeigt in Discord an, dass der Client laeuft.
 *
 * Grundlage ist ein Test gegen das laufende Discord: die Pipe
 * \\.\pipe\discord-ipc-0 antwortet auf einen Handshake mit "READY"
 * und nennt das angemeldete Konto. Damit braucht es keine fremde
 * Bibliothek - reines Java ueber RandomAccessFile genuegt.
 *
 * Rahmenformat von Discord:
 *   4 Byte Opcode (klein), 4 Byte Laenge (klein), dann JSON.
 *   0 = Handshake, 1 = Frame, 2 = Close, 3 = Senden, 4 = Empfangen.
 *
 * Wichtig: Das Senden blockiert, sobald niemand mehr liest. Darum
 * laeuft ein eigener Leser-Thread, der Antworten wegwirft. Ohne ihn
 * haengt sich das Spiel beim Wechsel des Bildschirms auf.
 */
public final class DiscordPresence {

    /** Die Discord-Anwendung. Name und Logo kommen von dort. */
    private static final String APP_ID = "1559280467155615794";

    /**
     * Schluessel des Bildes aus den Rich-Presence-Assets.
     *
     * Discord nimmt hier den Dateinamen ohne Endung. Hochgeladen wurde
     * saturn-client-512.png, also ist der Schluessel saturn-client-512.
     */
    private static final String ASSET = "saturn-client-512";

    private DiscordPresence() {}

    /**
     * Schreibt jeden Schritt nach stderr. Der Launcher liest stderr mit und
     * zeigt es im Log-Bereich - dort ist auch die Ausgabe der Mods zu sehen.
     *
     * An: false  -> true, wenn wieder gesucht wird.
     */
    private static final boolean DEBUG = true;

    private static void log(String text) {
        if (DEBUG) System.err.println("[SaturnDiscord] " + text);
    }

    private static RandomAccessFile pipe;
    private static Thread leser;
    private static final Object SCHREIBSPERRE = new Object();
    private static boolean aktiv = true;
    private static String gesetzt = "";

    /** Verbindung aufbauen. Mehrfach aufrufen ist unkritisch. */
    public static void verbinden() {
        if (!aktiv || pipe != null || leser != null) return;
        for (int i = 0; i < 3; i++) {
            String pfad = "\\\\.\\pipe\\discord-ipc-" + i;
            try {
                pipe = new RandomAccessFile(pfad, "rw");
            } catch (IOException e) {
                continue;
            }
            if (handshake()) {
                log("verbunden mit " + pfad + ", Discord hat READY gesendet");
                leserStarten();
                return;
            }
            log(pfad + ": kein READY bekommen");
            close();
        }
    }

    /** Der Handshake nennt die Anwendung und die Protokollfassung. */
    private static boolean handshake() {
        try {
            rahmenSenden(0, "{\"v\":1,\"client_id\":\"" + APP_ID + "\"}");
            // Antwort abwarten - READY bestaetigt die Annahme.
            long ende = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < ende) {
                byte[] antwort = rahmenLesen(300);
                if (antwort == null) continue;
                String s = new String(antwort, StandardCharsets.UTF_8);
                if (s.contains("READY")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void leserStarten() {
        leser = new Thread(() -> {
            while (pipe != null && aktiv) {
                try {
                    byte[] h = rahmenLesen(200);
                    // Antworten werden nicht gebraucht; wegwerfen reicht.
                } catch (Exception e) {
                    if (!aktiv) return;
                }
            }
        }, "Saturn-Discord-Leser");
        leser.setDaemon(true);
        leser.start();
    }

    /**
     * Anzeige setzen.
     *
     * @param anzeige  grosse Zeile, z. B. "Idling" oder der Servername
     * @param details  kleine Zeile, z. B. die Minecraft-Version
     */
    public static void setzen(String anzeige, String details) {
        if (!aktiv) return;
        if (pipe == null) {
            log("keine Verbindung, setzen() wird derzeit nicht ausgefuehrt");
            return;
        }
        String neuer = anzeige + "|" + details;
        if (neuer.equals(gesetzt)) return;   // nichts Neues -> nichts senden
        gesetzt = neuer;

        StringBuilder b = new StringBuilder();
        b.append("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":")
         .append(pid())
         .append(",\"activity\":{");
        boolean erster = true;
        if (anzeige != null && !anzeige.isEmpty()) {
            b.append("\"state\":\"").append(json(anzeige)).append("\"");
            erster = false;
        }
        if (details != null && !details.isEmpty()) {
            if (!erster) b.append(",");
            b.append("\"details\":\"").append(json(details)).append("\"");
        }
        b.append(",\"timestamps\":{\"start\":").append(System.currentTimeMillis() / 1000L)
         .append("}")

         // Ohne dieses Feld zeigt Discord die Anzeige gar nicht erst an.
         // Der Schluessel ist der Dateiname des hochgeladenen Assets, ohne
         // Endung - hier "saturn-client-512".
         .append(",\"assets\":{")
         .append("\"large_image\":\"").append(ASSET).append("\"")
         .append(",\"large_text\":\"Saturn Client\"")
         .append("}")

         .append("}},\"nonce\":null}}");

        try {
            log("sende: " + b);
            rahmenSenden(1, b.toString());
        } catch (IOException e) {
            log("Senden fehlgeschlagen: " + e + " - Verbindung wird neu aufgebaut");
            close();
        }
    }

    /** Verbindung schliessen, damit Discord nichts mehr anzeigt. */
    public static void beenden() {
        aktiv = false;
        close();
    }

    private static void close() {
        RandomAccessFile p = pipe;
        pipe = null;
        leser = null;
        gesetzt = "";
        if (p != null) {
            try {
                // Opcode 2 = Close
                ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                bb.putInt(2);
                bb.putInt(0);
                p.write(bb.array());
            } catch (IOException ignored) {
            }
            try {
                p.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void rahmenSenden(int opcode, String json) throws IOException {
        byte[] nutzlast = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8 + nutzlast.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(opcode);
        bb.putInt(nutzlast.length);
        bb.put(nutzlast);
        synchronized (SCHREIBSPERRE) {
            if (pipe == null) throw new IOException("keine Verbindung");
            pipe.seek(0);
            pipe.write(bb.array());
            pipe.getChannel().force(false);
        }
    }

    /** Liest einen Rahmen. Timeout 0 heisst: sofort zurueck, falls nichts da. */
    private static byte[] rahmenLesen(int timeoutMs) {
        try {
            if (pipe == null) return null;
            long ende = System.currentTimeMillis() + timeoutMs;
            byte[] kopf = new byte[8];
            int gelesen = 0;
            while (gelesen < 8) {
                if (System.currentTimeMillis() > ende) return null;
                pipe.seek(gelesen);
                int n = pipe.read(kopf, gelesen, 8 - gelesen);
                if (n <= 0) {
                    Thread.sleep(10);
                    continue;
                }
                gelesen += n;
            }
            ByteBuffer hb = ByteBuffer.wrap(kopf).order(ByteOrder.LITTLE_ENDIAN);
            int opcode = hb.getInt();
            int len = hb.getInt();
            if (opcode == 2 || len <= 0 || len > 65536) return null;
            byte[] daten = new byte[len];
            int d = 0;
            while (d < len) {
                pipe.seek(8 + d);
                int n = pipe.read(daten, d, len - d);
                if (n <= 0) {
                    if (System.currentTimeMillis() > ende) return null;
                    Thread.sleep(10);
                    continue;
                }
                d += n;
            }
            return daten;
        } catch (Exception e) {
            return null;
        }
    }

    private static long pid() {
        return ProcessHandle.current().pid();
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}