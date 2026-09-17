package cartographer.geology.rock;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SelectiveChunkVisitStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RockStreamingSessionTest {
    private static final RockIdentity GRANITE = new RockIdentity(1, "game:rock-granite", "game", "granite");
    private static final RockIdentity SHALE = new RockIdentity(2, "game:rock-shale", "game", "shale");
    private static final RockCatalog CATALOG = RockCatalog.from(Map.of(
            1, new BlockInfo(1, GRANITE.code()),
            2, new BlockInfo(2, SHALE.code())
    ));

    @Test
    void reducesVerticalChunksIndependentOfCompletionOrder() {
        SelectiveChunkVisit lower = decoded(0, 0, 0, 10, 1);
        SelectiveChunkVisit upper = decoded(0, 1, 0, 8, 2);
        RockMap first = mapWith(List.of(lower, upper));
        RockMap second = mapWith(List.of(upper, lower));
        assertEquals(first.columns(), second.columns());
        assertEquals(RockColumnState.OBSERVED, first.stateAt(0, 0));
        assertEquals(40, first.rockYAt(0, 0).orElseThrow());
        assertEquals(SHALE.code(), first.sampleAt(0, 0).orElseThrow().rock().orElseThrow().code());
    }

    @Test
    void completeLogicalInputMatchesLegacyOracleForTwoVerticalChunks() {
        ParsedChunk lower = chunk(0, 0, 0, 10, 1);
        ParsedChunk upper = chunk(0, 1, 0, 8, 2);
        RockMap legacy = new RockColumnScanner().scan(
                List.of(lower, upper), CATALOG, new WorldPosition(0, 0, 0), 1, 0, 64,
                RockChunkCoverage.fromParsedChunks(List.of(lower, upper))
        );
        RockMap streaming = mapWith(List.of(
                SelectiveChunkVisit.decoded(position(0, 1, 0), upper),
                SelectiveChunkVisit.decoded(position(0, 0, 0), lower)
        ));
        assertEquals(RockLegacyOracle.snapshot(legacy), RockLegacyOracle.snapshot(streaming));
    }

    @Test
    void unknownAboveCandidateInvalidatesLowerCandidateButUnknownBelowDoesNot() {
        RockStreamingSession lowerOnly = session();
        lowerOnly.accept(decoded(0, 0, 0, 10, 1));
        lowerOnly.accept(SelectiveChunkVisit.missing(position(0, 1, 0)));
        assertEquals(RockColumnState.UNAVAILABLE, lowerOnly.finish().stateAt(0, 0));

        RockStreamingSession upperOnly = session();
        upperOnly.accept(decoded(0, 1, 0, 8, 2));
        upperOnly.accept(SelectiveChunkVisit.missing(position(0, 0, 0)));
        assertEquals(RockColumnState.OBSERVED, upperOnly.finish().stateAt(0, 0));
    }

    @Test
    void zeroVisitsFinalizeEveryCircleCellAsUnavailable() {
        RockMap map = session().finish();
        assertEquals(0, map.observedCount());
        assertEquals(0, map.noRockCount());
        assertEquals(5, map.unavailableCount());
        assertEquals(RockColumnState.UNAVAILABLE, map.stateAt(0, 0));
    }

    @Test
    void paletteRejectionIsAvailableAndDuplicateStatusesAreRejected() {
        RockStreamingSession session = session();
        session.accept(SelectiveChunkVisit.paletteRejected(position(0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> session.accept(SelectiveChunkVisit.paletteRejected(position(0, 0, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> session.accept(SelectiveChunkVisit.missing(position(0, 0, 0))));

        RockStreamingSession knownEmpty = session();
        for (int chunkX = -1; chunkX <= 0; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
                for (int chunkY = 0; chunkY <= 1; chunkY++) {
                    knownEmpty.accept(SelectiveChunkVisit.paletteRejected(
                            position(chunkX, chunkY, chunkZ)));
                }
            }
        }
        assertEquals(5, knownEmpty.finish().noRockCount());

        RockStreamingSession knownUpper = session();
        knownUpper.accept(decoded(0, 0, 0, 10, 1));
        knownUpper.accept(SelectiveChunkVisit.paletteRejected(position(0, 1, 0)));
        assertEquals(RockColumnState.OBSERVED, knownUpper.finish().stateAt(0, 0));
    }

    @Test
    void everyTerminalStatusRejectsSamePositionAndCrossStatusDuplicates() {
        for (SelectiveChunkVisitStatus status : SelectiveChunkVisitStatus.values()) {
            RockStreamingSession session = session();
            session.accept(visitFor(status, position(0, 0, 0)));
            assertThrows(IllegalArgumentException.class,
                    () -> session.accept(visitFor(status, position(0, 0, 0))));
        }
        RockStreamingSession crossStatus = session();
        crossStatus.accept(SelectiveChunkVisit.failed(position(0, 0, 0), "decode failure"));
        assertThrows(IllegalArgumentException.class,
                () -> crossStatus.accept(decoded(0, 0, 0, 10, 1)));
    }

    @Test
    void rejectsWrongDimensionAndOutOfRangeChunkPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(new SelectiveChunkVisit(
                        new cartographer.model.ChunkPosition(0, 0, 0, 1),
                        SelectiveChunkVisitStatus.MISSING, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(1, 0, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(0, 2, 0))));
    }

    @Test
    void lifecycleAndModeContractsAreExplicit() {
        RockStreamingSession session = session();
        assertThrows(NullPointerException.class, () -> session.accept(null));
        session.finish();
        assertThrows(IllegalStateException.class, session::finish);
        assertThrows(IllegalStateException.class,
                () -> session.accept(SelectiveChunkVisit.missing(position(0, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> RockStreamingSession.open(
                new WorldPosition(0, 0, 0), 1, 0, 64, 0, RockMapMode.AT_Y, CATALOG));
    }

    @Test
    void sessionHasNoDecodedChunkOrBoxedCoverageStateFields() throws NoSuchFieldException {
        for (Field field : RockStreamingSession.class.getDeclaredFields()) {
            assertEquals(false, ParsedChunk.class.isAssignableFrom(field.getType()));
            assertEquals(false, Map.class.isAssignableFrom(field.getType()));
            assertEquals(false, List.class.isAssignableFrom(field.getType()));
        }
        assertEquals(long[].class, RockStreamingSession.class.getDeclaredField("terminalSeenWords").getType());
        assertEquals(long[].class, RockStreamingSession.class.getDeclaredField("availableWords").getType());
    }

    private RockStreamingSession session() {
        return RockStreamingSession.open(new WorldPosition(0, 0, 0), 1, 0, 64, 0, CATALOG);
    }

    private SelectiveChunkVisit decoded(int chunkX, int chunkY, int chunkZ, int localY, int blockId) {
        ParsedChunk chunk = chunk(chunkX, chunkY, chunkZ, localY, blockId);
        return SelectiveChunkVisit.decoded(position(chunkX, chunkY, chunkZ), chunk);
    }

    private ParsedChunk chunk(int chunkX, int chunkY, int chunkZ, int localY, int blockId) {
        int[] blocks = new int[32 * 32 * 32];
        blocks[(localY * 32 + 0) * 32] = blockId;
        return new ParsedChunk(new ChunkCoordinate(chunkX, chunkY, chunkZ), chunkY * 32,
                32, 32, 32, blocks);
    }

    private SelectiveChunkVisit visitFor(SelectiveChunkVisitStatus status,
                                         cartographer.model.ChunkPosition position) {
        return switch (status) {
            case DECODED -> SelectiveChunkVisit.decoded(
                    position, chunk(position.x(), position.y(), position.z(), 10, 1));
            case PALETTE_REJECTED -> SelectiveChunkVisit.paletteRejected(position);
            case MISSING -> SelectiveChunkVisit.missing(position);
            case FAILED -> SelectiveChunkVisit.failed(position, "decode failure");
        };
    }

    private RockMap mapWith(List<SelectiveChunkVisit> visits) {
        RockStreamingSession session = session();
        for (SelectiveChunkVisit visit : visits) session.accept(visit);
        return session.finish();
    }

    private cartographer.model.ChunkPosition position(int x, int y, int z) {
        return new cartographer.model.ChunkPosition(x, y, z, 0);
    }
}
