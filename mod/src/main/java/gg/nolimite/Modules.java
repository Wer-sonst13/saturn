package gg.nolimite;

import static gg.nolimite.Module.register;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alle Module des NoLimite-Clients.
 * Reihenfolge = Reihenfolge im Menü. Jedes Modul lässt sich ein-/ausschalten,
 * die HUD-Module zusätzlich im HUD-Editor verschieben und skalieren.
 */
public final class Modules {

    public static final String[] THEMES = {"BLUE", "CYAN", "PURPLE", "GREEN", "ORANGE", "RED", "LIGHT", "DARK"};

    private Modules() {}

    public static void init() {
        // ---------------- RENDER ----------------
        register(new Module("fullbright", "Fullbright",
                "Removes darkness and improves visibility in all situations.",
                "RENDER", "sun", false, false, true)
                .add(Setting.num("level", "Brightness Level", 1.0, 0.1, 1.0, 0.05)));

        register(new Module("brightness", "Brightness",
                "A global screen brightness that never goes dark.",
                "RENDER", "sun", false, false, true)
                .add(Setting.num("level", "Brightness", 1.0, 0.1, 1.0, 0.05)));

        register(new Module("nofog", "Nofog",
                "Removes fog for a clearer and more detailed view.",
                "RENDER", "nofog", false, false, true)
                .add(Setting.num("distance", "Fog Distance", 512.0, 64.0, 1024.0, 16.0)));

        register(new Module("saturation", "Color Saturation",
                "Change the color saturation of your game and menus.",
                "RENDER", "droplet", false, false, true)
                .add(Setting.num("level", "Saturation", 1.0, 0.0, 1.0, 0.05))
                .add(Setting.bool("menus", "Also In Menus", true)));

        register(new Module("clearbg", "Clear Background",
                "Removes the background blur and darkening behind menus.",
                "RENDER", "square", false, false, false));

        register(new Module("nametags", "Nametags",
                "Customize and enhance player name visibility.",
                "RENDER", "pencil", false, false, true)
                .add(Setting.bool("enabled", "Show Nametags", true))
                .add(Setting.bool("background", "Background", true))
                .add(Setting.color("color", "Nametag Color", 0xFFFFFFFF))
                .add(Setting.num("scale", "Nametag Scale", 1.0, 0.5, 2.0, 0.05))
                .add(Setting.bool("hideSelf", "Hide Own Name", true)));

        register(new Module("fov", "FOV Changer",
                "Customize your field of view without changing the game.",
                "RENDER", "arrows", false, false, true)
                .add(Setting.num("value", "FOV", 70.0, 30.0, 150.0, 1.0))
                .add(Setting.bool("slider", "Show FOV Slider", false)));

        register(new Module("oldanim", "Old Animations",
                "Brings back the classic item swing and attack animations.",
                "RENDER", "sword", false, false, true)
                .add(Setting.bool("swing", "Classic Swing", true))
                .add(Setting.bool("attack", "Classic Attack Indicator", true)));

        register(new Module("weather", "Weather Changer",
                "Override the displayed weather locally without touching the world.",
                "RENDER", "cloud", false, false, true)
                .add(Setting.choice("mode", "Weather", "OFF", "OFF", "RAIN", "THUNDER", "CLEAR"))
                .add(Setting.bool("sound", "Weather Sound", true)));

        register(new Module("time", "Time Changer",
                "Allows you to configure which time of day you see.",
                "RENDER", "clock", false, false, true)
                .add(Setting.choice("mode", "Time Of Day", "OFF", "OFF", "DAY", "NIGHT", "NOON", "MIDNIGHT"))
                .add(Setting.bool("daylight", "Disable Daylight Cycle", false)));

        register(new Module("titles", "Titles",
                "Custom positioning and scaling of Minecraft titles and subtitles.",
                "RENDER", "tr", false, false, true)
                .add(Setting.num("x", "X Offset", 0, -400, 400, 1))
                .add(Setting.num("y", "Y Offset", 0, -400, 400, 1))
                .add(Setting.num("scale", "Scale", 1.0, 0.5, 3.0, 0.05))
                .add(Setting.bool("showSubtitle", "Show Subtitle", true)));

        register(new Module("shinypots", "Shiny Pots",
                "Adds an enchantment glint to potions in your inventory.",
                "RENDER", "flask", false, false, true)
                .add(Setting.bool("splash", "Splash Particles", true)));

        register(new Module("highlight", "Item Highlighter",
                "Highlight listed items to quickly find them in your inventory.",
                "RENDER", "pencil", false, false, true)
                .add(Setting.text("items", "Items (comma separated)", "diamond, emerald, netherite_ingot"))
                .add(Setting.color("color", "Highlight Color", 0xFF55FF55))
                .add(Setting.bool("glow", "Glow Effect", false)));

        register(new Module("freelook", "Freelook",
                "Look around freely without changing your movement.",
                "RENDER", "eye", false, false, true)
                .add(Setting.bool("thirdperson", "Third Person", false)));

        // ---------------- HUD ----------------
        register(new Module("fps", "FPS",
                "Shows your current frames per second in the corner.",
                "HUD", "fps", true, false, false)
                .add(Setting.text("format", "Format", "{fps} FPS"))
                .add(Setting.color("color", "Color", 0xFFFFFFFF)));

        register(new Module("coords", "Coordinates",
                "Displays your XYZ position in the world.",
                "HUD", "coord", true, false, true)
                .add(Setting.bool("biome", "Show Biome", true))
                .add(Setting.bool("chunk", "Show Chunk", false))
                .add(Setting.bool("block", "Look At Block", true))
                .add(Setting.color("color", "Color", 0xFFFFFFFF)));

        register(new Module("lightlevel", "Light Level",
                "Shows the light level of nearby blocks so you always know.",
                "HUD", "torch", true, true, true)
                .add(Setting.bool("blockLight", "Block Light", true))
                .add(Setting.bool("skyLight", "Sky Light", false)));

        register(new Module("armorstatus", "Armorstatus",
                "Displays the durability and status of your equipment.",
                "HUD", "armor", true, false, true)
                .add(Setting.bool("percent", "Show Percent", true))
                .add(Setting.color("okColor", "High Color", 0xFF55FF55))
                .add(Setting.color("lowColor", "Low Color", 0xFFFF5555)));

        register(new Module("armorhud", "Musas Armor HUD",
                "Shows the durability of your equipped armor by icon.",
                "HUD", "armor2", true, true, true)
                .add(Setting.num("scale", "Icon Scale", 1.0, 0.5, 2.0, 0.05))
                .add(Setting.bool("percent", "Show Percent", false)));

        register(new Module("potionstatus", "Potion Status",
                "Shows active potion effects and their remaining time.",
                "HUD", "flask", true, false, true)
                .add(Setting.bool("icons", "Show Icons", false))
                .add(Setting.bool("time", "Show Time", true)));

        register(new Module("itemcounter", "Item Counter",
                "Count the amount of listed items in your inventory.",
                "HUD", "cube", true, false, true)
                .add(Setting.text("items", "Items (comma separated)", "diamond, emerald, gold, iron"))
                .add(Setting.bool("showEmpty", "Show Zero Counts", false)));

        register(new Module("cps", "CPS",
                "Shows your clicks per second (CPS).",
                "HUD", "mouse", true, false, true)
                .add(Setting.bool("avg", "Show Average", true)));

        register(new Module("combo", "Combo Counter",
                "Chain your hits and count the combo you land.",
                "HUD", "combo", true, false, true)
                .add(Setting.num("timeout", "Combo Timeout (s)", 2.0, 0.5, 5.0, 0.1)));

        register(new Module("clock", "Clock",
                "Display the current real-world time on your HUD.",
                "HUD", "clock", true, false, true)
                .add(Setting.bool("seconds", "Show Seconds", true))
                .add(Setting.bool("date", "Show Date", false)));

        register(new Module("speed", "Speedometer",
                "Display your in-game speed in m/s.",
                "HUD", "speedo", true, false, true)
                .add(Setting.num("divisor", "Divisor", 1.0, 1.0, 40.0, 0.5)));

        register(new Module("keystrokes", "Keystrokes",
                "Shows your WASD and mouse buttons while moving.",
                "HUD", "keys", true, false, true)
                .add(Setting.bool("jump", "Show Jump Key", true))
                .add(Setting.num("scale", "Scale", 1.0, 0.5, 2.0, 0.05)));

        register(new Module("helditem", "Held Item",
                "Shows the name and count of the item in your hand.",
                "HUD", "cube", true, false, true)
                .add(Setting.bool("count", "Show Count", true))
                .add(Setting.bool("name", "Show Name", true)));

        register(new Module("biome", "Biome & World",
                "Shows the current biome and dimension you are in.",
                "HUD", "world", true, false, true)
                .add(Setting.bool("dimension", "Show Dimension", true)));

        register(new Module("serverip", "Server IP",
                "Shows the address of the server you are playing on.",
                "HUD", "globe", true, false, false));

        register(new Module("scoreboard", "Scoreboard",
                "Customize and enhance the in-game scoreboard.",
                "HUD", "board", true, false, true)
                .add(Setting.num("scale", "HUD Scale", 1.0, 0.3, 2.5, 0.05))
                .add(Setting.choice("background", "Background", "VANILLA", "VANILLA", "TRANSPARENT", "BLUR", "CUSTOM"))
                .add(Setting.color("bgColor", "Background Color", 0xB0000000))
                .add(Setting.bool("corners", "Corners", false))
                .add(Setting.integer("cornerSize", "Corner Size", 4, 0, 16, 1))
                .add(Setting.bool("dynamicPadding", "Dynamic Padding", true))
                .add(Setting.integer("width", "Width", 0, 0, 500, 1))
                .add(Setting.integer("height", "Height", 0, 0, 500, 1))
                .add(Setting.bool("showNumbers", "Show Numbers", true))
                .add(Setting.bool("fontShadow", "Font Shadow", true))
                .add(Setting.bool("hideScoreboard", "Hide Scoreboard", false)));

        // ---------------- CHAT ----------------
        register(new Module("splitchat", "Split Chat",
                "Split the chat into windows and tabs, filtered by player.",
                "CHAT", "chat", false, true, true)
                .add(Setting.bool("tabs", "Show Tabs", true))
                .add(Setting.bool("windowed", "Windowed Style", false))
                .add(Setting.bool("pauseOnScroll", "Pause On Scroll", true)));

        register(new Module("smoothchat", "Smooth Chat",
                "Slides new chat messages in instead of letting them jump.",
                "CHAT", "chat", false, true, true)
                .add(Setting.num("speed", "Animation Speed", 1.0, 0.2, 3.0, 0.1)));

        register(new Module("chatutils", "Chat Utils",
                "Copy chat messages with a click, search and filter the chat.",
                "CHAT", "search", false, true, true)
                .add(Setting.bool("timestamps", "Timestamps", true))
                .add(Setting.bool("wrap", "Word Wrap", true))
                .add(Setting.integer("history", "Saved Messages", 500, 50, 2000, 50)));

        register(new Module("timestamps", "Timestamps",
                "Prefix every chat message with the current time.",
                "CHAT", "clock", false, false, true)
                .add(Setting.choice("format", "Format", "[HH:mm]", "[HH:mm]", "[HH:mm:ss]", "HH:mm")));

        // ---------------- MISC ----------------
        register(new Module("theme", "Theme",
                "Switch between light and dark color themes.",
                "MISC", "palette", false, false, true)
                .add(Setting.choices("name", "Theme", "BLUE", THEMES)));

        register(new Module("guiscale", "GUI Scale",
                "Enable separate scaling for the inventory and menus.",
                "MISC", "grid", false, false, true)
                .add(Setting.num("scale", "Menu Scale", 1.0, 0.6, 2.0, 0.05)));

        register(new Module("icon", "Icon",
                "Customize the icons displayed in the NoLimite menu.",
                "MISC", "bolt", false, false, true)
                .add(Setting.choice("style", "Icon Style", "LINE", "LINE", "FILLED", "MINIMAL")));

        register(new Module("profiles", "Profiles",
                "Save and switch between different mod and setting sets.",
                "MISC", "user", false, false, true)
                .add(Setting.text("profile", "Active Profile", "Default")));
    }

