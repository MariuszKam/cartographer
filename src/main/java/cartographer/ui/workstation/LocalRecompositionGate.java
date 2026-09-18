package cartographer.ui.workstation;

import cartographer.render.RenderLayer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generation gate for asynchronous local map recomposition.
 *
 * <p>A completion is accepted only while it is still the latest requested
 * recomposition for the same retained frame and layer selection.</p>
 */
public final class LocalRecompositionGate {
    private long generation;

    public Token begin(MapFrame frame, Set<RenderLayer> layers) {
        Objects.requireNonNull(frame, "frame is required");
        Objects.requireNonNull(layers, "layers are required");
        generation++;
        return new Token(generation, frame, Set.copyOf(layers));
    }

    public void invalidate() {
        generation++;
    }

    public boolean accepts(
            Token token,
            Optional<MapFrame> currentFrame,
            Set<RenderLayer> currentLayers
    ) {
        Objects.requireNonNull(token, "token is required");
        Objects.requireNonNull(currentFrame, "currentFrame is required");
        Objects.requireNonNull(currentLayers, "currentLayers are required");
        return token.generation() == generation
                && currentFrame.filter(token.frame()::equals).isPresent()
                && token.layers().equals(Set.copyOf(currentLayers));
    }

    public record Token(
            long generation,
            MapFrame frame,
            Set<RenderLayer> layers
    ) {
        public Token {
            Objects.requireNonNull(frame, "frame is required");
            layers = Set.copyOf(Objects.requireNonNull(layers, "layers are required"));
        }
    }
}
