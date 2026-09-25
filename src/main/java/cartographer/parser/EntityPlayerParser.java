package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldPosition;
import cartographer.binary.DotNetBinaryReader;

import java.util.Locale;

public final class EntityPlayerParser {

    public ParseResult<WorldPosition> parse(
            byte[] entityPlayerSerialized
    ) {
        if (entityPlayerSerialized == null
                || entityPlayerSerialized.length == 0) {
            return ParseResult.failure(
                    "EntityPlayerSerialized is empty"
            );
        }

        DotNetBinaryReader reader =
                new DotNetBinaryReader(entityPlayerSerialized);

        try {
            String firstString = reader.readDotNetString();

            String entityClass;
            String gameVersion;

            if (looksLikeEntityPlayerClass(firstString)) {
                entityClass = firstString;
                gameVersion = reader.readDotNetString();
            } else if (looksLikeGameVersion(firstString)) {
                /*
                 * Some serialization paths may give us the
                 * Entity.ToBytes payload directly without the
                 * class-registry wrapper.
                 */
                entityClass = "EntityPlayer";
                gameVersion = firstString;
            } else {
                throw new IllegalStateException(
                        "Expected EntityPlayer header or game version, got: "
                                + firstString
                );
            }

            long entityId = reader.readInt64LE();

            /*
             * Entity.ToBytes():
             *
             * version
             * EntityId
             * WatchedAttributes
             * Pos
             */
            TreeAttributeSkipper.skip(reader);

            int positionOffset = reader.position();

            double x = reader.readDoubleLE();
            double internalY = reader.readDoubleLE();
            double z = reader.readDoubleLE();

            validatePosition(
                    x,
                    internalY,
                    z,
                    positionOffset,
                    entityClass,
                    gameVersion,
                    entityId
            );

            return ParseResult.success(
                    new WorldPosition(
                            x,
                            internalY,
                            z
                    )
            );

        } catch (RuntimeException exception) {
            return ParseResult.failure(
                    "Cannot parse EntityPlayerSerialized at byte "
                            + reader.position()
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    private boolean looksLikeEntityPlayerClass(
            String value
    ) {
        return value != null
                && value.toLowerCase(Locale.ROOT)
                .contains("entityplayer");
    }

    private boolean looksLikeGameVersion(
            String value
    ) {
        return value != null
                && value.matches("\\d+\\.\\d+.*");
    }

    private void validatePosition(
            double x,
            double y,
            double z,
            int offset,
            String entityClass,
            String version,
            long entityId
    ) {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) {
            throw new IllegalStateException(
                    "EntityPos contains NaN or infinity at byte "
                            + offset
            );
        }

        if (Math.abs(x) > 100_000_000
                || Math.abs(y) > 100_000_000
                || Math.abs(z) > 100_000_000) {
            throw new IllegalStateException(
                    "Implausible EntityPos: "
                            + "X=" + x
                            + ", Y=" + y
                            + ", Z=" + z
                            + ", class=" + entityClass
                            + ", version=" + version
                            + ", entityId=" + entityId
            );
        }
    }
}