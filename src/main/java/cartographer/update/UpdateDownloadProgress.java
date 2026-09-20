package cartographer.update;

public record UpdateDownloadProgress(
        long bytesDownloaded,
        long totalBytes
) {

    public UpdateDownloadProgress {
        if (totalBytes <= 0L) {
            throw new IllegalArgumentException(
                    "totalBytes must be greater than zero"
            );
        }
        if (bytesDownloaded < 0L || bytesDownloaded > totalBytes) {
            throw new IllegalArgumentException(
                    "bytesDownloaded must be between zero and totalBytes"
            );
        }
    }

    public int percent() {
        if (bytesDownloaded == totalBytes) {
            return 100;
        }
        return (int) ((bytesDownloaded * 100.0d) / totalBytes);
    }
}
