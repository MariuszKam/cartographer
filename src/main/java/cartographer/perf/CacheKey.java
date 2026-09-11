package cartographer.perf;

public record CacheKey(String saveName, long size, long modifiedMillis, String parserVersion) {
    public String fileName() {
        String safeName = saveName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safeName + "-" + size + "-" + modifiedMillis + "-" + parserVersion + ".cache";
    }
}
