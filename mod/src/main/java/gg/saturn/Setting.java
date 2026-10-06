package gg.saturn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Eine typisierte Einstellung eines Moduls.
 * Wird per Gson als {"key": {"key":..,"type":..,"value":..}} gespeichert.
 */
public class Setting {
    public enum Type { BOOL, INT, DOUBLE, TEXT, CHOICE, COLOR }

    public final String key;
    public final String label;
    public final Type type;
    public final double min, max, step;
    public final List<String> choices = new ArrayList<>();
    public Object value;

    private Setting(String key, String label, Type type, Object def,
                    double min, double max, double step, List<String> choices) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.value = def;
        this.min = min;
        this.max = max;
        this.step = step;
        if (choices != null) this.choices.addAll(choices);
    }

    // ---------- Bauhelfer ----------

    public static Setting bool(String key, String label, boolean def) {
        return new Setting(key, label, Type.BOOL, def, 0, 0, 0, null);
    }

    public static Setting integer(String key, String label, int def, int min, int max, int step) {
        return new Setting(key, label, Type.INT, def, min, max, step, null);
    }

    public static Setting num(String key, String label, double def, double min, double max, double step) {
        return new Setting(key, label, Type.DOUBLE, def, min, max, step, null);
    }

    public static Setting text(String key, String label, String def) {
        return new Setting(key, label, Type.TEXT, def, 0, 0, 0, null);
    }

    public static Setting color(String key, String label, int def) {
        return new Setting(key, label, Type.COLOR, def, 0, 0, 0, null);
    }

    public static Setting choice(String key, String label, String def, String... options) {
        return new Setting(key, label, Type.CHOICE, def, 0, 0, 0, Arrays.asList(options));
    }

    public static Setting choices(String key, String label, String def, String[] options) {
        return new Setting(key, label, Type.CHOICE, def, 0, 0, 0, Arrays.asList(options));
    }

    // ---------- Werte ----------

    public boolean asBool() {
        return value instanceof Boolean b && b;
    }

    public int asInt() {
        return value instanceof Number n ? n.intValue() : 0;
    }

    public double asDouble() {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    public String asString() {
        return value == null ? "" : value.toString();
    }

    public int asColor() {
        return value instanceof Number n ? n.intValue() : 0xFFFFFFFF;
    }

    /** Setzt den Wert und hält min/max ein. Gibt true zurück, falls sich etwas geändert hat. */
    public boolean set(Object v) {
        if (v == null) return false;
        switch (type) {
            case BOOL -> v = v instanceof Boolean b && b;
            case INT -> {
                if (!(v instanceof Number n)) return false;
                v = (int) clamp(n.doubleValue(), min, max);
            }
            case DOUBLE -> {
                if (!(v instanceof Number n)) return false;
                double d = clamp(n.doubleValue(), min, max);
                if (step > 0) d = Math.round(d / step) * step;
                v = d;
            }
            case COLOR -> {
                if (!(v instanceof Number n)) return false;
                v = n.intValue() | 0xFF000000;
            }
            case TEXT, CHOICE -> v = v.toString();
        }
        if (v.equals(this.value)) return false;
        this.value = v;
        return true;
    }

    public void reset() {
        this.value = switch (type) {
            case BOOL -> false;
            case INT, DOUBLE, COLOR -> 0;
            default -> "";
        };
    }

    public Object defaultValue() {
        return switch (type) {
            case BOOL -> Boolean.FALSE;
            case INT -> 0;
            case DOUBLE -> 0.0;
            case COLOR -> 0xFFFFFFFF;
            case CHOICE -> choices.isEmpty() ? "" : choices.get(0);
            default -> "";
        };
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}