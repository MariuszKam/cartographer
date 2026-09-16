package cartographer.perf.fingerprint;

/** Adapter contract for writing a domain result's normalized semantic values. */
@FunctionalInterface
public interface SemanticFingerprintable {
    void writeCanonical(CanonicalWriter writer);
}
