[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        "BeforeUpgrade",
        "AfterUpgrade",
        "AfterUninstall",
        "TamperInstaller",
        "RestoreInstaller"
    )]
    [string]$Mode,

    [Parameter(Mandatory = $false)]
    [string]$SavePath,

    [Parameter(Mandatory = $false)]
    [string]$OldVersion,

    [Parameter(Mandatory = $false)]
    [string]$TargetVersion,

    [Parameter(Mandatory = $false)]
    [string]$StateRoot = (
        Join-Path (
            [Environment]::GetFolderPath(
                [Environment+SpecialFolder]::UserProfile
            )
        ) ".vs-cartographer"
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:FailureCount = 0
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$validationRoot = Join-Path $repositoryRoot "build\auto-update-stage5"
$baselinePath = Join-Path $validationRoot "campaign-baseline.json"
$afterUpgradePath = Join-Path $validationRoot "after-upgrade.json"
$afterUninstallPath = Join-Path $validationRoot "after-uninstall.json"
$applicationName = "VS Cartographer"

function Write-Pass([string]$Message) {
    Write-Host "PASS: $Message"
}

function Write-Info([string]$Message) {
    Write-Host "INFO: $Message"
}

function Write-WarningMessage([string]$Message) {
    Write-Host "WARNING: $Message"
}

function Write-Fail([string]$Message) {
    $script:FailureCount++
    Write-Host "FAIL: $Message"
}

function Ensure-Windows {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw "Auto Update Stage 5 validation is supported only on Windows."
    }
}

function Ensure-ValidationDirectory {
    if (-not (Test-Path -LiteralPath $validationRoot -PathType Container)) {
        New-Item -ItemType Directory -Path $validationRoot -Force | Out-Null
    }
}

function Assert-StableVersion([string]$Value, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name is required."
    }
    if ($Value -notmatch "^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$") {
        throw "$Name must use stable MAJOR.MINOR.PATCH form: $Value"
    }
}

function Assert-UpgradeDirection(
    [string]$FromVersion,
    [string]$ToVersion
) {
    Assert-StableVersion $FromVersion "-OldVersion"
    Assert-StableVersion $ToVersion "-TargetVersion"

    $from = [Version]$FromVersion
    $to = [Version]$ToVersion
    if ($to.CompareTo($from) -le 0) {
        throw "Target version must be newer than old version: $FromVersion -> $ToVersion"
    }
}

function Resolve-SaveFile([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "-SavePath is required for this validation mode."
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    $item = Get-Item -LiteralPath $resolved.Path -ErrorAction Stop
    if (-not ($item -is [System.IO.FileInfo])) {
        throw "Save path is not a file: $($item.FullName)"
    }
    if (-not $item.Extension.Equals(
        ".vcdbs",
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Save path must have a .vcdbs extension: $($item.FullName)"
    }
    return $item
}

function Get-SaveSnapshot([System.IO.FileInfo]$SaveFile) {
    return [ordered]@{
        Path = $SaveFile.FullName
        Sha256 = (
            Get-FileHash -LiteralPath $SaveFile.FullName -Algorithm SHA256
        ).Hash.ToLowerInvariant()
        Length = [int64]$SaveFile.Length
        WalExists = [bool](Test-Path -LiteralPath "$($SaveFile.FullName)-wal")
        ShmExists = [bool](Test-Path -LiteralPath "$($SaveFile.FullName)-shm")
    }
}

function Assert-SaveUnchanged(
    [System.IO.FileInfo]$SaveFile,
    $BaselineSave
) {
    $current = Get-SaveSnapshot $SaveFile
    if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
        [string]$current.Path,
        [string]$BaselineSave.Path
    )) {
        Write-Fail "save path differs from baseline"
        return
    }

    if ([string]$current.Sha256 -eq [string]$BaselineSave.Sha256) {
        Write-Pass "Vintage Story save SHA-256 is unchanged"
    } else {
        Write-Fail "Vintage Story save SHA-256 changed"
    }

    if ([int64]$current.Length -eq [int64]$BaselineSave.Length) {
        Write-Pass "Vintage Story save length is unchanged"
    } else {
        Write-Fail "Vintage Story save length changed"
    }

    if ($current.WalExists -and -not [bool]$BaselineSave.WalExists) {
        Write-Fail "new SQLite WAL sidecar appeared"
    } else {
        Write-Pass "no new SQLite WAL sidecar"
    }

    if ($current.ShmExists -and -not [bool]$BaselineSave.ShmExists) {
        Write-Fail "new SQLite SHM sidecar appeared"
    } else {
        Write-Pass "no new SQLite SHM sidecar"
    }
}

