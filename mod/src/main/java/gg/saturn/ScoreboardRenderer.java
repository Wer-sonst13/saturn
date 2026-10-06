package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Zeichnet das Scoreboard selbst, damit Hintergrund, Ecken, Grösse,
 * Zahlen, Abstand und Position wirklich frei einstellbar sind.
 */
public final class ScoreboardRenderer {

    private ScoreboardRenderer() {}

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

        // Namen sammeln, Punktzahl holen, absteigend sortieren
        List<String> names = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (Object raw : board.getScoreboardEntries(objective)) {
            if (!(raw instanceof ScoreHolder holder)) continue;
            String name = holder.getNameForScoreboard();
            int score = 0;
            var rs = board.getScore(holder, objective);
            if (rs != null) score = rs.getScore();
            names.add(name);
            scores.add(score);
        }
        if (names.isEmpty()) return;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> -scores.get(i)));

        TextRenderer tr = mc.textRenderer;
        String title = objective.getDisplayName().getString();

        int pad = dynamic ? 2 : 1;
        int titleH = 9;
        int rowH = 9;

        int contentW = tr.getWidth(title);
        for (int i : order) contentW = Math.max(contentW, tr.getWidth(lineFor(board, names.get(i))));
        int maxNumW = 0;
        if (numbers) {
            for (int i : order) maxNumW = Math.max(maxNumW, tr.getWidth(String.valueOf(scores.get(i))));
            contentW += maxNumW + 4;
        }

        int w = forcedW > 0 ? forcedW : contentW + pad * 2;
        int bodyH = order.size() * rowH;
        int h = forcedH > 0 ? forcedH : titleH + bodyH + pad;

        // rechts verankert, vertikal mittig
        int right = scaledWidth + (int) mod.x;
        int top = mc.getWindow().getScaledHeight() / 2 + (int) mod.y - h / 2;
        int left = right - w;

        MatrixStack ms = ctx.getMatrices();
        ms.push();
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
        ctx.drawText(tr, title, pad, y, 0xFFFFFFFF, shadow);
        y += titleH;

        for (int i : order) {
            String name = names.get(i);
            int score = scores.get(i);
            ctx.drawText(tr, lineFor(board, name), pad, y, 0xFFFFFF, shadow);
            if (numbers) {
                String num = String.valueOf(score);
                ctx.drawText(tr, num, w - pad - tr.getWidth(num), y, 0xFF55FF55, shadow);
            }
            y += rowH;
        }

        ms.pop();
    }

    /** Team-Präfix + Name + Suffix. */
    private static String lineFor(Scoreboard board, String name) {
        Team team = board.getScoreHolderTeam(name);
        if (team == null) return name;
        return team.getPrefix().getString() + name + team.getSuffix().getString();
    }
}