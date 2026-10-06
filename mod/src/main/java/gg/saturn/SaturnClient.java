package gg.saturn;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Einstiegspunkt des Saturn-Clients.
 * Registriert die Tasten, zeichnet das HUD und hält die Module aktuell.
 */
public class SaturnClient {

    public static final String MOD_ID = "saturn";

    public static KeyBinding menuKey;
    public static KeyBinding editorKey;
    public static KeyBinding chatUtilsKey;
    public static KeyBinding hudToggleKey;

    // für das Keystrokes-Modul
    public static KeyBinding keyForward, keyLeft, keyBack, keyRight, keyJump;

    private static boolean menuIsOpen = false;
    private static int saveCounter = 0;

    private static boolean module(String id) {
        Module m = Module.get(id);
        return m != null && m.enabled;
    }

    public static boolean moduleOn(String id) {
        return module(id);
    }

    public static boolean menuOpen() {
        return menuIsOpen;
    }

    public static void init() {
        ConfigStore.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saturn.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.saturn"));
        editorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saturn.editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, "category.saturn"));
        chatUtilsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saturn.chatutils", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, "category.saturn"));
        hudToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saturn.hudtoggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "category.saturn"));

        // `MinecraftClient.options` ist an dieser Stelle noch null: Fabric ruft die
        // Client-Entrypoints aus dem Konstruktor von MinecraftClient heraus auf
        // (Hooks.startClient), lange bevor die Optionen angelegt sind. Deshalb
        // kommen die Vanilla-Tasten erst bei CLIENT_STARTED - dort ist der
        // Client fertig aufgebaut, aber der erste Tick steht noch aus.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            GameOptions o = client.options;
            keyForward = o.forwardKey;
            keyLeft = o.leftKey;
            keyBack = o.backKey;
            keyRight = o.rightKey;
            keyJump = o.jumpKey;
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> HudRenderer.renderAll(ctx, MinecraftClient.getInstance()));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) {
                menuIsOpen = true;
                mc.setScreen(new SaturnMenuScreen(mc.currentScreen));
            }
            while (editorKey.wasPressed()) {
                mc.setScreen(new HudEditorScreen(mc.currentScreen));
            }
            while (chatUtilsKey.wasPressed()) {
                mc.setScreen(new ChatUtilsScreen(mc.currentScreen));
            }
            while (hudToggleKey.wasPressed()) {
                mc.options.hudHidden = !mc.options.hudHidden;
            }

            if (mc.currentScreen instanceof SaturnScreen || mc.currentScreen instanceof ModMenuScreen) {
                menuIsOpen = true;
            } else if (menuIsOpen) {
                menuIsOpen = false;
            }

            if (mc.player != null) {
                Effects.tick(mc);
                Trackers.decayCombo();
            }

            if (++saveCounter >= 200) {   // alle 10 Sekunden sichern
                saveCounter = 0;
                ConfigStore.save();
            }
        });
    }

    /** Formatiert eine Dauer als "1h 23m 45s". */
    public static String formatDuration(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (h > 0 || m > 0) sb.append(m).append("m ");
        sb.append(sec).append('s');
        return sb.toString().trim();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(java.util.Locale.ROOT, "%.2f %s", v, u[i]);
    }
}