package cartographer.perf;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persisted compatibility metadata for one render-data cache revision. */
public record RenderDataCacheManifest(
        String schemaVersion,
        String normalizedSavePath,
        String namespaceHash,
        long saveSize,
        long saveModifiedMillis,
        String compatibilityVersion
) {
    public static final String CURRENT_SCHEMA_VERSION = "render-data-v1";
    public static final String CURRENT_COMPATIBILITY_VERSION = "parser-data-v1";

    private static final List<String> KEYS = List.of(
            "schemaVersion",
            "normalizedSavePathBase64",
            "namespaceHash",
            "saveSize",
            "saveModifiedMillis",
            "compatibilityVersion"
    );

    public RenderDataCacheManifest(RenderDataCacheRevision revision) {
        this(
                revision.schemaVersion(),
                revision.identity().normalizedSavePath().toString(),
                revision.identity().namespaceHash(),
                revision.saveSize(),
                revision.saveModifiedMillis(),
                revision.compatibilityVersion()
        );
    }

    public RenderDataCacheManifest {
        schemaVersion = requireText(schemaVersion, "schema version");
        normalizedSavePath = requireText(normalizedSavePath, "normalized save path");
        namespaceHash = requireText(namespaceHash, "namespace hash");
        if (saveSize < 0) {
            throw new IllegalArgumentException("save size cannot be negative");
        }
        compatibilityVersion = requireText(compatibilityVersion, "compatibility version");
    }

    boolean matches(RenderDataCacheRevision revision) {
        return schemaVersion.equals(revision.schemaVersion())
                && normalizedSavePath.equals(revision.identity().normalizedSavePath().toString())
                && namespaceHash.equals(revision.identity().namespaceHash())
                && saveSize == revision.saveSize()
                && saveModifiedMillis == revision.saveModifiedMillis()
                && compatibilityVersion.equals(revision.compatibilityVersion());
    }

    String serialize() {
        String encodedPath = Base64.getEncoder().encodeToString(
                normalizedSavePath.getBytes(StandardCharsets.UTF_8)
        );
        return "schemaVersion=" + schemaVersion + "\n"
                + "normalizedSavePathBase64=" + encodedPath + "\n"
                + "namespaceHash=" + namespaceHash + "\n"
                + "saveSize=" + saveSize + "\n"
                + "saveModifiedMillis=" + saveModifiedMillis + "\n"
                + "compatibilityVersion=" + compatibilityVersion + "\n";
    }

    static RenderDataCacheManifest parse(String serialized) {
        Objects.requireNonNull(serialized, "serialized manifest is required");
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : serialized.split("\\R", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || values.put(
                    line.substring(0, separator),
                    line.substring(separator + 1)
            ) != null) {
                throw new IllegalArgumentException("malformed render-data manifest");
            }
        }
        if (!values.keySet().equals(new java.util.LinkedHashSet<>(KEYS))) {
            throw new IllegalArgumentException("unexpected render-data manifest keys");
        }
        String path;
        try {
            path = new String(
                    Base64.getDecoder().decode(values.get("normalizedSavePathBase64")),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid encoded save path", exception);
        }
        try {
            return new RenderDataCacheManifest(
                    values.get("schemaVersion"),
                    path,
                    values.get("namespaceHash"),
                    Long.parseLong(values.get("saveSize")),
                    Long.parseLong(values.get("saveModifiedMillis")),
                    values.get("compatibilityVersion")
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid render-data revision number", exception);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
