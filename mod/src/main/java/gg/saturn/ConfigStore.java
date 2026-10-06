package gg.saturn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Speichert an/aus, Position, Größe und alle Einstellungen der Module
 * in config/saturn.json - inklusive der Mod-Profile.
 */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ModuleProfile> PROFILES = new LinkedHashMap<>();
    private static String activeProfile = "Default";

    private ConfigStore() {}

    // ---------------- Mod-Profile ----------------

    /** Ein Profil merkt sich nur an/aus - Positionen bleiben pro Modul erhalten. */
    public static final class ModuleProfile {
        public String name = "Default";
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
    }

    public static List<String> profileNames() {
        List<String> out = new ArrayList<>(PROFILES.keySet());
        if (out.isEmpty()) out.add("Default");
        return out;
    }

    public static String activeProfile() {
        return activeProfile;
    }

    public static ModuleProfile profile(String name) {
        ModuleProfile p = PROFILES.get(name);
        if (p == null) {
            p = new ModuleProfile();
            p.name = name;
            for (Module m : Module.all()) p.enabled.put(m.id, m.enabled);
            PROFILES.put(name, p);
        }
        return p;
    }

    /** Speichert den aktuellen Zustand im gerade aktiven Profil. */
    public static void saveActiveProfile() {
        ModuleProfile p = profile(activeProfile);
        p.enabled.clear();
        for (Module m : Module.all()) p.enabled.put(m.id, m.enabled);
    }

    public static void activateProfile(String name) {
        profile(activeProfile);            // laufendes Profil sichern
        ModuleProfile p = profile(name);
        for (Module m : Module.all()) {
            Boolean on = p.enabled.get(m.id);
            if (on != null) m.enabled = on;
        }
        activeProfile = name;
        saveActiveProfile();
    }

    // ---------------- Laden / Speichern ----------------

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("saturn.json");
    }

    @SuppressWarnings("unchecked")
    public static void load() {
        Modules.init();

        // 1. Startwerte der Positionen setzen, damit neue Module sinnvoll platziert sind.
        Map<String, double[]> pos = Modules.defaultPositions();
        for (Module m : Module.all()) {
            double[] p = pos.get(m.id);
            if (p != null) {
                m.x = p[0];
                m.y = p[1];
            }
        }

        // 2. Standardprofil anlegen (alle Module aus).
        ModuleProfile def = new ModuleProfile();
        def.name = "Default";
        for (Module m : Module.all()) def.enabled.put(m.id, true);
        PROFILES.clear();
        PROFILES.put("Default", def);

        try {
            if (!Files.exists(file())) return;
            Map<String, Object> root;
            try (Reader r = Files.newBufferedReader(file())) {
                root = GSON.fromJson(r, new TypeToken<Map<String, Object>>() {}.getType());
            }
            if (root == null) return;

            // Module
            Map<String, Object> mods = asMap(root.get("modules"));
            for (Map.Entry<String, Object> e : mods.entrySet()) {
                Module m = Module.get(e.getKey());
                Map<String, Object> v = asMap(e.getValue());
                if (m == null || v == null) continue;
                if (v.get("on") instanceof Boolean b) m.enabled = b;
                if (v.get("x") instanceof Number n) m.x = n.doubleValue();
                if (v.get("y") instanceof Number n) m.y = n.doubleValue();
                if (v.get("scale") instanceof Number n) m.scale = Math.max(0.2, n.doubleValue());
            }

            // Einstellungen
            Map<String, Object> sets = asMap(root.get("settings"));
            for (Map.Entry<String, Object> e : sets.entrySet()) {
                Module m = Module.get(e.getKey());
                Map<String, Object> v = asMap(e.getValue());
                if (m == null || v == null) continue;
                for (Map.Entry<String, Object> s : v.entrySet()) {
                    for (Setting st : m.settings) {
                        if (st.key.equals(s.getKey())) st.set(s.getValue());
                    }
                }
            }

            // Profile
            Map<String, Object> profs = asMap(root.get("profiles"));
            if (!profs.isEmpty()) {
                Map<String, ModuleProfile> loaded = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : profs.entrySet()) {
                    Map<String, Object> v = asMap(e.getValue());
                    ModuleProfile p = new ModuleProfile();
                    p.name = e.getKey();
                    for (Map.Entry<String, Object> s : v.entrySet()) {
                        if (s.getValue() instanceof Boolean b) p.enabled.put(s.getKey(), b);
                    }
                    loaded.put(p.name, p);
                }
                PROFILES.clear();
                PROFILES.putAll(loaded);
                activeProfile = PROFILES.containsKey(activeProfile)
                        ? activeProfile : PROFILES.keySet().iterator().next();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        applyProfile(activeProfile);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    private static void applyProfile(String name) {
        ModuleProfile p = PROFILES.get(name);
        if (p == null) return;
        for (Module m : Module.all()) {
            Boolean on = p.enabled.get(m.id);
            if (on != null) m.enabled = on;
        }
    }

    public static void save() {
        saveActiveProfile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);

        Map<String, Object> mods = new LinkedHashMap<>();
        Map<String, Object> sets = new LinkedHashMap<>();
        for (Module m : Module.all()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("on", m.enabled);
            v.put("x", m.x);
            v.put("y", m.y);
            v.put("scale", m.scale);
            mods.put(m.id, v);

            Map<String, Object> s = new LinkedHashMap<>();
            for (Setting st : m.settings) s.put(st.key, st.value);
            sets.put(m.id, s);
        }
        root.put("modules", mods);
        root.put("settings", sets);

        Map<String, Object> profs = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleProfile> e : PROFILES.entrySet()) {
            profs.put(e.getKey(), new LinkedHashMap<>(e.getValue().enabled));
        }
        root.put("profiles", profs);

        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(root, w);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Setzt alle Module auf Standard zurück (Positionen + Werte). */
    public static void resetAll() {
        Map<String, double[]> pos = Modules.defaultPositions();
        for (Module m : Module.all()) {
            double[] p = pos.get(m.id);
            m.x = p == null ? 4 : p[0];
            m.y = p == null ? 4 : p[1];
            m.scale = 1.0;
            for (Setting s : m.settings) s.value = s.defaultValue();
        }
        ModuleProfile def = new ModuleProfile();
        def.name = "Default";
        for (Module m : Module.all()) def.enabled.put(m.id, true);
        PROFILES.clear();
        PROFILES.put("Default", def);
        activeProfile = "Default";
    }
}