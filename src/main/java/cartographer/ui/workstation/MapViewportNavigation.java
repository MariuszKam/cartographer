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
        return clamp(desiredOffset / scrollableLength, 0.0, 1.0);
    }

    static double interpolateScrollValue(
            double minValue,
            double maxValue,
            double fraction
    ) {
        return minValue + clamp(fraction, 0.0, 1.0) * (maxValue - minValue);
    }

    private static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }
}
