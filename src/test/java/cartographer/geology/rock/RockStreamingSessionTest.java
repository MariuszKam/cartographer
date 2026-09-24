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
        assertEquals(RockMapTestOracle.snapshot(first), RockMapTestOracle.snapshot(second));
        assertEquals(RockColumnState.OBSERVED, first.stateAt(0, 0));
        assertEquals(40, first.rockYAt(0, 0).orElseThrow());
        assertEquals(SHALE.code(), first.sampleAt(0, 0).orElseThrow().rock().orElseThrow().code());
    }

    @Test
    void coverageStatesPreserveRockSemantics() {
        ChunkCoordinate lowerPosition = new ChunkCoordinate(0, 0, 0);
        ChunkCoordinate upperPosition = new ChunkCoordinate(0, 1, 0);
        ParsedChunk lower = RockStreamingFixtures.chunk(
                lowerPosition, RockStreamingFixtures.at(1, 10, 1, 1));
        ParsedChunk upper = RockStreamingFixtures.chunk(upperPosition);
        WorldPosition center = new WorldPosition(1, 0, 1);

        RockMap paletteAvailable = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.paletteRejected(position(upperPosition))),
                center, 0, 64);
        assertEquals(RockColumnState.OBSERVED, paletteAvailable.stateAt(1, 1));
        assertEquals(10, paletteAvailable.rockYAt(1, 1).orElseThrow());

        RockMap missingUpper = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.missing(position(upperPosition))),
                center, 0, 64);
        assertEquals(RockColumnState.UNAVAILABLE, missingUpper.stateAt(1, 1));

        RockMap failedUpper = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.failed(position(upperPosition), "decode failure")),
                center, 0, 64);
        assertEquals(RockColumnState.UNAVAILABLE, failedUpper.stateAt(1, 1));

        RockMap missingLower = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(upperPosition), upper),
                SelectiveChunkVisit.missing(position(lowerPosition))),
                center, 0, 64);
        assertEquals(RockColumnState.UNAVAILABLE, missingLower.stateAt(1, 1));

        ChunkCoordinate topPosition = new ChunkCoordinate(0, 2, 0);
        ParsedChunk top = RockStreamingFixtures.chunk(topPosition);
        RockMap missingMiddle = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.missing(position(upperPosition)),
                SelectiveChunkVisit.decoded(position(topPosition), top)),
                center, 0, 96);
        assertEquals(RockColumnState.UNAVAILABLE, missingMiddle.stateAt(1, 1));

        RockMap complete = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.decoded(position(upperPosition), upper)),
                center, 0, 64);
        assertEquals(RockColumnState.OBSERVED, complete.stateAt(1, 1));
        assertEquals(10, complete.rockYAt(1, 1).orElseThrow());

        RockMap knownEmpty = fixtureStreaming(List.of(
                SelectiveChunkVisit.paletteRejected(position(lowerPosition)),
                SelectiveChunkVisit.paletteRejected(position(upperPosition))),
                center, 0, 64);
        assertEquals(RockColumnState.NO_ROCK, knownEmpty.stateAt(1, 1));
    }

    @Test
    void preservesModdedOreAndPartialYSemantics() {
        WorldPosition center = new WorldPosition(1, 0, 1);
        ChunkCoordinate position = new ChunkCoordinate(0, 0, 0);
        ParsedChunk modded = RockStreamingFixtures.chunk(position,
                RockStreamingFixtures.at(1, 1, 1, 3));
        RockMap moddedMap = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(position), modded)),
                center, 0, 32);
        assertEquals("somemod:rock-gneiss", moddedMap.sampleAt(1, 1).orElseThrow()
                .rock().orElseThrow().code());

        ParsedChunk ore = RockStreamingFixtures.chunk(position,
                RockStreamingFixtures.at(1, 1, 1, 4));
        RockMap oreMap = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(position), ore)),
                center, 0, 32);
        assertEquals(RockColumnState.NO_ROCK, oreMap.stateAt(1, 1));

        ParsedChunk lower = RockStreamingFixtures.chunk(position,
                RockStreamingFixtures.at(1, 4, 1, 1),
                RockStreamingFixtures.at(1, 5, 1, 2));
        ChunkCoordinate upperPosition = new ChunkCoordinate(0, 1, 0);
        ParsedChunk upper = RockStreamingFixtures.chunk(upperPosition,
                RockStreamingFixtures.at(1, 1, 1, 2),
                RockStreamingFixtures.at(1, 2, 1, 1));
        RockMap partial = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(position), lower),
                SelectiveChunkVisit.decoded(position(upperPosition), upper)),
                center, 5, 34);
        assertEquals(33, partial.rockYAt(1, 1).orElseThrow());

        RockMap narrow = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(position), lower)),
                center, 5, 6);
        assertEquals(5, narrow.rockYAt(1, 1).orElseThrow());
    }

    @Test
    void preservesNegativeAndFractionalCoordinates() {
        ChunkCoordinate negativePosition = new ChunkCoordinate(-1, 0, -1);
        ParsedChunk negative = RockStreamingFixtures.chunk(negativePosition,
                RockStreamingFixtures.at(31, 1, 31, 3));
        RockMap negativeMap = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(negativePosition), negative)),
                new WorldPosition(-0.2, 0, -0.2), 0, 32);
        assertEquals(RockColumnState.OBSERVED, negativeMap.stateAt(-1, -1));
        assertEquals("somemod:rock-gneiss", negativeMap.sampleAt(-1, -1).orElseThrow()
                .rock().orElseThrow().code());

        ChunkCoordinate positivePosition = new ChunkCoordinate(0, 0, 0);
        ParsedChunk positive = RockStreamingFixtures.chunk(positivePosition,
                RockStreamingFixtures.at(10, 1, 10, 3));
        RockMap positiveMap = fixtureStreaming(List.of(
                SelectiveChunkVisit.decoded(position(positivePosition), positive)),
                new WorldPosition(10.8, 0, 10.8), 0, 32);
        assertEquals(RockColumnState.OBSERVED, positiveMap.stateAt(10, 10));
        assertEquals("somemod:rock-gneiss", positiveMap.sampleAt(10, 10).orElseThrow()
                .rock().orElseThrow().code());
    }

    @Test
    void coverageStatusPermutationsAreDeterministic() {
        ChunkCoordinate lowerPosition = new ChunkCoordinate(0, 0, 0);
        ChunkCoordinate upperPosition = new ChunkCoordinate(0, 1, 0);
        ParsedChunk lower = RockStreamingFixtures.chunk(lowerPosition,
                RockStreamingFixtures.at(1, 10, 1, 1));
        WorldPosition center = new WorldPosition(1, 0, 1);
        assertPermutations(
                List.of(SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                        SelectiveChunkVisit.paletteRejected(position(upperPosition))),
                center);
        assertPermutations(
                List.of(SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                        SelectiveChunkVisit.missing(position(upperPosition))),
                center);
        assertPermutations(
                List.of(SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                        SelectiveChunkVisit.failed(position(upperPosition), "decode failure")),
                center);
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

        assertCrossStatus(SelectiveChunkVisit.paletteRejected(position(0, 0, 0)),
                SelectiveChunkVisit.missing(position(0, 0, 0)));
        assertCrossStatus(SelectiveChunkVisit.missing(position(0, 0, 0)),
                SelectiveChunkVisit.paletteRejected(position(0, 0, 0)));
        assertCrossStatus(decoded(0, 0, 0, 10, 1),
                SelectiveChunkVisit.failed(position(0, 0, 0), "decode failure"));
    }

    @Test
    void rejectsWrongDimensionAndOutOfRangeChunkPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(new SelectiveChunkVisit(
                        new cartographer.model.ChunkPosition(0, 0, 0, 1),
                        SelectiveChunkVisitStatus.MISSING, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(-2, 0, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(1, 0, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(0, 0, -2))));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(0, 0, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(0, -1, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.missing(position(0, 1 + 1, 0))));
        ParsedChunk mismatched = chunk(1, 0, 0, 10, 1);
        assertThrows(IllegalArgumentException.class,
                () -> session().accept(SelectiveChunkVisit.decoded(position(0, 0, 0), mismatched)));
    }

    @Test
    void lifecycleAndModeContractsAreExplicit() {
        RockStreamingSession session = session();
        assertThrows(NullPointerException.class, () -> session.accept(null));
        session.finish();
        assertThrows(IllegalStateException.class, session::finish);
        assertThrows(IllegalStateException.class,
                () -> session.accept(SelectiveChunkVisit.missing(position(0, 0, 0))));
        RockStreamingSession atY = RockStreamingSession.open(
                new WorldPosition(0, 0, 0), 1, 31, 32, 0, RockMapMode.AT_Y, CATALOG);
        atY.accept(decoded(0, 0, 0, 31, 1));
        assertEquals(RockColumnState.OBSERVED, atY.finish().stateAt(0, 0));
        assertThrows(IllegalArgumentException.class, () -> RockStreamingSession.open(
                new WorldPosition(0, 0, 0), 1, 0, 2, 0, RockMapMode.AT_Y, CATALOG));
    }

    @Test
    void atYPreservesStatusesBoundariesAndExactTarget() {
        ChunkCoordinate lowerPosition = new ChunkCoordinate(0, 0, 0);
        ParsedChunk y31 = RockStreamingFixtures.chunk(
                lowerPosition, RockStreamingFixtures.at(1, 31, 1, 1));
        RockMap at31 = atYStreaming(List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), y31)),
                31, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.OBSERVED, at31.stateAt(1, 1));
        assertEquals("game:rock-granite", at31.sampleAt(1, 1).orElseThrow()
                .rock().orElseThrow().code());

        ChunkCoordinate upperPosition = new ChunkCoordinate(0, 1, 0);
        ParsedChunk y32 = RockStreamingFixtures.chunk(
                upperPosition, RockStreamingFixtures.at(1, 0, 1, 3));
        RockMap at32 = atYStreaming(List.of(
                SelectiveChunkVisit.decoded(position(upperPosition), y32)),
                32, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.OBSERVED, at32.stateAt(1, 1));
        assertEquals("somemod:rock-gneiss", at32.sampleAt(1, 1).orElseThrow()
                .rock().orElseThrow().code());

        ParsedChunk empty = RockStreamingFixtures.chunk(lowerPosition);
        RockMap noRock = atYStreaming(List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), empty)),
                5, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.NO_ROCK, noRock.stateAt(1, 1));

        RockMap paletteRejected = atYStreaming(List.of(
                SelectiveChunkVisit.paletteRejected(position(lowerPosition))),
                5, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.NO_ROCK, paletteRejected.stateAt(1, 1));

        RockMap missing = atYStreaming(List.of(
                SelectiveChunkVisit.missing(position(lowerPosition))),
                5, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.UNAVAILABLE, missing.stateAt(1, 1));

        RockMap failed = atYStreaming(List.of(
                SelectiveChunkVisit.failed(position(lowerPosition), "decode failure")),
                5, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.UNAVAILABLE, failed.stateAt(1, 1));

        RockMap unseen = atYStreaming(List.of(), 5, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.UNAVAILABLE, unseen.stateAt(1, 1));
    }

    @Test
    void atYPreservesCoordinatesModdedAndOreExclusion() {
        ChunkCoordinate negativePosition = new ChunkCoordinate(-1, 0, -1);
        ParsedChunk negative = RockStreamingFixtures.chunk(negativePosition,
                RockStreamingFixtures.at(31, 1, 31, 3));
        RockMap negativeMap = atYStreaming(List.of(
                SelectiveChunkVisit.decoded(position(negativePosition), negative)),
                1, new WorldPosition(-0.2, 0, -0.2));
        assertEquals(RockColumnState.OBSERVED, negativeMap.stateAt(-1, -1));
        assertEquals("somemod:rock-gneiss", negativeMap.sampleAt(-1, -1).orElseThrow()
                .rock().orElseThrow().code());

        ChunkCoordinate positivePosition = new ChunkCoordinate(0, 0, 0);
        ParsedChunk positive = RockStreamingFixtures.chunk(positivePosition,
                RockStreamingFixtures.at(10, 1, 10, 3));
        RockMap positiveMap = atYStreaming(List.of(
                SelectiveChunkVisit.decoded(position(positivePosition), positive)),
                1, new WorldPosition(10.8, 0, 10.8));
        assertEquals(RockColumnState.OBSERVED, positiveMap.stateAt(10, 10));
        assertEquals("somemod:rock-gneiss", positiveMap.sampleAt(10, 10).orElseThrow()
                .rock().orElseThrow().code());

        ParsedChunk ore = RockStreamingFixtures.chunk(positivePosition,
                RockStreamingFixtures.at(1, 1, 1, 4));
        RockMap oreMap = atYStreaming(List.of(
                SelectiveChunkVisit.decoded(position(positivePosition), ore)),
                1, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.NO_ROCK, oreMap.stateAt(1, 1));
    }

    @Test
    void atYHorizontalVisitPermutationsAreDeterministic() {
        List<SelectiveChunkVisit> visits = List.of(
                SelectiveChunkVisit.paletteRejected(position(-1, 0, -1)),
                SelectiveChunkVisit.missing(position(0, 0, -1)),
                SelectiveChunkVisit.missing(position(-1, 0, 0)),
                SelectiveChunkVisit.paletteRejected(position(0, 0, 0))
        );
        RockMap first = atYStreaming(visits, 5, new WorldPosition(0, 0, 0));
        RockMap second = atYStreaming(List.of(visits.get(3), visits.get(1), visits.get(0), visits.get(2)),
                5, new WorldPosition(0, 0, 0));
        assertEquals(RockMapTestOracle.snapshot(first), RockMapTestOracle.snapshot(second));
    }

    @Test
    void atYRejectsWrongVerticalChunkAndMismatchedDecodedChunk() {
        RockStreamingSession session = RockStreamingSession.open(
                new WorldPosition(1, 0, 1), 1, 32, 33, 0, RockMapMode.AT_Y, RockStreamingFixtures.CATALOG);
        assertThrows(IllegalArgumentException.class,
                () -> session.accept(SelectiveChunkVisit.missing(position(0, 0, 0))));
        ParsedChunk mismatch = RockStreamingFixtures.chunk(new ChunkCoordinate(1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> session.accept(SelectiveChunkVisit.decoded(position(0, 1, 0), mismatch)));
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
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        blocks[(localY * size) * size] = blockId;
        return cartographer.model.ParsedChunkFixtures.create(new ChunkCoordinate(chunkX, chunkY, chunkZ), chunkY * size,
                size, size, size, blocks);
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

    private RockMap atYStreaming(List<SelectiveChunkVisit> visits, int targetY, WorldPosition center) {
        RockStreamingSession session = RockStreamingSession.open(center, 1, targetY,
                Math.addExact(targetY, 1), 0, RockMapMode.AT_Y, RockStreamingFixtures.CATALOG);
        for (SelectiveChunkVisit visit : visits) session.accept(visit);
        return session.finish();
    }

    private void assertCrossStatus(SelectiveChunkVisit first, SelectiveChunkVisit second) {
        RockStreamingSession session = session();
        session.accept(first);
        assertThrows(IllegalArgumentException.class, () -> session.accept(second));
    }

    private cartographer.model.ChunkPosition position(int x, int y, int z) {
        return new cartographer.model.ChunkPosition(x, y, z, 0);
    }

    private cartographer.model.ChunkPosition position(ChunkCoordinate coordinate) {
        return position(coordinate.x(), coordinate.y(), coordinate.z());
    }

    private RockMap fixtureStreaming(
            List<SelectiveChunkVisit> visits,
            WorldPosition center,
            int minY,
            int maxY
    ) {
        RockStreamingSession session = RockStreamingSession.open(
                center, 1, minY, maxY, 0, RockStreamingFixtures.CATALOG);
        for (SelectiveChunkVisit visit : visits) session.accept(visit);
        return session.finish();
    }

    private void assertPermutations(
            List<SelectiveChunkVisit> visits,
            WorldPosition center
    ) {
        RockMap first = fixtureStreaming(visits, center, 0, 64);
        RockMap second = fixtureStreaming(
                List.of(visits.get(1), visits.get(0)), center, 0, 64);
        assertEquals(RockMapTestOracle.snapshot(first), RockMapTestOracle.snapshot(second));
    }
}
