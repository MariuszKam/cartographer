package cartographer.ui.workstation;

import cartographer.render.MapViewportGeometry;
import java.util.Optional;

final class MapCursorMapping {
    private MapCursorMapping() {
    }

    static Optional<MapCursorPosition> toAbsoluteWorld(
            MapViewportGeometry geometry,
            double pointerX,
            double pointerY,
            double renderedMinX,
            double renderedMinY,
            double renderedWidth,
            double renderedHeight
    ) {
        if (geometry == null
                || !Double.isFinite(pointerX)
                || !Double.isFinite(pointerY)
                || !Double.isFinite(renderedMinX)
                || !Double.isFinite(renderedMinY)
                || !Double.isFinite(renderedWidth)
                || !Double.isFinite(renderedHeight)
                || renderedWidth <= 0.0
                || renderedHeight <= 0.0) {
            return Optional.empty();
        }

        double sourceX = (pointerX - renderedMinX)
                * geometry.imageWidth() / renderedWidth;
        double sourceY = (pointerY - renderedMinY)
                * geometry.imageHeight() / renderedHeight;
        if (!Double.isFinite(sourceX)
                || !Double.isFinite(sourceY)
                || !geometry.containsImagePoint(sourceX, sourceY)) {
            return Optional.empty();
        }

        double absoluteX = geometry.imageXToAbsoluteWorldX(sourceX);
        double absoluteZ = geometry.imageYToAbsoluteWorldZ(sourceY);
        if (!Double.isFinite(absoluteX) || !Double.isFinite(absoluteZ)) {
            return Optional.empty();
        }
        return Optional.of(new MapCursorPosition(absoluteX, absoluteZ));
    }
}
