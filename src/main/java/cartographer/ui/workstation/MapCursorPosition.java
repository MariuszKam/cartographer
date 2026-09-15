package cartographer.ui.workstation;

public record MapCursorPosition(double absoluteX, double absoluteZ) {
    public MapCursorPosition {
        if (!Double.isFinite(absoluteX) || !Double.isFinite(absoluteZ)) {
            throw new IllegalArgumentException("cursor coordinates must be finite");
        }
    }
}