function Resolve-StateRoot([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "-StateRoot cannot be blank."
    }
    return [IO.Path]::GetFullPath($Path)
}

function Get-ProtectedStateSnapshot([string]$RootPath) {
    $root = Resolve-StateRoot $RootPath
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        return @()
    }

    $prefix = $root.TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    ) + [IO.Path]::DirectorySeparatorChar

    $snapshot = @()
    foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse) {
        $relative = $file.FullName.Substring($prefix.Length)
        $normalized = $relative.Replace("/", "\")

        if ($normalized.Equals(
            "update.properties",
            [StringComparison]::OrdinalIgnoreCase
        )) {
            continue
        }
        if ($normalized.StartsWith(
            "cache\",
            [StringComparison]::OrdinalIgnoreCase
        )) {
            continue
        }
        if ($normalized.StartsWith(
            "updates\",
            [StringComparison]::OrdinalIgnoreCase
        )) {
            continue
        }

        $snapshot += [ordered]@{
            Path = $normalized
            Length = [int64]$file.Length
            Sha256 = (
                Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256
            ).Hash.ToLowerInvariant()
        }
    }

    return @($snapshot | Sort-Object Path)
}

function Assert-ProtectedStateUnchanged(
    $BaselineState,
    [string]$RootPath
) {
    $startFailures = $script:FailureCount
    $expected = @($BaselineState)
    $current = @(Get-ProtectedStateSnapshot $RootPath)

    $expectedByPath = @{}
    foreach ($item in $expected) {
        $expectedByPath[[string]$item.Path] = $item
    }

    $currentByPath = @{}
    foreach ($item in $current) {
        $currentByPath[[string]$item.Path] = $item
    }

    foreach ($path in $expectedByPath.Keys) {
        if (-not $currentByPath.ContainsKey($path)) {
            Write-Fail "Cartographer state file disappeared: $path"
            continue
        }

        $before = $expectedByPath[$path]
        $after = $currentByPath[$path]
        if ([int64]$before.Length -ne [int64]$after.Length) {
            Write-Fail "Cartographer state file length changed: $path"
            continue
        }
        if ([string]$before.Sha256 -ne [string]$after.Sha256) {
            Write-Fail "Cartographer state file content changed: $path"
        }
    }

    foreach ($path in $currentByPath.Keys) {
        if (-not $expectedByPath.ContainsKey($path)) {
            Write-Fail "unexpected protected Cartographer state file appeared: $path"
        }
    }

    if ($script:FailureCount -eq $startFailures) {
        Write-Pass "protected Cartographer state is byte-for-byte unchanged"
    }
}

function Get-AutoCheckPreference([string]$RootPath) {
    $path = Join-Path (Resolve-StateRoot $RootPath) "update.properties"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return $null
    }

    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match "^\s*autoCheck\s*[:=]\s*(.*?)\s*$") {
            return $Matches[1].Trim().ToLowerInvariant()
        }
    }
    return $null
}

function Assert-AutoCheckPreferencePreserved(
    $BaselineValue,
    [string]$RootPath
) {
    if ($null -eq $BaselineValue) {
        Write-Info "autoCheck was not explicitly persisted at baseline"
        return
    }

    $current = Get-AutoCheckPreference $RootPath
    if ([string]$current -eq [string]$BaselineValue) {
        Write-Pass "autoCheck preference is preserved"
    } else {
        Write-Fail "autoCheck preference changed (baseline '$BaselineValue', current '$current')"
    }
}


function Get-OptionalProperty(
    $Object,
    [string]$Name
) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return ""
    }
    return [string]$property.Value
}

function Get-InstallEntries {
    $roots = @(
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"
    )

    $entries = @()
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }

        foreach ($key in Get-ChildItem -LiteralPath $root -ErrorAction SilentlyContinue) {
            try {
                $properties = Get-ItemProperty -LiteralPath $key.PSPath -ErrorAction Stop
                $displayName = Get-OptionalProperty $properties "DisplayName"
                if ($displayName -ne $applicationName) {
                    continue
                }

                $entries += [ordered]@{
                    RegistryPath = $key.PSPath
                    DisplayName = Get-OptionalProperty $properties "DisplayName"
                    DisplayVersion = Get-OptionalProperty $properties "DisplayVersion"
                    InstallLocation = Get-OptionalProperty $properties "InstallLocation"
                    UninstallString = Get-OptionalProperty $properties "UninstallString"
                }
            } catch {
                Write-WarningMessage "could not read uninstall entry: $($key.PSPath)"
            }
        }
    }

    return @($entries)
}

