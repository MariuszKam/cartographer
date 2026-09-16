package cartographer.perf.fingerprint;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** JDK-only SHA-256 fingerprinting for canonical semantic adapters. */
public final class SemanticFingerprinter {
    private SemanticFingerprinter() {
    }

    public static ResultFingerprint fingerprint(SemanticFingerprintable value) {
        Objects.requireNonNull(value, "value is required");
        CanonicalWriter writer = new CanonicalWriter();
        value.writeCanonical(writer);
        return fingerprint(writer.toByteArray());
    }

    public static ResultFingerprint fingerprint(CanonicalWriter writer) {
        Objects.requireNonNull(writer, "writer is required");
        return fingerprint(writer.toByteArray());
    }

    private static ResultFingerprint fingerprint(byte[] canonicalBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ResultFingerprint(toHex(digest.digest(canonicalBytes)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }
}
