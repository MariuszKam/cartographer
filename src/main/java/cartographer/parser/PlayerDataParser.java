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

        Optional<WorldPosition> binaryPosition = parsePlausibleDoubleTriple(payload);
        if (binaryPosition.isPresent()) {
            return ParseResult.success(binaryPosition.get());
        }

        return ParseResult.failure("could not locate a plausible X/Y/Z position in playerdata payload");
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
            return Optional.of(new WorldPosition(x, y, z));
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
                if (plausible(x, y, z)) {
                    return Optional.of(new WorldPosition(x, y, z));
                }
                values.clear();
            }
        }
        return Optional.empty();
    }

    private Optional<WorldPosition> parsePlausibleDoubleTriple(byte[] payload) {
        for (int offset = 0; offset <= payload.length - 24; offset++) {
            ByteBuffer buffer = ByteBuffer.wrap(payload, offset, 24).order(ByteOrder.LITTLE_ENDIAN);
            double x = buffer.getDouble();
            double y = buffer.getDouble();
            double z = buffer.getDouble();
            if (plausible(x, y, z)) {
                return Optional.of(new WorldPosition(x, y, z));
            }
        }
        return Optional.empty();
    }

    private boolean plausible(double x, double y, double z) {
        return finite(x) && finite(y) && finite(z)
                && Math.abs(x) < 100_000_000
                && y > -10_000
                && y < 10_000
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
