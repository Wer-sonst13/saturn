package gg.saturn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein Feature des Saturn-Clients: Name, Beschreibung, Kategorie, Icon,
 * an/aus und (falls es im Spiel sichtbar ist) Position + Größe im HUD.
 */
public class Module {
    public final String id;
    public final String name;
    public final String desc;
    public final String category;
    public final String icon;
    public final List<Setting> settings = new ArrayList<>();
    public final boolean hud;          // hat eine Position im Spiel-HUD
    public final boolean isNew;        // "NEW" - Badge im Menü
    public final boolean hasMenu;      // hat ein eigenes Einstellungsfenster

    public boolean enabled;
    public double x = 4, y = 4;
    public double scale = 1.0;

    public Module(String id, String name, String desc, String category, String icon,
                  boolean hud, boolean isNew, boolean hasMenu) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.icon = icon;
        this.hud = hud;
        this.isNew = isNew;
        this.hasMenu = hasMenu;
    }

    public Setting setting(String key) {
        for (Setting s : settings) if (s.key.equals(key)) return s;
        return null;
    }

    public boolean flag(String key, boolean fallback) {
        Setting s = setting(key);
        return s == null ? fallback : s.asBool();
    }

    public double num(String key, double fallback) {
        Setting s = setting(key);
        return s == null ? fallback : s.asDouble();
    }

    public int numInt(String key, int fallback) {
        Setting s = setting(key);
        return s == null ? fallback : s.asInt();
    }

    public String str(String key, String fallback) {
        Setting s = setting(key);
        return s == null ? fallback : s.asString();
    }

    public String choice(String key, String fallback) {
        Setting s = setting(key);
        return s == null ? fallback : s.asString();
    }

    public int color(String key, int fallback) {
        Setting s = setting(key);
        return s == null ? fallback : s.asColor();
    }

    public Module add(Setting... ss) {
        Collections.addAll(settings, ss);
        return this;
    }

    // Registry -------------------------------------------------------

    private static final Map<String, Module> ALL = new LinkedHashMap<>();

    public static void register(Module m) {
        ALL.put(m.id, m);
    }

    public static Module get(String id) {
        return ALL.get(id);
    }

    public static List<Module> all() {
        return new ArrayList<>(ALL.values());
    }

    public static List<String> categories() {
        List<String> out = new ArrayList<>();
        for (Module m : ALL.values()) if (!out.contains(m.category)) out.add(m.category);
        return out;
    }
}