function Test-VersionMatches(
    [string]$DisplayVersion,
    [string]$ExpectedVersion
) {
    if ([string]::IsNullOrWhiteSpace($DisplayVersion)) {
        return $false
    }
    if ($DisplayVersion.Trim() -eq $ExpectedVersion) {
        return $true
    }

    try {
        $display = [Version]$DisplayVersion.Trim()
        $expected = [Version]$ExpectedVersion
        return (
            $display.Major -eq $expected.Major -and
            $display.Minor -eq $expected.Minor -and
            $display.Build -eq $expected.Build
        )
    } catch {
        return $false
    }
}

function Assert-InstalledVersion([string]$ExpectedVersion) {
    $entries = @(Get-InstallEntries)
    if ($entries.Count -ne 1) {
        Write-Fail "expected exactly one installed VS Cartographer entry, found $($entries.Count)"
        return $null
    }

    $entry = $entries[0]
    if (Test-VersionMatches $entry.DisplayVersion $ExpectedVersion) {
        Write-Pass "installed product version matches $ExpectedVersion"
    } else {
        Write-Fail "installed product version '$($entry.DisplayVersion)' does not match $ExpectedVersion"
    }

    return $entry
}

function Get-ShortcutPaths {
    $desktop = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::Desktop
    )
    $programs = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::Programs
    )

    return [ordered]@{
        Desktop = Join-Path $desktop "$applicationName.lnk"
        StartMenu = Join-Path (
            Join-Path $programs $applicationName
        ) "$applicationName.lnk"
    }
}

function Get-ShortcutTarget([string]$ShortcutPath) {
    $shell = $null
    try {
        $shell = New-Object -ComObject WScript.Shell
        $shortcut = $shell.CreateShortcut($ShortcutPath)
        return [string]$shortcut.TargetPath
    } finally {
        if ($null -ne $shell) {
            [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell)
        }
    }
}

function Assert-ShortcutsPresent {
    $paths = Get-ShortcutPaths
    $targets = @()

    foreach ($name in @("Desktop", "StartMenu")) {
        $path = [string]$paths[$name]
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Write-Fail "$name shortcut is missing: $path"
            continue
        }

        $target = Get-ShortcutTarget $path
        if ([string]::IsNullOrWhiteSpace($target)) {
            Write-Fail "$name shortcut has no target"
            continue
        }
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            Write-Fail "$name shortcut target does not exist: $target"
            continue
        }
        if (-not [IO.Path]::GetFileName($target).Equals(
            "$applicationName.exe",
            [StringComparison]::OrdinalIgnoreCase
        )) {
            Write-Fail "$name shortcut target is not $applicationName.exe: $target"
            continue
        }

        Write-Pass "$name shortcut resolves to the packaged launcher"
        $targets += [IO.Path]::GetFullPath($target)
    }

    if ($targets.Count -eq 2 -and -not [StringComparer]::OrdinalIgnoreCase.Equals(
        $targets[0],
        $targets[1]
    )) {
        Write-Fail "Desktop and Start Menu shortcuts point to different launchers"
    }

    if ($targets.Count -gt 0) {
        return $targets[0]
    }
    return $null
}

function Assert-ShortcutsAbsent {
    $paths = Get-ShortcutPaths
    foreach ($name in @("Desktop", "StartMenu")) {
        $path = [string]$paths[$name]
        if (Test-Path -LiteralPath $path) {
            Write-Fail "$name shortcut remains after uninstall: $path"
        } else {
            Write-Pass "$name shortcut is absent after uninstall"
        }
    }
}

function Write-JsonFile(
    [string]$Path,
    $Value
) {
    Ensure-ValidationDirectory
    $Value |
        ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath $Path -Encoding UTF8
}

function Read-Baseline {
    if (-not (Test-Path -LiteralPath $baselinePath -PathType Leaf)) {
        throw "Stage 5 baseline is missing: $baselinePath. Run BeforeUpgrade first."
    }
    return Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json
}

