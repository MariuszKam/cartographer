package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldPosition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerDataParser {
    private static final Pattern TEXT_POSITION = Pattern.compile(
            "(?i)(?:pos(?:ition)?[xyz]|[xyz])[^-+0-9]{0,16}([-+]?[0-9]+(?:\\.[0-9]+)?)");
    private static final double AUTOMATIC_POSITION_SCORE_THRESHOLD = 10_000.0;

    public ParseResult<WorldPosition> parse(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return ParseResult.failure("playerdata payload is empty");
        }

        Optional<WorldPosition> protobufPosition = parseProtoFixed64Position(payload);
        if (protobufPosition.isPresent()) {
            return ParseResult.success(protobufPosition.get());
        }

        Optional<WorldPosition> textPosition = parseTextPosition(payload);
        if (textPosition.isPresent()) {
            return ParseResult.success(textPosition.get());
        }

        Optional<WorldPosition> binaryPosition = bestBinaryCandidate(payload);
        if (binaryPosition.isPresent()) {
            return ParseResult.success(binaryPosition.get());
        }

        return ParseResult.failure("could not locate a plausible X/Y/Z position in playerdata payload");
    }

    public List<PlayerPositionCandidate> findCandidates(byte[] payload, int limit) {
        List<PlayerPositionCandidate> candidates = new ArrayList<>();
        if (payload == null || payload.length == 0 || limit <= 0) {
            return candidates;
        }

        collectDoubleTriples(payload, candidates);
        collectFloatTriples(payload, candidates);
        candidates.sort((left, right) -> Double.compare(right.score(), left.score()));
        return candidates.size() <= limit ? candidates : List.copyOf(candidates.subList(0, limit));
    }

    private Optional<WorldPosition> parseProtoFixed64Position(byte[] payload) {
        Double x = null;
        Double y = null;
        Double z = null;

        int offset = 0;
        while (offset < payload.length) {
            Varint tag = readVarint(payload, offset);
            if (tag == null) {
                break;
            }
            offset = tag.nextOffset;
            int fieldNumber = (int) (tag.value >>> 3);
            int wireType = (int) (tag.value & 0x7);

            if (wireType == 1) {
                if (offset + Double.BYTES > payload.length) {
                    break;
                }
                double value = ByteBuffer.wrap(payload, offset, Double.BYTES).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                if (fieldNumber == 1) {
                    x = value;
                } else if (fieldNumber == 2) {
                    y = value;
                } else if (fieldNumber == 3) {
                    z = value;
                }
                offset += Double.BYTES;
            } else {
                int next = skipField(payload, offset, wireType);
                if (next < 0) {
                    break;
                }
                offset = next;
            }
        }

        if (x != null && y != null && z != null && plausible(x, y, z)) {
            WorldPosition position = new WorldPosition(x, y, z);
            if (score(position) > AUTOMATIC_POSITION_SCORE_THRESHOLD) {
                return Optional.of(position);
            }
        }
        return Optional.empty();
    }

    private Optional<WorldPosition> parseTextPosition(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);
        Matcher matcher = TEXT_POSITION.matcher(text);
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group(1)));
            if (values.size() == 3) {
                double x = values.get(0);
                double y = values.get(1);
                double z = values.get(2);
                WorldPosition position = new WorldPosition(x, y, z);
                if (plausible(x, y, z) && score(position) > AUTOMATIC_POSITION_SCORE_THRESHOLD) {
                    return Optional.of(position);
                }
                values.clear();
            }
        }
        return Optional.empty();
    }

    private Optional<WorldPosition> bestBinaryCandidate(byte[] payload) {
        return findCandidates(payload, 1).stream()
                .filter(candidate -> candidate.score() > 0.0)
                .filter(candidate -> candidate.score() > AUTOMATIC_POSITION_SCORE_THRESHOLD)
                .map(PlayerPositionCandidate::position)
                .findFirst();
    }

    private void collectDoubleTriples(byte[] payload, List<PlayerPositionCandidate> candidates) {
        for (int offset = 0; offset <= payload.length - 24; offset += Double.BYTES) {
            ByteBuffer buffer = ByteBuffer.wrap(payload, offset, 24).order(ByteOrder.LITTLE_ENDIAN);
            double x = buffer.getDouble();
            double y = buffer.getDouble();
            double z = buffer.getDouble();
            if (plausible(x, y, z)) {
                WorldPosition position = new WorldPosition(x, y, z);
                candidates.add(new PlayerPositionCandidate(offset, "double-le", position, score(position)));
            }
        }
    }

    private void collectFloatTriples(byte[] payload, List<PlayerPositionCandidate> candidates) {
        for (int offset = 0; offset <= payload.length - 12; offset += Float.BYTES) {
            ByteBuffer buffer = ByteBuffer.wrap(payload, offset, 12).order(ByteOrder.LITTLE_ENDIAN);
            double x = buffer.getFloat();
            double y = buffer.getFloat();
            double z = buffer.getFloat();
            if (plausible(x, y, z)) {
                WorldPosition position = new WorldPosition(x, y, z);
                candidates.add(new PlayerPositionCandidate(offset, "float-le", position, score(position)));
            }
        }
    }

    private double score(WorldPosition position) {
        double horizontalDistance = Math.hypot(position.x(), position.z());
        double score = 0.0;
        boolean hasVerticalAxis = position.y() >= 40.0 && position.y() <= 300.0;
        if (hasVerticalAxis) {
            score += 10_000.0;
        }
        boolean hasHorizontalAxes = Math.abs(position.x()) >= 100.0 && Math.abs(position.z()) >= 100.0;
        if (hasHorizontalAxes) {
            score += 5_000.0;
        }
        if (hasVerticalAxis && hasHorizontalAxes && horizontalDistance >= 1_000.0 && horizontalDistance <= 2_000_000.0) {
            score += 2_000_000.0 - horizontalDistance;
        }
        if (horizontalDistance < 64.0) {
            score -= 100_000.0;
        }
        if (Math.abs(position.x()) > 2_000_000.0 || Math.abs(position.z()) > 2_000_000.0) {
            score -= 500_000.0;
        }
        return score;
    }

    private boolean plausible(double x, double y, double z) {
        return finite(x) && finite(y) && finite(z)
                && !(x == 0.0 && y == 0.0 && z == 0.0)
                && Math.abs(x) < 100_000_000
                && y > -1_000
                && y < 2_000
                && Math.abs(z) < 100_000_000;
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private Varint readVarint(byte[] payload, int offset) {
        long value = 0;
        int shift = 0;
        for (int index = offset; index < payload.length && shift < 64; index++) {
            int current = payload[index] & 0xFF;
            value |= (long) (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return new Varint(value, index + 1);
            }
            shift += 7;
        }
        return null;
    }

    private int skipField(byte[] payload, int offset, int wireType) {
        return switch (wireType) {
            case 0 -> {
                Varint value = readVarint(payload, offset);
                yield value == null ? -1 : value.nextOffset;
            }
            case 2 -> {
                Varint length = readVarint(payload, offset);
                if (length == null || length.value < 0 || length.nextOffset + length.value > payload.length) {
                    yield -1;
                }
                yield (int) (length.nextOffset + length.value);
            }
            case 5 -> offset + Integer.BYTES <= payload.length ? offset + Integer.BYTES : -1;
            default -> -1;
        };
    }

    private record Varint(long value, int nextOffset) {
    }
}
