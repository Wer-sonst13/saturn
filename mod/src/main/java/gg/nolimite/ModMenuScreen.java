package gg.nolimite;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * MOD MENU - Einstieg wie im NoRisk-Client (5 Symbole mittig).
 */
public class ModMenuScreen extends Screen {

    private static final int N = 5;
    private final int[] hit = new int[N];
    private String[] labels;

    public ModMenuScreen() {
        super(Text.literal("MOD MENU"));
        labels = new String[]{"CAMERA", "SPEICHERN", "MODULE", "CHAT UTILS", "HUD"};
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x90000000);
        super.render(ctx, mx, my, delta);

        int cx = width / 2;
        int cy = height / 2;

        ctx.drawCenteredTextWithShadow(textRenderer, "NOLIMITE", cx, cy - 60, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "CLIENT", cx + 90, cy - 60, 0xB0B0B0);

        int bw = 44, bh = 34, gap = 10;
        int totalW = N * bw + (N - 1) * gap;
        int x = cx - totalW / 2;
        int y = cy + 10;

        for (int i = 0; i < N; i++) {
            hit[i * 4] = x;
            hit[i * 4 + 1] = y;
            hit[i * 4 + 2] = bw;
            hit[i * 4 + 3] = bh;
            boolean hover = Ui.inside(mx, my, x, y, bw, bh);
            ctx.fill(x, y, x + bw, y + bh, hover ? 0xFF2B2B2B : 0xFF141414);
            ctx.drawBorder(x, y, bw, bh, hover ? 0xFFFFFFFF : 0xFF4A4A4A);
            Ui.textCentered(ctx, textRenderer, String.valueOf(i + 1), x + bw / 2, y + 12, 0xFFFFFFFF);
            if (hover) Ui.textCentered(ctx, textRenderer, labels[i], cx, y + bh + 4, 0xFFAAAAAA);
            x += bw + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < N; i++) {
            if (Ui.inside(mx, my, hit[i * 4], hit[i * 4 + 1], hit[i * 4 + 2], hit[i * 4 + 3])) {
                MinecraftClient mc = MinecraftClient.getInstance();
                switch (i) {
                    case 0 -> mc.setScreen(new ModMenuScreen());
                    case 1 -> ConfigStore.save();
                    case 2 -> mc.setScreen(new NoLimiteMenuScreen(this));
                    case 3 -> mc.setScreen(new ChatUtilsScreen(this));
                    case 4 -> mc.setScreen(new HudEditorScreen(this));
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}