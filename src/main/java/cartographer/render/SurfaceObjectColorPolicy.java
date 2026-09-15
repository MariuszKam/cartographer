package cartographer.render;

import java.awt.Color;
import java.util.Objects;

/** Deterministic color policy for logical observed surface-object resources. */
public final class SurfaceObjectColorPolicy {
    private SurfaceObjectColorPolicy() {
    }

    public static Color colorFor(String qualifiedResourceKey) {
        Objects.requireNonNull(qualifiedResourceKey, "qualified resource key is required");
        int hash = qualifiedResourceKey.hashCode();
        int mixed = hash ^ (hash >>> 16);
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        mixed ^= mixed >>> 16;

        float hue = (mixed & 0xffff) / 65535.0f;
        float saturation = 0.68f + ((mixed >>> 16) & 0xff) / 255.0f * 0.18f;
        float brightness = 0.78f + ((mixed >>> 24) & 0xff) / 255.0f * 0.16f;
        Color base = Color.getHSBColor(hue, saturation, brightness);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 220);
    }
}
