package cartographer.atlas;

import cartographer.cli.CommandException;
import cartographer.application.ProgressReporter;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.WorldPosition;
import cartographer.render.MapRenderer;
import cartographer.render.PngWriter;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.render.RenderedMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AtlasRenderer {

    private static final Set<RenderLayer> SUPPORTED_LAYERS =
            Set.of(
                    RenderLayer.TERRAIN,
                    RenderLayer.MARKERS
            );

    private final TilePyramid tilePyramid;
    private final MapRenderer mapRenderer;
    private final PngWriter pngWriter;

    public AtlasRenderer(
            TilePyramid tilePyramid,
            MapRenderer mapRenderer,
            PngWriter pngWriter
    ) {
        this.tilePyramid =
                tilePyramid;

        this.mapRenderer =
                mapRenderer;

        this.pngWriter =
                pngWriter;
    }

    public void render(
            Path outputDirectory,
            WorldPosition center,
            HomeState home,
            List<MapChunk> chunks,
            int radiusBlocks,
            int levels,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(
                home,
                "Home state is required"
        );

        List<AtlasTile> tiles =
                tilePyramid.plan(
                        center,
                        radiusBlocks,
                        levels
                );

        for (int index = 0;
             index < tiles.size();
             index++) {

            AtlasTile tile =
                    tiles.get(
                            index
                    );

            progress.progress(
                    "Rendering atlas tiles",
                    index + 1,
                    tiles.size()
            );

            RenderOptions options =
                    new RenderOptions(
                            tile.radiusBlocks(),
                            1,
                            RenderStyle.TOPOGRAPHIC,
                            SUPPORTED_LAYERS
                    );

            RenderedMap rendered =
                    mapRenderer.render(
                            tile.center(),
                            home,
                            chunks,
                            options,
                            ProgressReporter.NONE
                    );

            pngWriter.write(
                    rendered.image(),
                    outputDirectory
                            .resolve(
                                    "tiles"
                            )
                            .resolve(
                                    "z"
                                            + tile.level()
                            )
                            .resolve(
                                    tile.x()
                                            + "_"
                                            + tile.z()
                                            + ".png"
                            )
            );
        }

        writeMetadata(
                outputDirectory,
                center,
                radiusBlocks,
                levels,
                tiles.size()
        );

        writeViewer(
                outputDirectory,
                levels
        );
    }

    private void writeMetadata(
            Path outputDirectory,
            WorldPosition center,
            int radiusBlocks,
            int levels,
            int tileCount
    ) {
        String json =
                "{\n"
                        + "  \"centerX\": "
                        + center.x()
                        + ",\n"
                        + "  \"centerZ\": "
                        + center.z()
                        + ",\n"
                        + "  \"radiusBlocks\": "
                        + radiusBlocks
                        + ",\n"
                        + "  \"levels\": "
                        + levels
                        + ",\n"
                        + "  \"tileCount\": "
                        + tileCount
                        + "\n"
                        + "}\n";

        writeString(
                outputDirectory.resolve(
                        "metadata.json"
                ),
                json
        );
    }

    private void writeViewer(
            Path outputDirectory,
            int levels
    ) {
        StringBuilder html =
                new StringBuilder();

        html.append(
                "<!doctype html><meta charset=\"utf-8\"><title>VS Cartographer Atlas</title>"
        );

        html.append(
                "<style>body{font-family:sans-serif;background:#111;color:#eee}img{image-rendering:pixelated;border:1px solid #444;margin:4px}</style>"
        );

        html.append(
                "<h1>VS Cartographer Atlas</h1>"
        );

        for (int level = 0;
             level < levels;
             level++) {

            int axis =
                    1 << level;

            html.append(
                            "<h2>LOD "
                    )
                    .append(
                            level
                    )
                    .append(
                            "</h2><div>"
                    );

            for (int z = 0;
                 z < axis;
                 z++) {

                html.append(
                        "<div>"
                );

                for (int x = 0;
                     x < axis;
                     x++) {

                    html.append(
                                    "<img src=\"tiles/z"
                            )
                            .append(
                                    level
                            )
                            .append(
                                    '/'
                            )
                            .append(
                                    x
                            )
                            .append(
                                    '_'
                            )
                            .append(
                                    z
                            )
                            .append(
                                    ".png\">"
                            );
                }

                html.append(
                        "</div>"
                );
            }

            html.append(
                    "</div>"
            );
        }

        writeString(
                outputDirectory.resolve(
                        "index.html"
                ),
                html.toString()
        );
    }

    private void writeString(
            Path path,
            String content
    ) {
        try {
            Path parent =
                    path.getParent();

            if (parent != null) {
                Files.createDirectories(
                        parent
                );
            }

            Files.writeString(
                    path,
                    content,
                    StandardCharsets.UTF_8
            );

        } catch (IOException exception) {
            throw new CommandException(
                    "Cannot write atlas file: "
                            + path
                            + " ("
                            + exception.getMessage()
                            + ")",
                    exception
            );
        }
    }
}