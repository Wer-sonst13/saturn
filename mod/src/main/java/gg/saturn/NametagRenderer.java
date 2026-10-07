package gg.saturn;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * Zeichnet die eigenen Nametags über anderen Spielern.
 * Wird aus dem normalen Renderer entfernt (NametagMixin) und hier neu gezeichnet,
 * damit Hintergrund, Farbe und Grösse einstellbar sind.
 */
public final class NametagRenderer {

    private NametagRenderer() {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = Module.get("nametags");
        if (m == null || !m.enabled) return;
        if (!m.flag("enabled", true)) return;
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        ClientWorld world = mc.world;
        float scale = (float) m.num("scale", 1.0);
        boolean background = m.flag("background", true);
        int color = m.color("color", 0xFFFFFFFF);
        boolean hideSelf = m.flag("hideSelf", true);

        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // Sichtbare Lebewesen einsammeln, nach Entfernung sortiert (hinten zuerst)
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(64.0),
                e -> e != mc.player && e.isAlive() && e != mc.getCameraEntity());
        targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)));

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        float cosYaw = (float) Math.cos(-mc.gameRenderer.getCamera().getYaw() * Math.PI / 180.0);
        float sinYaw = (float) Math.sin(-mc.gameRenderer.getCamera().getYaw() * Math.PI / 180.0);
        float cosPitch = (float) Math.cos(-mc.gameRenderer.getCamera().getPitch() * Math.PI / 180.0);
        float sinPitch = (float) Math.sin(-mc.gameRenderer.getCamera().getPitch() * Math.PI / 180.0);

        for (Entity e : targets) {
            if (e instanceof net.minecraft.entity.player.PlayerEntity pe && hideSelf && pe == mc.player) continue;

            Vec3d rel = e.getPos().add(0, e.getEyeHeight(e.getPose()) + 0.3, 0).subtract(cam);
            // in den Kameraraum drehen
            float x = (float) (rel.x * cosYaw - rel.z * sinYaw);
            float z = (float) (rel.x * sinYaw + rel.z * cosYaw);
            float y = (float) (rel.y * cosPitch - z * sinPitch);
            z = (float) (rel.y * sinPitch + z * cosPitch);
            if (z <= 0.05f) continue;   // hinter der Kamera

            double dist = Math.sqrt(rel.x * rel.x + rel.y * rel.y + rel.z * rel.z);
            if (dist > 64.0) continue;

            int sx = (int) (w / 2.0 + x / z * (w / 2.0));
            int sy = (int) (h / 2.0 - y / z * (h / 2.0));
            if (sx < -80 || sx > w + 80 || sy < -20 || sy > h + 20) continue;

            String name = displayName(e);
            int tw = tr.getWidth(name);
            float s = Math.max(0.5f, Math.min(2f, scale));
            int tws = Math.round(tw * s);
            int ths = Math.round(9 * s);
            int x0 = sx - tws / 2;
            int y0 = sy - ths;

            if (background) {
                ctx.fill(x0 - 2, y0 - 1, x0 + tws + 2, y0 + ths + 1, 0x80000000);
            }
            // Entfernungs-Abblendung
            float fade = (float) Math.max(0.35, 1.0 - dist / 64.0);
            int c = Ui.withAlpha(color, fade);

            var ms = ctx.getMatrices();
            ms.pushMatrix();
            ms.translate(x0, y0);
            ms.scale(s, s);
            ctx.drawText(tr, name, 0, 0, c, true);
            ms.popMatrix();
        }
    }

    // Die Reflection wird einmal aufgeloest statt pro Spieler und pro Bild.
//
// Vorher stand getDeclaredMethod() + setAccessible() + invoke() in der
// Schleife, also bei 20 Spielern 20-mal in jedem einzelnen Bild. Die
// Methodensuche ist der teure Teil davon, invoke() ist nochmal deutlich
// langsamer als ein direkter Aufruf.
private static final java.lang.reflect.Method GET_ENTRY = lePlayerEntry();
    private static final java.lang.reflect.Method GET_PROFILE_NAME =
            ermittle(GameProfile.class, "getName");

    private static java.lang.reflect.Method lePlayerEntry() {
        try {
            var m = AbstractClientPlayerEntity.class.getDeclaredMethod("getPlayerListEntry");
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.reflect.Method ermittle(Class<?> c, String name) {
        try {
            return c.getMethod(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String displayName(Entity e) {
        if (e instanceof AbstractClientPlayerEntity acpe && GET_ENTRY != null) {
            // getPlayerListEntry() ist protected -> ueber Reflection, haelt auch Aenderungen aus
            try {
                Object ple = GET_ENTRY.invoke(acpe);
                if (ple instanceof PlayerListEntry entry) {
                    String name = nameOf(entry);
                    Team team = entry.getScoreboardTeam();
                    if (team != null) return team.getPrefix().getString() + name + team.getSuffix().getString();
                    return name;
                }
            } catch (Throwable ignored) {
            }
        }
        return e.getName().getString();
    }

    /** Der Name aus dem Profil. */
    private static String nameOf(PlayerListEntry ple) {
        // Auch hier war die Suche pro Aufruf. GET_PROFILE_NAME ist einmal gelöst.
        if (GET_PROFILE_NAME != null) {
            try {
                Object profile = ple.getProfile();
                if (profile == null) return "?";
                Object n = GET_PROFILE_NAME.invoke(profile);
                if (n != null) return n.toString();
            } catch (Throwable ignored) {
            }
        }
        return "?";
    }
}