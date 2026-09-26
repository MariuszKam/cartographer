package cartographer.ui.workstation;

final class MapViewportNavigation {
    private MapViewportNavigation() {
    }

    static double centeredScrollFraction(
            double contentMin,
            double contentLength,
            double viewportLength,
            double targetPosition
    ) {
        double scrollableLength = contentLength - viewportLength;
        if (scrollableLength <= 0.0) {
            return 0.5;
        }

        double desiredOffset = targetPosition - contentMin - viewportLength / 2.0;
        return clampUnit(desiredOffset / scrollableLength);
    }

    static double interpolateScrollValue(
            double minValue,
            double maxValue,
            double fraction
    ) {
        return minValue + clampUnit(fraction) * (maxValue - minValue);
    }

    private static double clampUnit(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }
}