    /** Anzahl aller Module - für die Kopfzeile im Menü. */
    /** Die Kategorienamen in der Reihenfolge, in der sie im Menü erscheinen. */
    public static java.util.List<String> categories() {
        return Module.categories();
    }

    public static int count() {
        return Module.all().size();
    }

    /** Alle Module, die im Spiel als HUD-Element sichtbar platziert werden können. */
    public static java.util.List<Module> hudModules() {
        java.util.List<Module> out = new java.util.ArrayList<>();
        for (Module m : Module.all()) if (m.hud) out.add(m);
        return out;
    }

    /** Standard-Positionen der HUD-Elemente (erster Start ohne Config). */
    public static Map<String, double[]> defaultPositions() {
        Map<String, double[]> p = new LinkedHashMap<>();
        double y = 4;
        for (Module m : Module.all()) {
            if (!m.hud) continue;
            if (m.id.equals("scoreboard")) {
                p.put(m.id, new double[]{0, 0});
            } else if (m.id.equals("fps") || m.id.equals("coords") || m.id.equals("serverip")) {
                p.put(m.id, new double[]{4, y});
                y += 12;
            } else if (m.id.equals("armorstatus") || m.id.equals("armorhud") || m.id.equals("potionstatus")
                    || m.id.equals("itemcounter")) {
                p.put(m.id, new double[]{-200, y});
                y += 14;
            } else {
                p.put(m.id, new double[]{4, y});
                y += 12;
            }
        }
        return p;
    }
}
