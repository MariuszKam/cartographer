package cartographer.cache;

import cartographer.save.SavePathIdentity;

import java.nio.file.Path;

/** Stable namespace identity for render data belonging to one save path. */
public record RenderDataCacheIdentity(
        Path normalizedSavePath,
        String namespaceHash
) {
    public RenderDataCacheIdentity(Path savePath) {
        this(savePath, null);
    }

    public RenderDataCacheIdentity {
        normalizedSavePath = SavePathIdentity.normalize(normalizedSavePath);
        String expectedHash = RenderDataCacheIdentityHash.sha256(normalizedSavePath.toString());
        if (namespaceHash == null) {
            namespaceHash = expectedHash;
        } else if (!expectedHash.equalsIgnoreCase(namespaceHash)) {
            throw new IllegalArgumentException("namespace hash does not match save path");
        }
        namespaceHash = expectedHash;
    }

}
