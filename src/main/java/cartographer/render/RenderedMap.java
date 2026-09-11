package cartographer.render;

import java.awt.image.BufferedImage;

public record RenderedMap(BufferedImage image, MapRenderReport report) {
}
