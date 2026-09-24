package cartographer.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class WindowsUpdateBootstrapper implements UpdateBootstrapper {
    private static final String EXPECTED_LAUNCHER = "VS Cartographer.exe";
    private static final String SCRIPT_FILE = "install-update.ps1";
    private static final String OUTCOME_FILE = "result.properties";

    private final Path bootstrapDirectory;
    private final LongSupplier pidSupplier;
    private final Supplier<Optional<Path>> launcherSupplier;
    private final CommandStarter commandStarter;
    private final Supplier<String> osNameSupplier;

    public WindowsUpdateBootstrapper(Path updatesRoot) {
        this(
                updatesRoot,
                () -> ProcessHandle.current().pid(),
                () -> ProcessHandle.current()
                        .info()
                        .command()
                        .map(Path::of),
                command -> {
                    ProcessBuilder builder = new ProcessBuilder(command);
                    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                    builder.start();
                },
                () -> System.getProperty("os.name", "")
        );
    }

    WindowsUpdateBootstrapper(
            Path updatesRoot,
            LongSupplier pidSupplier,
            Supplier<Optional<Path>> launcherSupplier,
            CommandStarter commandStarter,
            Supplier<String> osNameSupplier
    ) {
        Path normalizedRoot = Objects.requireNonNull(
                updatesRoot,
                "updatesRoot is required"
        ).toAbsolutePath().normalize();
        this.bootstrapDirectory = normalizedRoot
                .resolve("bootstrap")
                .normalize();
        if (!bootstrapDirectory.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "Bootstrap directory resolves outside update root"
            );
        }
        this.pidSupplier = Objects.requireNonNull(
                pidSupplier,
                "pidSupplier is required"
        );
        this.launcherSupplier = Objects.requireNonNull(
                launcherSupplier,
                "launcherSupplier is required"
        );
        this.commandStarter = Objects.requireNonNull(
                commandStarter,
                "commandStarter is required"
        );
        this.osNameSupplier = Objects.requireNonNull(
                osNameSupplier,
                "osNameSupplier is required"
        );
    }

    @Override
    public void launch(
            UpdateManifest manifest,
            Path installerPath
    ) throws IOException {
        Objects.requireNonNull(manifest, "manifest is required");
        Objects.requireNonNull(
                installerPath,
                "installerPath is required"
        );

        requireWindows();

        Path installer = installerPath
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(installer)) {
            throw new IOException(
                    "Verified installer no longer exists"
            );
        }

        Path launcher = launcherSupplier.get()
                .map(path -> path.toAbsolutePath().normalize())
                .orElseThrow(() -> new IOException(
                        "Cannot resolve current application executable"
                ));
        requirePackagedLauncher(launcher);

        Files.createDirectories(bootstrapDirectory);
        Path script = bootstrapDirectory.resolve(SCRIPT_FILE);
        Path outcome = outcomePath();
        Path installerLog = installerLogPath(manifest.version());
        Files.deleteIfExists(outcome);
        Files.deleteIfExists(installerLog);
        Files.writeString(
                script,
                bootstrapScript(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        long pid = pidSupplier.getAsLong();
        if (pid <= 0L) {
            throw new IOException(
                    "Cannot resolve current application process id"
            );
        }

        List<String> command = new ArrayList<>();
        command.add("powershell.exe");
        command.add("-NoLogo");
        command.add("-NoProfile");
        command.add("-NonInteractive");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-WindowStyle");
        command.add("Hidden");
        command.add("-File");
        command.add(script.toString());
        command.add("-TargetPid");
        command.add(Long.toString(pid));
        command.add("-InstallerPath");
        command.add(installer.toString());
        command.add("-InstallerLogPath");
        command.add(installerLog.toString());
        command.add("-RelaunchPath");
        command.add(launcher.toString());
        command.add("-ResultPath");
        command.add(outcome.toString());
        command.add("-TargetVersion");
        command.add(manifest.version().toString());
        command.add("-ExpectedSha256");
        command.add(manifest.installerSha256());
        command.add("-ExpectedSize");
        command.add(Long.toString(manifest.installerSize()));

        commandStarter.start(List.copyOf(command));
    }

    public Path outcomePath() {
        return bootstrapDirectory.resolve(OUTCOME_FILE);
    }

    Path installerLogPath(ApplicationVersion version) {
        return bootstrapDirectory.resolve(
                "install-" + Objects.requireNonNull(
                        version,
                        "version is required"
                ) + ".log"
        );
    }

    private void requireWindows() throws IOException {
        String osName = osNameSupplier.get();
        if (osName == null
                || !osName.toLowerCase(Locale.ROOT)
                .startsWith("windows")) {
            throw new IOException(
                    "Restart-and-update is supported only on Windows"
            );
        }
    }

    private void requirePackagedLauncher(Path launcher)
            throws IOException {
        if (!Files.isRegularFile(launcher)) {
            throw new IOException(
                    "Current application executable does not exist"
            );
        }
        Path fileName = launcher.getFileName();
        if (fileName == null
                || !EXPECTED_LAUNCHER.equalsIgnoreCase(
                fileName.toString()
        )) {
            throw new IOException(
                    "Restart-and-update requires the installed "
                            + EXPECTED_LAUNCHER
            );
        }
    }

    private String bootstrapScript() {
        return """
                param(
                    [Parameter(Mandatory=$true)][Int64]$TargetPid,
                    [Parameter(Mandatory=$true)][string]$InstallerPath,
                    [Parameter(Mandatory=$true)][string]$InstallerLogPath,
                    [Parameter(Mandatory=$true)][string]$RelaunchPath,
                    [Parameter(Mandatory=$true)][string]$ResultPath,
                    [Parameter(Mandatory=$true)][string]$TargetVersion,
                    [Parameter(Mandatory=$true)][string]$ExpectedSha256,
                    [Parameter(Mandatory=$true)][Int64]$ExpectedSize
                )

                $ErrorActionPreference = "Stop"

                function Write-Outcome(
                    [string]$Status,
                    [string]$Reason,
                    [int]$ExitCode
                ) {
                    $lines = @(
                        "status=$Status",
                        "version=$TargetVersion",
                        "reason=$Reason",
                        "exitCode=$ExitCode"
                    )
                    [System.IO.File]::WriteAllLines(
                        $ResultPath,
                        $lines,
                        [System.Text.Encoding]::ASCII
                    )
                }

                function Restart-Cartographer {
                    try {
                        Start-Process -FilePath $RelaunchPath | Out-Null
                    } catch {
                        # Persisted outcome remains for the next manual launch.
                    }
                }

                try {
                    $target = Get-Process -Id $TargetPid -ErrorAction SilentlyContinue
                    if ($null -ne $target) {
                        $target | Wait-Process -ErrorAction Stop
                    }

                    $installer = Get-Item -LiteralPath $InstallerPath -ErrorAction Stop
                    if ($installer.Length -ne $ExpectedSize) {
                        Write-Outcome "FAILED" "INTEGRITY_CHECK_FAILED" -2
                        Restart-Cartographer
                        exit 2
                    }

                    $actualHash = (
                        Get-FileHash -LiteralPath $InstallerPath -Algorithm SHA256
                    ).Hash.ToLowerInvariant()
                    if ($actualHash -ne $ExpectedSha256.ToLowerInvariant()) {
                        Write-Outcome "FAILED" "INTEGRITY_CHECK_FAILED" -2
                        Restart-Cartographer
                        exit 2
                    }

                    $installerArguments = @(
                        "/quiet",
                        "/norestart",
                        "/L*V",
                        ('"{0}"' -f $InstallerLogPath)
                    )
                    $installerProcess = Start-Process -FilePath $InstallerPath -ArgumentList $installerArguments -PassThru -Wait
                    $installerExit = $installerProcess.ExitCode

                    if ($installerExit -eq 0 -or $installerExit -eq 1641) {
                        Write-Outcome "SUCCESS" "SUCCESS" $installerExit
                    } elseif ($installerExit -eq 3010) {
                        Write-Outcome "RESTART_REQUIRED" "RESTART_REQUIRED" $installerExit
                    } else {
                        Write-Outcome "FAILED" "INSTALLER_FAILED" $installerExit
                    }

                    Restart-Cartographer
                    exit $installerExit
                } catch {
                    Write-Outcome "FAILED" "BOOTSTRAP_FAILED" -1
                    Restart-Cartographer
                    exit 1
                }
                """;
    }

    @FunctionalInterface
    interface CommandStarter {
        void start(List<String> command) throws IOException;
    }
}
