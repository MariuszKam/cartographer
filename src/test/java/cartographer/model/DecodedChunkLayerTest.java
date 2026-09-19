package cartographer.model;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecodedChunkLayerTest {

    @Test
    void builderPopulatesLayerAndCannotBeReusedAfterBuild() {
        DecodedChunkLayer.Builder builder =
                DecodedChunkLayer.builder(2);
        builder.set(0, 7).set(1, 3);

        DecodedChunkLayer layer = builder.build();

        assertEquals(2, layer.length());
        assertEquals(7, layer.valueAt(0));
        assertEquals(3, layer.valueAt(1));
        assertThrows(IllegalStateException.class, () -> builder.set(0, 9));
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void copiedArrayCannotMutateLayer() {
        DecodedChunkLayer layer =
                DecodedChunkLayer.builder(2)
                        .set(0, 7)
                        .set(1, 3)
                        .build();

        int[] values = layer.toArray();
        values[0] = 9;

        assertArrayEquals(new int[]{7, 3}, layer.toArray());
    }

    @Test
    void copyOfDefensivelyCopiesSourceArray() {
        int[] source = {7, 3};
        DecodedChunkLayer layer = DecodedChunkLayer.copyOf(source);

        source[0] = 9;

        assertArrayEquals(new int[]{7, 3}, layer.toArray());
    }

    @Test
    void constantLayerProvidesValuesWithoutChangingDefensiveArraySemantics() {
        DecodedChunkLayer layer =
                DecodedChunkLayer.constant(4, 7);

        assertEquals(4, layer.length());
        assertEquals(7, layer.valueAt(0));
        assertEquals(7, layer.valueAt(3));
        assertArrayEquals(
                new int[]{7, 7, 7, 7},
                layer.toArray()
        );

        int[] returned = layer.toArray();
        returned[0] = 99;

        assertEquals(7, layer.valueAt(0));
        assertArrayEquals(
                new int[]{7, 7, 7, 7},
                layer.toArray()
        );
    }

    @Test
    void emptyLayerIsAZeroConstantLayer() {
        DecodedChunkLayer layer =
                DecodedChunkLayer.empty(3);

        assertEquals(0, layer.valueAt(0));
        assertEquals(0, layer.valueAt(2));
        assertArrayEquals(new int[]{0, 0, 0}, layer.toArray());
    }

    @Test
    void constantLayerPreservesBoundsChecks() {
        DecodedChunkLayer layer =
                DecodedChunkLayer.constant(2, 5);

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> layer.valueAt(-1)
        );
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> layer.valueAt(2)
        );
    }

    @Test
    void compactPaletteBitPlanesProvidePointLookupsAndDefensiveArrays() {
        int[] palette = {10, 20};
        byte[] bitPlanes =
                ByteBuffer.allocate(Integer.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(0b1010)
                        .array();

        DecodedChunkLayer layer =
                DecodedChunkLayer.compactPaletteBits(
                        4,
                        palette,
                        palette.length,
                        bitPlanes,
                        bitPlanes.length,
                        1,
                        1,
                        4
                );

        palette[0] = 99;
        bitPlanes[0] = 0;

        assertEquals(10, layer.valueAt(0));
        assertEquals(20, layer.valueAt(1));
        assertEquals(10, layer.valueAt(2));
        assertEquals(20, layer.valueAt(3));
        assertArrayEquals(
                new int[]{10, 20, 10, 20},
                layer.toArray()
        );

        int[] returned = layer.toArray();
        returned[1] = 123;
        assertEquals(20, layer.valueAt(1));
    }

    @Test
    void compactPaletteBitPlanesValidateGeometry() {
        byte[] bitPlanes = new byte[Integer.BYTES];

        assertThrows(
                IllegalArgumentException.class,
                () -> DecodedChunkLayer.compactPaletteBits(
                        5,
                        new int[]{0, 1},
                        2,
                        bitPlanes,
                        bitPlanes.length,
                        1,
                        1,
                        4
                )
        );
    }

    @Test
    void equalContentsDoNotProvideValueEquality() {
        DecodedChunkLayer first =
                DecodedChunkLayer.builder(1)
                        .set(0, 7)
                        .build();
        DecodedChunkLayer second =
                DecodedChunkLayer.builder(1)
                        .set(0, 7)
                        .build();

        assertNotEquals(first, second);
    }
}
