package cartographer.perf.fingerprint;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FingerprintTest {
    @Test
    void canonicalValuesHashDeterministicallyAndValueIsFullSha256() {
        ResultFingerprint first = fingerprint(writer -> writer
                .writeInt(7)
                .writeLong(9L)
                .writeBoolean(true)
                .writeString("café")
                .writeEnum(TestKind.ALPHA));
        ResultFingerprint second = fingerprint(writer -> writer
                .writeInt(7)
                .writeLong(9L)
                .writeBoolean(true)
                .writeString("café")
                .writeEnum(TestKind.ALPHA));

        assertEquals(first, second);
        assertEquals(64, first.sha256Hex().length());
        assertEquals(first.sha256Hex().toLowerCase(java.util.Locale.ROOT), first.sha256Hex());
        assertThrows(IllegalArgumentException.class, () -> new ResultFingerprint("abc"));
    }

    @Test
    void canonicalFramingPreventsConcatenationAmbiguity() {
        ResultFingerprint abC = fingerprint(writer -> writer
                .writeSequenceStart(2).writeString("ab").writeString("c"));
        ResultFingerprint aBc = fingerprint(writer -> writer
                .writeSequenceStart(2).writeString("a").writeString("bc"));

        assertNotEquals(abC, aBc);
    }

    @Test
    void sequenceOrderingIsExplicitAndUnorderedNormalizationIsDeliberate() {
        ResultFingerprint ordered = fingerprint(writer -> writer
                .writeSequenceStart(2).writeString("a").writeString("b"));
        ResultFingerprint reversed = fingerprint(writer -> writer
                .writeSequenceStart(2).writeString("b").writeString("a"));
        ResultFingerprint normalizedOne = unorderedFingerprint(List.of("b", "a"));
        ResultFingerprint normalizedTwo = unorderedFingerprint(List.of("a", "b"));

        assertNotEquals(ordered, reversed);
        assertEquals(normalizedOne, normalizedTwo);
    }

    @Test
    void imagesUseDimensionsAndLogicalArgbRatherThanStorageLayout() {
        BufferedImage argb = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        BufferedImage bgr = new BufferedImage(2, 1, BufferedImage.TYPE_4BYTE_ABGR);
        int firstPixel = new Color(10, 20, 30, 255).getRGB();
        int secondPixel = new Color(40, 50, 60, 128).getRGB();
        argb.setRGB(0, 0, firstPixel);
        argb.setRGB(1, 0, secondPixel);
        bgr.setRGB(0, 0, firstPixel);
        bgr.setRGB(1, 0, secondPixel);

        BufferedImage changedDimension = new BufferedImage(1, 2, BufferedImage.TYPE_INT_ARGB);
        changedDimension.setRGB(0, 0, firstPixel);
        changedDimension.setRGB(0, 1, secondPixel);
        BufferedImage changedPixel = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        changedPixel.setRGB(0, 0, firstPixel);
        changedPixel.setRGB(1, 0, Color.BLACK.getRGB());

        assertEquals(ImageFingerprinter.fingerprint(argb), ImageFingerprinter.fingerprint(bgr));
        assertNotEquals(ImageFingerprinter.fingerprint(argb),
                ImageFingerprinter.fingerprint(changedDimension));
        assertNotEquals(ImageFingerprinter.fingerprint(argb),
                ImageFingerprinter.fingerprint(changedPixel));
    }

    @Test
    void immutableFingerprintEqualityIsDeterministic() {
        ResultFingerprint first = fingerprint(writer -> writer.writeBytes(new byte[]{1, 2, 3}));
        ResultFingerprint second = new ResultFingerprint(first.sha256Hex());

        assertEquals(first, second);
        assertEquals("ResultFingerprint[sha256Hex=" + first.sha256Hex() + "]", first.toString());
    }

    private static ResultFingerprint fingerprint(SemanticFingerprintable value) {
        return SemanticFingerprinter.fingerprint(value);
    }

    private static ResultFingerprint unorderedFingerprint(List<String> values) {
        List<String> sorted = values.stream().sorted().toList();
        return fingerprint(writer -> {
            writer.writeSequenceStart(sorted.size());
            sorted.forEach(writer::writeString);
        });
    }

    private enum TestKind {
        ALPHA
    }
}
