package cartographer.ui;

import cartographer.resource.ResourceAnalyzer;
import cartographer.model.BlockInfo;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ResourceCatalogService {

    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final ResourceAnalyzer resourceAnalyzer;
    private final OreResourceResolver resolver;

    public ResourceCatalogService(
            VcdbsReader reader,
            ResourceAnalyzer resourceAnalyzer
    ) {
        this(reader, resourceAnalyzer, new OreResourceResolver());
    }

    public ResourceCatalogService(
            VcdbsReader reader,
            ResourceAnalyzer resourceAnalyzer,
            OreResourceResolver resolver
    ) {
        this(
                reader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        new WorldMetadataReader()
                ),
                resourceAnalyzer,
                resolver
        );
    }

    public ResourceCatalogService(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            ResourceAnalyzer resourceAnalyzer,
            OreResourceResolver resolver
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "sessionFactory is required"
        );
        this.resourceAnalyzer = Objects.requireNonNull(
                resourceAnalyzer,
                "resourceAnalyzer is required"
        );
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
    }

    public List<OreResource> discover(Path savePath) {
        Objects.requireNonNull(savePath, "savePath is required");

        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            List<cartographer.model.ServerMapRegion> regions =
                    reader.readMapRegions(
                            session,
                            new ReadDiagnostics()
                    );

            Map<Integer, BlockInfo> registry =
                    session.snapshot()
                            .blockRegistry();

            return resolver.resolve(
                    resourceAnalyzer.resourceKeys(
                            regions
                    ),
                    registry
            );
        }
    }

    static List<OreResource> resourcesFromKeys(List<String> sourceKeys) {
        return new OreResourceResolver().resolve(
                sourceKeys,
                Map.of()
        );
    }
}
