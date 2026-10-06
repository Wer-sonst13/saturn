package gg.saturn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Laufende Werte, die im HUD angezeigt werden (CPS, Combo, Licht, Chatverlauf ...). */
public final class Trackers {

    // ---------------- CPS ----------------
    private static final Deque<Long> CLICKS = new ArrayDeque<>();
    private static final Deque<Long> ATTACKS = new ArrayDeque<>();
    private static int lastCps, lastAvgCps;
    private static int combo;
    private static long lastHit = -1L;
    private static long sessionStart = System.currentTimeMillis();

    private Trackers() {}

    /** Wird vom Mouse-Mixin aufgerufen. */
    public static void click() {
        long now = System.currentTimeMillis();
        CLICKS.addLast(now);
        while (!CLICKS.isEmpty() && now - CLICKS.peekFirst() > 1000) CLICKS.removeFirst();
        recomputeCps(now);
    }

    /** Wird vom Combat-Mixin aufgerufen. */
    public static void hit() {
        long now = System.currentTimeMillis();
        ATTACKS.addLast(now);
        while (!ATTACKS.isEmpty() && now - ATTACKS.peekFirst() > 1000) ATTACKS.removeFirst();
        recomputeCps(now);
    }

    private static void recomputeCps(long now) {
        lastCps = (int) CLICKS.stream().filter(t -> now - t <= 1000).count();
        // Durchschnitt der letzten Sekunden
        long sum = 0;
        for (long t : ATTACKS) if (now - t <= 1000) sum++;
        lastAvgCps = (int) Math.round(sum / 1.0);
    }

    public static int cps() {
        return lastCps;
    }

    /** Angriffe (Schläge), nicht Mausklicks. */
    public static int aps() {
        return lastAvgCps;
    }

    public static void registerComboHit() {
        long now = System.currentTimeMillis();
        double timeout = 2000;
        Module cm = Module.get("combo");
        if (cm != null) timeout = cm.num("timeout", 2.0) * 1000;
        combo = (lastHit > 0 && now - lastHit <= timeout) ? combo + 1 : 1;
        lastHit = now;
    }

    public static int combo() {
        // Combo verfällt, wenn zu lange nicht getroffen wurde
        double timeout = 2000;
        Module cm = Module.get("combo");
        if (cm != null) timeout = cm.num("timeout", 2.0) * 1000;
        if (lastHit > 0 && System.currentTimeMillis() - lastHit > timeout) return 0;
        return combo;
    }

    /** 1.20 s Pause nach einem Treffer - ohne sie würde jeder Klick zählen. */
    public static void decayCombo() {
        lastHit = -1L;
        combo = 0;
    }

    // ---------------- Welt ----------------

    public static int blockLight(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return 0;
        BlockPos pos = p.getBlockPos();
        return mc.world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos);
    }

    public static int skyLight(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return 0;
        BlockPos pos = p.getBlockPos();
        return mc.world.getLightLevel(net.minecraft.world.LightType.SKY, pos);
    }

    public static String biome(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return "?";
        var entry = mc.world.getBiome(mc.player.getBlockPos());
        if (entry == null) return "?";
        return entry.getIdAsString();     // z. B. "minecraft:plains"
    }

    public static String dimension(MinecraftClient mc) {
        if (mc.world == null) return "?";
        return pretty(mc.world.getRegistryKey().getValue().getPath()).toUpperCase(Locale.ROOT);
    }

    /** "minecraft:oak_savanna" -> "Oak Savanna" */
    public static String pretty(String path) {
        if (path == null) return "?";
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Blick-Richtung als Block (z. B. MINECRAFT:STONE). */
    public static String lookedBlock(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return "?";
        var hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return "-";
        var pos = ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
        var state = mc.world.getBlockState(pos);
        return String.valueOf(state.getBlock());
    }

    public static double speed(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return 0;
        double div = 1.0;
        Module sm = Module.get("speed");
        if (sm != null) div = Math.max(0.1, sm.num("divisor", 1.0));
        return Math.sqrt(p.getVelocity().x * p.getVelocity().x + p.getVelocity().z * p.getVelocity().z) / div;
    }

    public static long sessionTime() {
        return System.currentTimeMillis() - sessionStart;
    }

    public static String clock(boolean seconds, boolean date) {
        DateTimeFormatter f = seconds
                ? DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
                : DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
        String s = LocalTime.now().format(f);
        if (date) {
            s = s + "  " + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT));
        }
        return s;
    }

    /** Anzahl eines Gegenstands im Inventar (inkl. Rüstung/Handschuh). */
    public static int countItem(MinecraftClient mc, String id) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return 0;
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && key(s).equals(id)) n += s.getCount();
        }
        return n;
    }

    /** Der reine Item-Name, z. B. diamond (ohne minecraft: und ohne Namen). */
    public static String key(ItemStack stack) {
        if (stack.isEmpty()) return "";
        String n = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (n.startsWith("minecraft:")) n = n.substring(10);
        n = n.replace(' ', '_');
        return n;
    }

    public static boolean keystroke(KeyBinding k) {
        return k != null && k.isPressed();
    }

    // ---------------- Chatverlauf (für Chat Utils) ----------------

    public static final class ChatLine {
        public final String time;
        public final String text;
        ChatLine(String time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    private static final Deque<ChatLine> CHAT = new ArrayDeque<>();

    public static void addChat(String text) {
        String t = text;
        // vorhandenen Zeitstempel entfernen, damit er nicht doppelt auftaucht
        int b = t.indexOf(']');
        if (t.startsWith("[") && b > 0 && b < 12) t = t.substring(b + 1).trim();
        int max = 500;
        Module m = Module.get("chatutils");
        if (m != null) max = m.numInt("history", 500);
        CHAT.addLast(new ChatLine(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)), t));
        while (CHAT.size() > max) CHAT.removeFirst();
    }

    public static List<ChatLine> chat() {
        return new ArrayList<>(CHAT);
    }

    public static void clearChat() {
        CHAT.clear();
    }

    public static List<String> activeEffects(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (StatusEffectInstance e : mc.player.getActiveStatusEffects().values()) {
            out.add(e.getTranslationKey());
        }
        return out;
    }

    public static World world() {
        return MinecraftClient.getInstance().world;
    }

    public static Text text(String s) {
        return Text.literal(s);
    }
}