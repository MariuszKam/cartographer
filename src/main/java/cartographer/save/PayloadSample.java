package cartographer.save;

import cartographer.parser.PlayerPositionCandidate;

import java.util.List;

public record PayloadSample(
        int rowNumber,
        String columnName,
        int byteLength,
        String hexPreview,
        String textPreview,
        List<PlayerPositionCandidate> playerPositionCandidates) {
}
