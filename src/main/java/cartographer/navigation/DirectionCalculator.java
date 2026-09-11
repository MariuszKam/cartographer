package cartographer.navigation;

import cartographer.model.HomeLocation;
import cartographer.model.WorldPosition;

public class DirectionCalculator {
    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public Direction fromPlayerToHome(WorldPosition player, HomeLocation home) {
        double dx = home.x() - player.x();
        double dz = home.z() - player.z();
        double distance = Math.hypot(dx, dz);
        double bearing = bearing(dx, dz);
        return new Direction(distance, compass(bearing), bearing);
    }

    private double bearing(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, dz));
        return (degrees + 360.0) % 360.0;
    }

    private String compass(double bearing) {
        int index = (int) Math.floor((bearing + 22.5) / 45.0) % 8;
        return COMPASS[index];
    }
}
