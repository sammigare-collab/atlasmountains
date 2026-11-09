package com.atlasmountains.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Applies "name=value" overrides to a MountainSettings by field name, so the diagnostic tool
 * and the mod can be tuned without recompiling. Unknown names are an error, not a silent no-op.
 */
public final class SettingsOverrides {
    private SettingsOverrides() {}

    public static boolean isOverride(String arg) {
        return arg.indexOf('=') > 0;
    }

    public static void apply(MountainSettings s, String arg) {
        int eq = arg.indexOf('=');
        String name = arg.substring(0, eq), value = arg.substring(eq + 1);
        try {
            Field f = MountainSettings.class.getField(name);
            if (Modifier.isStatic(f.getModifiers())) throw new IllegalArgumentException(name);
            Class<?> t = f.getType();
            if (t == double.class) f.setDouble(s, Double.parseDouble(value));
            else if (t == int.class) f.setInt(s, Integer.parseInt(value));
            else if (t == boolean.class) f.setBoolean(s, Boolean.parseBoolean(value));
            else if (t.isEnum()) f.set(s, enumValue(t, value));
            else throw new IllegalArgumentException("unsupported type for " + name);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException("unknown setting: " + name);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String value) {
        return Enum.valueOf((Class) type, value.toUpperCase(Locale.ROOT));
    }

    public static String describe(MountainSettings s) {
        StringBuilder sb = new StringBuilder();
        for (Field f : MountainSettings.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                sb.append(String.format(Locale.UK, "%s=%s ", f.getName(), f.get(s)));
            } catch (IllegalAccessException ignored) { }
        }
        return sb.toString();
    }
}
