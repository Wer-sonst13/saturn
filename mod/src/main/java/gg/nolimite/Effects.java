package gg.nolimite;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * Laufzeiteffekte der Module.
 * Hier steht der Zustand, den die Mixins abfragen, und das, was pro Tick
 * oder pro Bild wirklich geändert wird.
 */
public final class Effects {

    private Effects() {}

    // ---- Zustand für die Mixins ----
    public static boolean fullbrightOn;
    public static float fullbrightLevel = 1f;
    public static boolean fogOn;
    public static float fogDistance = 512f;
    public static boolean darknessForced;

    // Freelook
    public static boolean freelook;
    public static float freeYaw, freePitch;
    public static boolean freelookReady;

    // Zeit / Wetter
    public static boolean timeOn;
    public static long fixedTime = 6000L;
    public static boolean weatherOn;
    public static int weatherMode;      // 0 aus, 1 regen, 2 gewitter, 3 klar

    // Titel
    public static boolean titlesOn;

    // Nametags
    public static boolean nametagsOn;

    public static void tick(MinecraftClient mc) {
        Module m;

        // Fullbright / Helligkeit über die vorhandene Gamma-Option
        m = Module.get("fullbright");
        fullbrightOn = m != null && m.enabled;
        m = Module.get("brightness");
        boolean brightOn = m != null && m.enabled;
        double gamma = 0.0;
        if (fullbrightOn) gamma = 1.0;
        if (brightOn) gamma = Math.max(gamma, m.num("level", 1.0));
        setGamma(mc, gamma);
        // Gibt es keine Gamma-Option, hilft der Darkness-Faktor der Lightmap.
        darknessForced = fullbrightOn || brightOn;

        m = Module.get("nofog");
        fogOn = m != null && m.enabled;
        fogDistance = m == null ? 512f : (float) m.num("distance", 512.0);

        m = Module.get("time");
        timeOn = m != null && m.enabled;
        fixedTime = switch (m == null ? "OFF" : m.choice("mode", "OFF")) {
            case "DAY" -> 1000L;
            case "NIGHT" -> 13000L;
            case "NOON" -> 6000L;
            case "MIDNIGHT" -> 18000L;
            default -> 6000L;
        };

        m = Module.get("weather");
        weatherOn = m != null && m.enabled;
        weatherMode = switch (m == null ? "OFF" : m.choice("mode", "OFF")) {
            case "RAIN" -> 1;
            case "THUNDER" -> 2;
            case "CLEAR" -> 3;
            default -> 0;
        };
        if (weatherOn && mc.world != null) {
            boolean wantRain = weatherMode == 1 || weatherMode == 2;
            mc.world.setRainGradient(wantRain ? 1f : 0f);
        }

        m = Module.get("freelook");
        freelook = m != null && m.enabled;
        if (!freelook) freelookReady = false;

        titlesOn = isOn("titles");
        nametagsOn = isOn("nametags");
    }

    public static boolean isOn(String id) {
        Module m = Module.get(id);
        return m != null && m.enabled;
    }

    /** Setzt die Helligkeit (0 = dunkel, 1 = hell). */
    private static void setGamma(MinecraftClient mc, double level) {
        if (mc.options == null) return;
        try {
            var opt = ((gg.nolimite.mixin.GameOptionsAccessor) (Object) mc.options).nolimite$gamma();
            if (opt == null) return;
            Double cur = opt.getValue();
            if (cur != null && Math.abs(cur - level) < 0.001) return;
            opt.setValue(level);
        } catch (Throwable ignored) {
        }
    }

    /** Wird vom Freelook-Mixin benutzt: Blickrichtung für die freie Kamera. */
    public static void freelookTurn(double dYaw, double dPitch) {
        freeYaw = MathHelper.wrapDegrees((float) (freeYaw + dYaw * 2.0));
        freePitch = MathHelper.clamp((float) (freePitch + dPitch * 2.0), -90f, 90f);
    }

    public static void freelookSync(Entity entity) {
        if (freelookReady) return;
        freeYaw = entity.getYaw();
        freePitch = entity.getPitch();
        freelookReady = true;
    }

    // ---- Bild-Effekte (werden im HUD-Hook gezeichnet) ----

    /**
     * Sättigung / Helligkeit als Vollbild-Overlay.
     * In 1.21 ist Blending dauerhaft aktiv, deshalb reicht ein simples fill().
     */
    public static void renderOverlay(DrawContext ctx, MinecraftClient mc) {
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        if (w <= 0 || h <= 0) return;

        Module sat = Module.get("saturation");
        if (sat != null && sat.enabled) {
            float level = (float) Math.max(0, Math.min(1, sat.num("level", 1.0)));
            if (level < 1f) {
                // 0 % = grau hinblenden, 100 % = original
                int a = Math.round(255 * (1f - level) * 0.9f);
                ctx.fill(0, 0, w, h, (a << 24) | 0x9AA0A6);
            }
        }
    }
}