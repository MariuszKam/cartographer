package cartographer.render;

public class TerrainPalette {
    public int unknown() {
        return 0xFF2B2B2B;
    }

    public int background() {
        return 0xFF111111;
    }

    public int water() {
        return 0xFF2D6FA3;
    }

    public int ground(int height) {
        int shade = Math.max(60, Math.min(180, 90 + height / 3));
        return 0xFF000000 | (shade / 2 << 16) | (shade << 8) | (shade / 3);
    }

    public int rock(int height) {
        int shade = Math.max(70, Math.min(210, 100 + height / 4));
        return 0xFF000000 | (shade << 16) | (shade << 8) | shade;
    }
}
