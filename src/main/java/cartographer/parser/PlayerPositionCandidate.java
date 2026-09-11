package cartographer.parser;

import cartographer.model.WorldPosition;

public record PlayerPositionCandidate(int offset, String encoding, WorldPosition position, double score) {
}
