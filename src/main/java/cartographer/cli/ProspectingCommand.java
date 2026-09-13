package cartographer.cli;

import cartographer.application.AnalyzeProspectingAreaUseCase;
import cartographer.application.ProspectingAreaRequest;
import cartographer.application.ProspectingAreaResult;
import cartographer.render.RockMapRenderer;
import cartographer.model.WorldPosition;
import cartographer.prospecting.ProspectingAssessment;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class ProspectingCommand implements Command {
    private static final int DEFAULT_RADIUS = 512;
    private static final int MAX_RADIUS = 8192;

    private final PrintStream out;
    private final AnalyzeProspectingAreaUseCase useCase;
    private final String subcommand;

    public ProspectingCommand(
            PrintStream out,
            AnalyzeProspectingAreaUseCase useCase,
            String subcommand
    ) {
        this.out = out;
        this.useCase = useCase;
        this.subcommand = subcommand;
    }

    @Override
    public void run(String[] args) {
        if (!"analyze".equals(subcommand)) {
            throw new CommandException("Unknown prospecting subcommand: " + subcommand);
        }
        if (args.length < 1) {
            throw new CommandException("Usage: prospecting analyze <save.vcdbs>");
        }
        ProspectingAreaRequest request = request(args);
        ProspectingAreaResult result = useCase.execute(request);
        out.println("PROSPECTING ANALYSIS");
        out.println("Observed saved geology and relative worldgen signals");
        out.println("Center world: " + result.center().x() + "," + result.center().z());
        out.println("Radius: " + result.radius());
        if (result.assessments().isEmpty()) {
            out.println("Resources: none");
            return;
        }
        for (ProspectingAssessment assessment : result.assessments()) {
            var evidence = assessment.candidate().evidence();
            out.println();
            out.println("Resource: " + assessment.candidate().resourceKey());
            out.println("Assessment: " + assessment.rank());
            out.println("Worldgen signal: " + (evidence.worldgenSignal().isPresent()
                    ? String.format(Locale.ROOT, "%.3f relative", evidence.worldgenSignal().getAsDouble())
                    : "unavailable"));
            out.println("Observed geology: " + evidence.geologyState());
            out.println("Observed host rocks: " + evidence.observedHostRocks().stream()
                    .map(rock -> rock.code())
                    .sorted()
                    .toList());
            out.println("Host compatibility: " + assessment.compatibility());
            out.println("Actual ore: " + (evidence.actualOreObserved() ? "observed" : "not observed"));
            out.println("Reasons: " + String.join("; ", assessment.reasons()));
        }
    }

    private ProspectingAreaRequest request(String[] args) {
        Path savePath = Path.of(args[0]);
        Optional<String> resource = option(args, "--resource");
        Optional<String> x = option(args, "--center-x");
        Optional<String> z = option(args, "--center-z");
        if (x.isEmpty() != z.isEmpty()) {
            throw new CommandException("--center-x and --center-z must be used together");
        }
        Optional<WorldPosition> center = x.isEmpty()
                ? Optional.empty()
                : Optional.of(new WorldPosition(
                        doubleValue(x.orElseThrow(), "--center-x"),
                        0.0,
                        doubleValue(z.orElseThrow(), "--center-z")
                ));
        return new ProspectingAreaRequest(
                savePath,
                center,
                intOption(args, "--radius", DEFAULT_RADIUS, MAX_RADIUS),
                resource
        );
    }

    private int intOption(String[] args, String name, int defaultValue, int maximum) {
        String value = option(args, name).orElse(Integer.toString(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0 || parsed > maximum) {
                throw new CommandException(name + " must be between 1 and " + maximum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + name + ": " + value);
        }
    }

    private double doubleValue(String value, String name) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + name + ": " + value);
        }
    }

    private Optional<String> option(String[] args, String name) {
        for (int index = 1; index < args.length - 1; index++) {
            if (name.equals(args[index])) {
                return Optional.of(args[index + 1]);
            }
        }
        return Optional.empty();
    }
}