function Invoke-BeforeUpgrade {
    Assert-UpgradeDirection $OldVersion $TargetVersion

    $save = Resolve-SaveFile $SavePath
    $root = Resolve-StateRoot $StateRoot
    $state = @(Get-ProtectedStateSnapshot $root)
    if ($state.Count -eq 0) {
        Write-Fail "no protected Cartographer state exists; create a HOME or user marker before taking the baseline"
    } else {
        Write-Pass "protected Cartographer state baseline contains $($state.Count) file(s)"
    }

    $entry = Assert-InstalledVersion $OldVersion
    $launcher = Assert-ShortcutsPresent
    $saveSnapshot = Get-SaveSnapshot $save
    $autoCheck = Get-AutoCheckPreference $root

    if ($saveSnapshot.WalExists) {
        Write-WarningMessage "SQLite WAL sidecar already exists before campaign"
    } else {
        Write-Pass "SQLite WAL sidecar is absent before campaign"
    }
    if ($saveSnapshot.ShmExists) {
        Write-WarningMessage "SQLite SHM sidecar already exists before campaign"
    } else {
        Write-Pass "SQLite SHM sidecar is absent before campaign"
    }

    if ($script:FailureCount -gt 0) {
        throw "BeforeUpgrade validation failed; baseline was not written."
    }

    $baseline = [ordered]@{
        SchemaVersion = 1
        RecordedAtUtc = [DateTime]::UtcNow.ToString(
            "o",
            [Globalization.CultureInfo]::InvariantCulture
        )
        OldVersion = $OldVersion
        TargetVersion = $TargetVersion
        Save = $saveSnapshot
        StateRoot = $root
        ProtectedState = $state
        AutoCheck = $autoCheck
        InstalledEntry = $entry
        LauncherPath = $launcher
    }
    Write-JsonFile $baselinePath $baseline

    Write-Pass "Stage 5 baseline written: $baselinePath"
    Write-Host "SUMMARY: PASS - ready for controlled old-to-new update campaign"
}

function Invoke-AfterUpgrade {
    $baseline = Read-Baseline
    $save = Resolve-SaveFile $SavePath

    Assert-SaveUnchanged $save $baseline.Save
    Assert-ProtectedStateUnchanged $baseline.ProtectedState ([string]$baseline.StateRoot)
    Assert-AutoCheckPreferencePreserved $baseline.AutoCheck ([string]$baseline.StateRoot)

    $entry = Assert-InstalledVersion ([string]$baseline.TargetVersion)
    $launcher = Assert-ShortcutsPresent

    $report = [ordered]@{
        RecordedAtUtc = [DateTime]::UtcNow.ToString(
            "o",
            [Globalization.CultureInfo]::InvariantCulture
        )
        ExpectedVersion = [string]$baseline.TargetVersion
        InstalledEntry = $entry
        LauncherPath = $launcher
        AutomatedChecksPassed = ($script:FailureCount -eq 0)
    }
    Write-JsonFile $afterUpgradePath $report

    if ($script:FailureCount -eq 0) {
        Write-Host "SUMMARY: PASS - automated post-upgrade checks passed"
    } else {
        Write-Host "SUMMARY: FAIL - $($script:FailureCount) post-upgrade check(s) failed"
    }
}

