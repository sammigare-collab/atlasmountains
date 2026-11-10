package com.atlasmountains.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * tools/build_datapack.py repeats some heights from MountainSettings in the JSON it writes. This
 * fails when one side is changed without the other.
 */
class SettingsSyncTest {

    private static String script() throws Exception {
        for (Path p : new Path[] {Path.of("../tools/build_datapack.py"), Path.of("tools/build_datapack.py")}) {
            if (Files.exists(p)) return Files.readString(p);
        }
        return fail("tools/build_datapack.py not found from " + Path.of("").toAbsolutePath());
    }

    private static double constant(String src, String name) {
        Matcher m = Pattern.compile("(?m)^" + name + "\\s*=\\s*([-0-9.]+)").matcher(src);
        assertTrue(m.find(), name + " not found in build_datapack.py");
        return Double.parseDouble(m.group(1));
    }

    @Test
    void datapackGeneratorMatchesMountainSettings() throws Exception {
        String py = script();
        MountainSettings s = new MountainSettings();
        assertEquals(s.valleyFloorHeight, constant(py, "VALLEY_FLOOR"), "VALLEY_FLOOR vs valleyFloorHeight");
        assertEquals(s.summitHeight, constant(py, "SUMMIT"), "SUMMIT vs summitHeight");
        assertEquals(s.rockFraction, constant(py, "ROCK_FRACTION"), "ROCK_FRACTION vs rockFraction");
        assertEquals(s.valleyFloorHeight - 1, constant(py, "SEA_LEVEL"), "the lake surface (floor - 1) must be sea level");
    }
}
