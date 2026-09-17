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
    void differentialMatrixPreservesCoverageAndRockSemantics() {
        ParsedChunk lower = RockCharacterizationFixtures.chunk(
                new ChunkCoordinate(0, 0, 0), RockCharacterizationFixtures.at(1, 10, 1, 1));
        ParsedChunk upper = RockCharacterizationFixtures.chunk(
                new ChunkCoordinate(0, 1, 0));
        ChunkCoordinate lowerPosition = new ChunkCoordinate(0, 0, 0);
        ChunkCoordinate upperPosition = new ChunkCoordinate(0, 1, 0);
        WorldPosition center = new WorldPosition(1, 0, 1);

        differential(List.of(lower), List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.paletteRejected(position(upperPosition))),
                RockCharacterizationFixtures.coverage(lowerPosition, upperPosition), center, 0, 64);
        differential(List.of(lower), List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.missing(position(upperPosition))),
                RockCharacterizationFixtures.coverage(lowerPosition), center, 0, 64);
        differential(List.of(lower), List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.failed(position(upperPosition), "decode failure")),
                RockCharacterizationFixtures.coverage(lowerPosition), center, 0, 64);
        differential(List.of(upper), List.of(
                SelectiveChunkVisit.decoded(position(upperPosition), upper),
                SelectiveChunkVisit.missing(position(lowerPosition))),
                RockCharacterizationFixtures.coverage(upperPosition), center, 0, 64);

        ParsedChunk top = RockCharacterizationFixtures.chunk(new ChunkCoordinate(0, 2, 0));
        differential(List.of(lower, top), List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.missing(position(upperPosition)),
                SelectiveChunkVisit.decoded(position(new ChunkCoordinate(0, 2, 0)), top)),
                RockCharacterizationFixtures.coverage(lowerPosition, new ChunkCoordinate(0, 2, 0)),
                center, 0, 96);

        differential(List.of(lower, upper), List.of(
                SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                SelectiveChunkVisit.decoded(position(upperPosition), upper)),
                RockCharacterizationFixtures.coverage(lowerPosition, upperPosition), center, 0, 64);

        differential(List.of(), List.of(
                SelectiveChunkVisit.paletteRejected(position(lowerPosition)),
                SelectiveChunkVisit.paletteRejected(position(upperPosition))),
                RockCharacterizationFixtures.coverage(lowerPosition, upperPosition), center, 0, 64);
    }

    @Test
    void differentialMatrixPreservesModdedOreAndPartialYSemantics() {
        WorldPosition center = new WorldPosition(1, 0, 1);
        ChunkCoordinate position = new ChunkCoordinate(0, 0, 0);
        ParsedChunk modded = RockCharacterizationFixtures.chunk(position,
                RockCharacterizationFixtures.at(1, 1, 1, 3));
        RockMap moddedMap = differential(List.of(modded), List.of(
                SelectiveChunkVisit.decoded(position(position), modded)),
                RockCharacterizationFixtures.coverage(position), center, 0, 32);
        assertEquals("somemod:rock-gneiss", moddedMap.sampleAt(1, 1).orElseThrow()
                .rock().orElseThrow().code());

        ParsedChunk ore = RockCharacterizationFixtures.chunk(position,
                RockCharacterizationFixtures.at(1, 1, 1, 4));
        RockMap oreMap = differential(List.of(ore), List.of(
                SelectiveChunkVisit.decoded(position(position), ore)),
                RockCharacterizationFixtures.coverage(position), center, 0, 32);
        assertEquals(RockColumnState.NO_ROCK, oreMap.stateAt(1, 1));

        ParsedChunk lower = RockCharacterizationFixtures.chunk(position,
                RockCharacterizationFixtures.at(1, 4, 1, 1),
                RockCharacterizationFixtures.at(1, 5, 1, 2));
        ParsedChunk upper = RockCharacterizationFixtures.chunk(new ChunkCoordinate(0, 1, 0),
                RockCharacterizationFixtures.at(1, 1, 1, 2),
                RockCharacterizationFixtures.at(1, 2, 1, 1));
        RockMap partial = differential(List.of(lower, upper), List.of(
                SelectiveChunkVisit.decoded(position(position), lower),
                SelectiveChunkVisit.decoded(position(new ChunkCoordinate(0, 1, 0)), upper)),
                RockCharacterizationFixtures.coverage(position, new ChunkCoordinate(0, 1, 0)),
                center, 5, 34);
        assertEquals(33, partial.rockYAt(1, 1).orElseThrow());
        RockMap narrow = differential(List.of(lower), List.of(
                SelectiveChunkVisit.decoded(position(position), lower)),
                RockCharacterizationFixtures.coverage(position), center, 5, 6);
        assertEquals(5, narrow.rockYAt(1, 1).orElseThrow());
    }

    @Test
    void differentialMatrixPreservesNegativeAndFractionalCoordinates() {
        ChunkCoordinate negativePosition = new ChunkCoordinate(-1, 0, -1);
        ParsedChunk negative = RockCharacterizationFixtures.chunk(negativePosition,
                RockCharacterizationFixtures.at(31, 1, 31, 3));
        differential(List.of(negative), List.of(
                SelectiveChunkVisit.decoded(position(negativePosition), negative)),
                RockCharacterizationFixtures.coverage(negativePosition),
                new WorldPosition(-0.2, 0, -0.2), 0, 32);

        ChunkCoordinate positivePosition = new ChunkCoordinate(0, 0, 0);
        ParsedChunk positive = RockCharacterizationFixtures.chunk(positivePosition,
                RockCharacterizationFixtures.at(10, 1, 10, 3));
        differential(List.of(positive), List.of(
                SelectiveChunkVisit.decoded(position(positivePosition), positive)),
                RockCharacterizationFixtures.coverage(positivePosition),
                new WorldPosition(10.8, 0, 10.8), 0, 32);
    }

    @Test
    void coverageStatusPermutationsAreDeterministicAndMatchLegacy() {
        ChunkCoordinate lowerPosition = new ChunkCoordinate(0, 0, 0);
        ChunkCoordinate upperPosition = new ChunkCoordinate(0, 1, 0);
        ParsedChunk lower = RockCharacterizationFixtures.chunk(lowerPosition,
                RockCharacterizationFixtures.at(1, 10, 1, 1));
        WorldPosition center = new WorldPosition(1, 0, 1);
        assertPermutations(
                List.of(SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                        SelectiveChunkVisit.paletteRejected(position(upperPosition))),
                List.of(lower), RockCharacterizationFixtures.coverage(lowerPosition, upperPosition), center);
        assertPermutations(
                List.of(SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                        SelectiveChunkVisit.missing(position(upperPosition))),
                List.of(lower), RockCharacterizationFixtures.coverage(lowerPosition), center);
        assertPermutations(
                List.of(SelectiveChunkVisit.decoded(position(lowerPosition), lower),
                        SelectiveChunkVisit.failed(position(upperPosition), "decode failure")),
                List.of(lower), RockCharacterizationFixtures.coverage(lowerPosition), center);
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
    void atYMatchesLegacyForStatusesBoundariesAndExactTarget() {
        ParsedChunk y31 = RockCharacterizationFixtures.chunk(
                new ChunkCoordinate(0, 0, 0), RockCharacterizationFixtures.at(1, 31, 1, 1));
        ParsedChunk y32 = RockCharacterizationFixtures.chunk(
                new ChunkCoordinate(0, 1, 0), RockCharacterizationFixtures.at(1, 0, 1, 3));
        atYDifferential(List.of(y31), List.of(SelectiveChunkVisit.decoded(
                position(new ChunkCoordinate(0, 0, 0)), y31)), 31,
                new WorldPosition(1, 0, 1));
        atYDifferential(List.of(y32), List.of(SelectiveChunkVisit.decoded(
                position(new ChunkCoordinate(0, 1, 0)), y32)), 32,
                new WorldPosition(1, 0, 1));

        RockMap noRock = atYDifferential(List.of(RockCharacterizationFixtures.chunk(
                        new ChunkCoordinate(0, 0, 0))),
                List.of(SelectiveChunkVisit.decoded(position(new ChunkCoordinate(0, 0, 0)),
                        RockCharacterizationFixtures.chunk(new ChunkCoordinate(0, 0, 0)))),
                5, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.NO_ROCK, noRock.stateAt(1, 1));
        atYDifferential(List.of(), List.of(SelectiveChunkVisit.paletteRejected(
                position(new ChunkCoordinate(0, 0, 0)))), 5, new WorldPosition(1, 0, 1),
                RockCharacterizationFixtures.coverage(new ChunkCoordinate(0, 0, 0)));
        atYDifferential(List.of(), List.of(SelectiveChunkVisit.missing(
                position(new ChunkCoordinate(0, 0, 0)))), 5, new WorldPosition(1, 0, 1),
                RockCharacterizationFixtures.coverage());
        atYDifferential(List.of(), List.of(SelectiveChunkVisit.failed(
                position(new ChunkCoordinate(0, 0, 0)), "decode failure")), 5,
                new WorldPosition(1, 0, 1), RockCharacterizationFixtures.coverage());
        RockMap unseen = atYStreaming(List.of(), 5, new WorldPosition(1, 0, 1));
        assertEquals(RockColumnState.UNAVAILABLE, unseen.stateAt(1, 1));
    }

    @Test
    void atYMatchesLegacyForCoordinatesModdedAndOreExclusion() {
        ChunkCoordinate negativePosition = new ChunkCoordinate(-1, 0, -1);
        ParsedChunk negative = RockCharacterizationFixtures.chunk(negativePosition,
                RockCharacterizationFixtures.at(31, 1, 31, 3));
        atYDifferential(List.of(negative), List.of(SelectiveChunkVisit.decoded(
                position(negativePosition), negative)), 1,
                new WorldPosition(-0.2, 0, -0.2));

        ParsedChunk positive = RockCharacterizationFixtures.chunk(new ChunkCoordinate(0, 0, 0),
                RockCharacterizationFixtures.at(10, 1, 10, 3));
        atYDifferential(List.of(positive), List.of(SelectiveChunkVisit.decoded(
                position(new ChunkCoordinate(0, 0, 0)), positive)), 1,
                new WorldPosition(10.8, 0, 10.8));

        ParsedChunk ore = RockCharacterizationFixtures.chunk(new ChunkCoordinate(0, 0, 0),
                RockCharacterizationFixtures.at(1, 1, 1, 4));
        RockMap oreMap = atYDifferential(List.of(ore), List.of(SelectiveChunkVisit.decoded(
                position(new ChunkCoordinate(0, 0, 0)), ore)), 1,
                new WorldPosition(1, 0, 1));
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
        assertEquals(RockLegacyOracle.snapshot(first), RockLegacyOracle.snapshot(second));
    }

    @Test
    void atYRejectsWrongVerticalChunkAndMismatchedDecodedChunk() {
        RockStreamingSession session = RockStreamingSession.open(
                new WorldPosition(1, 0, 1), 1, 32, 33, 0, RockMapMode.AT_Y, RockCharacterizationFixtures.CATALOG);
        assertThrows(IllegalArgumentException.class,
                () -> session.accept(SelectiveChunkVisit.missing(position(0, 0, 0))));
        ParsedChunk mismatch = RockCharacterizationFixtures.chunk(new ChunkCoordinate(1, 1, 0));
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
        return new ParsedChunk(new ChunkCoordinate(chunkX, chunkY, chunkZ), chunkY * size,
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
                Math.addExact(targetY, 1), 0, RockMapMode.AT_Y, RockCharacterizationFixtures.CATALOG);
        for (SelectiveChunkVisit visit : visits) session.accept(visit);
        return session.finish();
    }

    private RockMap atYDifferential(List<ParsedChunk> chunks, List<SelectiveChunkVisit> visits,
                                    int targetY, WorldPosition center) {
        return atYDifferential(chunks, visits, targetY, center,
                RockCharacterizationFixtures.coverage(chunks.stream()
                        .map(ParsedChunk::coordinate).toArray(ChunkCoordinate[]::new)));
    }

    private RockMap atYDifferential(List<ParsedChunk> chunks, List<SelectiveChunkVisit> visits,
                                    int targetY, WorldPosition center, RockChunkCoverage coverage) {
        RockMap legacy = new RockAtYScanner().scan(chunks, RockCharacterizationFixtures.CATALOG,
                coverage, center, 1, targetY);
        RockMap streaming = atYStreaming(visits, targetY, center);
        assertEquals(RockLegacyOracle.snapshot(legacy), RockLegacyOracle.snapshot(streaming));
        assertEquals(legacy.observedCount(), streaming.observedCount());
        assertEquals(legacy.noRockCount(), streaming.noRockCount());
        assertEquals(legacy.unavailableCount(), streaming.unavailableCount());
        return streaming;
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

    private RockMap differential(List<ParsedChunk> chunks, List<SelectiveChunkVisit> visits,
                                 RockChunkCoverage coverage, WorldPosition center, int minY, int maxY) {
        RockMap legacy = new RockColumnScanner().scan(chunks, RockCharacterizationFixtures.CATALOG,
                center, 1, minY, maxY, coverage);
        RockStreamingSession streaming = RockStreamingSession.open(center, 1, minY, maxY, 0,
                RockCharacterizationFixtures.CATALOG);
        for (SelectiveChunkVisit visit : visits) streaming.accept(visit);
        RockMap result = streaming.finish();
        assertEquals(RockLegacyOracle.snapshot(legacy), RockLegacyOracle.snapshot(result));
        assertEquals(legacy.observedCount(), result.observedCount());
        assertEquals(legacy.noRockCount(), result.noRockCount());
        assertEquals(legacy.unavailableCount(), result.unavailableCount());
        return result;
    }

    private void assertPermutations(List<SelectiveChunkVisit> visits, List<ParsedChunk> chunks,
                                    RockChunkCoverage coverage, WorldPosition center) {
        RockMap first = mapWithFixtureVisits(visits);
        RockMap second = mapWithFixtureVisits(List.of(visits.get(1), visits.get(0)));
        RockMap legacy = new RockColumnScanner().scan(chunks, RockCharacterizationFixtures.CATALOG,
                center, 1, 0, 64, coverage);
        assertEquals(RockLegacyOracle.snapshot(legacy), RockLegacyOracle.snapshot(first));
        assertEquals(RockLegacyOracle.snapshot(first), RockLegacyOracle.snapshot(second));
    }

    private RockMap mapWithFixtureVisits(List<SelectiveChunkVisit> visits) {
        RockStreamingSession session = RockStreamingSession.open(
                new WorldPosition(1, 0, 1), 1, 0, 64, 0, RockCharacterizationFixtures.CATALOG);
        for (SelectiveChunkVisit visit : visits) session.accept(visit);
        return session.finish();
    }
}
