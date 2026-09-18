package cartographer.save;

import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

/** Creates operation-scoped, read-only SaveSession instances. */
public final class SaveSessionFactory {
    private final SqliteSaveConnection connectionFactory;
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final SaveSessionLifecycleProbe lifecycleProbe;

    public SaveSessionFactory() {
        this(
                new SqliteSaveConnection(),
                new VcdbsReader(
                        new PlayerDataParser(),
                        new MapChunkParser(),
                        new ChunkParser(),
                        new RegistryParser()
                ),
                new WorldMetadataReader(),
                SaveSessionLifecycleProbe.noOp()
        );
    }

    public SaveSessionFactory(
            SqliteSaveConnection connectionFactory,
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this(connectionFactory, reader, metadataReader, SaveSessionLifecycleProbe.noOp());
    }

    public SaveSessionFactory(
            SqliteSaveConnection connectionFactory,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            SaveSessionLifecycleProbe lifecycleProbe
    ) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connection factory is required");
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadata reader is required");
        this.lifecycleProbe = Objects.requireNonNull(lifecycleProbe, "lifecycle probe is required");
    }

    public SaveSession open(Path savePath) {
        Path normalized = normalize(savePath);
        Connection connection = lifecycleProbe.observe(
                normalized,
                connectionFactory.openReadOnly(normalized)
        );
        try {
            WorldMetadata metadata = metadataReader.read(connection, cartographer.application.ProgressReporter.NONE);
            Map<Integer, BlockInfo> registry = reader.readBlockRegistry(connection);
            SaveSnapshot snapshot = new SaveSnapshot(normalized, metadata, registry);
            return new SaveSession(normalized, connection, snapshot);
        } catch (RuntimeException exception) {
            closeAfterFailedOpen(connection, exception);
            throw exception;
        } catch (SQLException exception) {
            SaveException failure = new SaveException(
                    "Cannot initialize save snapshot: " + normalized,
                    exception
            );
            closeAfterFailedOpen(connection, failure);
            throw failure;
        }
    }

    private Path normalize(Path savePath) {
        return SavePathIdentity.normalize(savePath);
    }

    private void closeAfterFailedOpen(Connection connection, RuntimeException failure) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
