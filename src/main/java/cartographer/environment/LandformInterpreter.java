package cartographer.environment;

public class LandformInterpreter {
    public LandformSample interpret(
            int rawId
    ) {
        return new LandformSample(
                rawId
        );
    }
}