function Invoke-AfterUninstall {
    $baseline = Read-Baseline
    $save = Resolve-SaveFile $SavePath

    Assert-SaveUnchanged $save $baseline.Save
    Assert-ProtectedStateUnchanged $baseline.ProtectedState ([string]$baseline.StateRoot)
    Assert-AutoCheckPreferencePreserved $baseline.AutoCheck ([string]$baseline.StateRoot)

    $entries = @(Get-InstallEntries)
    if ($entries.Count -eq 0) {
        Write-Pass "VS Cartographer uninstall registration is absent"
    } else {
        Write-Fail "VS Cartographer still has $($entries.Count) uninstall registration(s)"
    }

    Assert-ShortcutsAbsent

    $launcher = [string]$baseline.LauncherPath
    if (Test-Path -LiteralPath $afterUpgradePath -PathType Leaf) {
        $upgrade = Get-Content -LiteralPath $afterUpgradePath -Raw | ConvertFrom-Json
        if (-not [string]::IsNullOrWhiteSpace([string]$upgrade.LauncherPath)) {
            $launcher = [string]$upgrade.LauncherPath
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($launcher)) {
        if (Test-Path -LiteralPath $launcher) {
            Write-Fail "installed launcher remains after uninstall: $launcher"
        } else {
            Write-Pass "installed launcher is removed after uninstall"
        }
    }

    $report = [ordered]@{
        RecordedAtUtc = [DateTime]::UtcNow.ToString(
            "o",
            [Globalization.CultureInfo]::InvariantCulture
        )
        AutomatedChecksPassed = ($script:FailureCount -eq 0)
    }
    Write-JsonFile $afterUninstallPath $report

    if ($script:FailureCount -eq 0) {
        Write-Host "SUMMARY: PASS - automated uninstall checks passed"
    } else {
        Write-Host "SUMMARY: FAIL - $($script:FailureCount) uninstall check(s) failed"
    }
}

function Resolve-StagedInstaller([string]$Version) {
    Assert-StableVersion $Version "-TargetVersion"
    $root = Resolve-StateRoot $StateRoot
    return Join-Path (
        Join-Path (
            Join-Path $root "updates"
        ) $Version
    ) "VS-Cartographer-Setup-$Version.exe"
}

function Invoke-TamperInstaller {
    $installer = Resolve-StagedInstaller $TargetVersion
    if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
        throw "Staged installer is missing: $installer"
    }

    $backup = "$installer.stage5-backup"
    if (Test-Path -LiteralPath $backup) {
        throw "Stage 5 backup already exists: $backup. Restore it before tampering again."
    }

    $item = Get-Item -LiteralPath $installer
    if ($item.Length -le 0) {
        throw "Cannot tamper an empty installer: $installer"
    }

    Copy-Item -LiteralPath $installer -Destination $backup -ErrorAction Stop
    $beforeHash = (
        Get-FileHash -LiteralPath $installer -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    $stream = $null
    try {
        $stream = [IO.File]::Open(
            $installer,
            [IO.FileMode]::Open,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None
        )
        [void]$stream.Seek(-1, [IO.SeekOrigin]::End)
        $original = $stream.ReadByte()
        if ($original -lt 0) {
            throw "Cannot read installer byte for tamper validation."
        }
        [void]$stream.Seek(-1, [IO.SeekOrigin]::End)
        $stream.WriteByte([byte]($original -bxor 1))
        $stream.Flush()
    } finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
    }

    $afterHash = (
        Get-FileHash -LiteralPath $installer -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $afterLength = (Get-Item -LiteralPath $installer).Length

    if ($beforeHash -eq $afterHash) {
        throw "Tamper helper did not change installer SHA-256."
    }
    if ([int64]$afterLength -ne [int64]$item.Length) {
        throw "Tamper helper unexpectedly changed installer length."
    }

    Write-Pass "staged installer was changed without changing its length"
    Write-Info "backup: $backup"
    Write-Info "Keep the old application open in Ready state."
    Write-Info "Use Restart & update now; in-process verification must reject the installer."
    Write-Info "After the failure check, run RestoreInstaller."
    Write-Host "SUMMARY: PASS - tampered installer prepared"
}

function Invoke-RestoreInstaller {
    $installer = Resolve-StagedInstaller $TargetVersion
    $backup = "$installer.stage5-backup"
    if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) {
        throw "Stage 5 installer backup is missing: $backup"
    }

    $backupItem = Get-Item -LiteralPath $backup
    $backupHash = (
        Get-FileHash -LiteralPath $backup -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    Move-Item -LiteralPath $backup -Destination $installer -Force

    $restoredItem = Get-Item -LiteralPath $installer
    $restoredHash = (
        Get-FileHash -LiteralPath $installer -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    if ([int64]$restoredItem.Length -ne [int64]$backupItem.Length) {
        throw "Restored installer length does not match the Stage 5 backup."
    }
    if ($restoredHash -ne $backupHash) {
        throw "Restored installer SHA-256 does not match the Stage 5 backup."
    }

    Write-Pass "original staged installer restored and verified"
    Write-Host "SUMMARY: PASS - staged installer restored"
}

try {
    Ensure-Windows
    Ensure-ValidationDirectory

    switch ($Mode) {
        "BeforeUpgrade" { Invoke-BeforeUpgrade }
        "AfterUpgrade" { Invoke-AfterUpgrade }
        "AfterUninstall" { Invoke-AfterUninstall }
        "TamperInstaller" { Invoke-TamperInstaller }
        "RestoreInstaller" { Invoke-RestoreInstaller }
    }

    if ($script:FailureCount -gt 0) {
        exit 1
    }
    exit 0
} catch {
    Write-Fail $_.Exception.Message
    Write-Host "SUMMARY: FAIL - Stage 5 validation could not complete"
    exit 1
}
