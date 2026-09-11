package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;

import java.util.Locale;

public class SurfaceClassifier {
    public SurfaceClass classify(
            BlockInfo block,
            BlockInfo liquid
    ) {
        if (liquid != null
                && liquid.id() != 0
                && isWater(liquid)) {
            return SurfaceClass.WATER;
        }

        if (block == null
                || block.code() == null
                || block.code().startsWith("unknown:")) {
            return SurfaceClass.UNKNOWN;
        }

        String code =
                block.code()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (code.contains("snow")) {
            return SurfaceClass.SNOW;
        }

        if (code.contains("leaves")
                || code.contains("foliage")
                || code.contains("flower")
                || code.contains("mushroom")
                || code.contains("sapling")
                || code.contains("crop")
                || code.contains("tallgrass")) {
            return SurfaceClass.VEGETATION;
        }

        if (code.contains("grass")) {
            return SurfaceClass.GRASS;
        }

        if (code.contains("sand")) {
            return SurfaceClass.SAND;
        }

        if (code.contains("gravel")) {
            return SurfaceClass.GRAVEL;
        }

        if (code.contains("soil")
                || code.contains("clay")
                || code.contains("peat")) {
            return SurfaceClass.SOIL;
        }

        if (code.contains("rock")
                || code.contains("stone")
                || code.contains("ore")) {
            return SurfaceClass.ROCK;
        }

        return SurfaceClass.UNKNOWN;
    }

    private boolean isWater(
            BlockInfo block
    ) {
        String code =
                block.code() == null
                        ? ""
                        : block.code()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return code.contains("water");
    }
}
