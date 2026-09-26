package cartographer.application;

import cartographer.render.ActualOreOverlaySpec;
import cartographer.spatial.OreChunkPositionPlanner;
import cartographer.progress.ProgressReporter;
import cartographer.testing.IntegrationTest;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.cache.RenderDataCacheRevision;
import cartographer.cache.RenderDataCacheStore;
import cartographer.index.ResourceChunkIndexEntry;
import cartographer.index.ResourceOccurrence;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.cache.TerrainHeightTile;
import cartographer.cache.TerrainTileStore;
import cartographer.cache.SurfaceTileStore;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cartographer.testing.ImageAssertions.assertImageEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class RenderActualOreMapRoutingTest extends RenderActualOreMapUseCaseTestSupport {


    @Test
    void renderRequestRequiresNonNullOptionalReferences() {
        assertEquals(
                Optional.empty(),
                new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"),
                        1,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(),
                        Optional.empty(),
                        ActualBlockYFilter.unbounded(),
                        Optional.empty()
                ).oreMatch()
        );
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), null, ActualBlockYFilter.unbounded(), Optional.empty()
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), Optional.empty(), ActualBlockYFilter.unbounded(), null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), Optional.of(" "), ActualBlockYFilter.unbounded(), Optional.empty()
                )
        );
    }

    @Test
    void renderResultRequiresNonNullActualOreMapOptional() {
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapResult(
                        null, null, null, null, null, null, null,
                        null, null, null, null, 0, List.of(),
                        RenderDataCacheReport.disabled("test"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
    }

    @Test
    void usesSelectiveReaderForOreAndStreamsMatchingChunk() {
        FakeReader reader = new FakeReader(
                Map.of(1, new BlockInfo(1, "ore-cassiterite-granite"))
        );
        RenderActualOreMapResult result = execute(reader);

        assertEquals(1, reader.adaptiveSelectiveCalls);
        assertEquals(1, reader.selectiveCalls);
        assertArrayEquals(new int[]{1}, reader.lastWantedBlockIds);
        assertFalse(reader.lastPositions.isEmpty());
        assertEquals(1, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertTrue(result.preparedMapData().isPresent());
        assertTrue(result.decorationState().isPresent());
        assertFalse(result.renderDataCacheReport().enabled());
        assertEquals(64, result.geometry().imageWidth());
        assertEquals(48.0, result.geometry().worldMinX());
        assertEquals(48.0, result.geometry().worldMinZ());
        assertEquals(80.0, result.geometry().worldMaxXExclusive());
        assertEquals(80.0, result.geometry().worldMaxZExclusive());
        assertEquals(
                ActualBlockMatchMode.ORE_CODE,
                result.actualOreOverlays().getFirst().spec().matchMode()
        );
    }

    @Test
    void avoidsSelectiveReaderWhenRegistryHasNoMatchingIds() {
        FakeReader reader = new FakeReader(Map.of());
        RenderActualOreMapResult result = execute(reader);

        assertEquals(0, reader.selectiveCalls);
        assertEquals(0, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertTrue(result.actualOreOverlays().getFirst().map().cells().isEmpty());
    }

    @Test
    void twoOverlaysPreserveOrderAndUseOneSelectiveLookup() {
        FakeReader reader = new FakeReader(Map.of(
                1, new BlockInfo(1, "ore-cassiterite-granite"),
                2, new BlockInfo(2, "ore-nativecopper-granite")
        ));
        RenderActualOreMapResult result = execute(
                reader,
                List.of(
                        new ActualOreOverlaySpec("Cassiterite", "cassiterite", Color.ORANGE, ActualBlockMatchMode.ORE_CODE),
                        new ActualOreOverlaySpec("Copper", "nativecopper", Color.RED, ActualBlockMatchMode.ORE_CODE)
                )
        );

        assertEquals(1, reader.selectiveCalls);
        assertEquals("Cassiterite", result.actualOreOverlays().get(0).spec().displayName());
        assertEquals("Copper", result.actualOreOverlays().get(1).spec().displayName());
    }

    @Test
    void genericSubstringMatchesNonOreCodeButOreCodeDoesNot() {
        FakeReader genericReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult generic = execute(
                genericReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.GENERIC_SUBSTRING
                ))
        );
        FakeReader oreReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult ore = execute(
                oreReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );

        assertEquals(1, generic.actualOreOverlays().getFirst().map().matchingBlocks());
        assertEquals(0, oreReader.selectiveCalls);
        assertEquals(0, ore.actualOreOverlays().getFirst().map().matchingBlocks());
    }

    @Test
    void surfaceDisabledDoesNotReadSurfaceChunks() {
        FakeReader reader = new FakeReader(Map.of());

        execute(reader, List.of(), Set.of(RenderLayer.TERRAIN), 64, 64, 16);

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(0, reader.exactChunkCalls);
        assertEquals(1, reader.registryCalls);
    }
}
