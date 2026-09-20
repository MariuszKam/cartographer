[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Artifacts", "Before", "After")]
    [string]$Mode,

    [Parameter(Mandatory = $false)]
    [string]$SavePath,

    [Parameter(Mandatory = $false)]
    [string]$Version
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:FailureCount = 0
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$validationRoot = Join-Path $repositoryRoot "build\release-validation"
$baselinePath = Join-Path $validationRoot "save-baseline.json"

function Write-Pass([string]$Message) {
    Write-Output "PASS: $Message"
}

function Write-Info([string]$Message) {
    Write-Output "INFO: $Message"
}

function Write-WarningMessage([string]$Message) {
    Write-Output "WARNING: $Message"
}

function Write-Fail([string]$Message) {
    $script:FailureCount++
    Write-Output "FAIL: $Message"
}

function Resolve-ArtifactVersion {
    if (-not [string]::IsNullOrWhiteSpace($Version)) {
        if ($Version -notmatch "^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$") {
            throw "-Version must use stable MAJOR.MINOR.PATCH form: $Version"
        }
        return $Version
    }

    $propertiesPath = Join-Path $repositoryRoot "gradle.properties"
    if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
        throw "Cannot resolve release version because gradle.properties is missing: $propertiesPath"
    }

    $matches = @(Get-Content -LiteralPath $propertiesPath | Where-Object {
        $_ -match "^version=(.+)$"
    })
    if ($matches.Count -ne 1) {
        throw "gradle.properties must contain exactly one version=<MAJOR.MINOR.PATCH> entry"
    }

    $resolved = ($matches[0] -replace "^version=", "").Trim()
    if ($resolved -notmatch "^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$") {
        throw "Project version must use stable MAJOR.MINOR.PATCH form: $resolved"
    }
    return $resolved
}

function Ensure-ValidationDirectory {
    if (-not (Test-Path -LiteralPath $validationRoot -PathType Container)) {
        New-Item -ItemType Directory -Path $validationRoot -Force | Out-Null
    }
}

function Resolve-SaveFile([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "-SavePath is required for Before and After modes."
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    $item = Get-Item -LiteralPath $resolved.Path -ErrorAction Stop
    if (-not ($item -is [System.IO.FileInfo])) {
        throw "Save path is not a file: $($item.FullName)"
    }
    if (-not $item.Extension.Equals(".vcdbs", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Save path must have a .vcdbs extension: $($item.FullName)"
    }
    return $item
}

function Get-SidecarState([string]$SaveFilePath) {
    return [ordered]@{
        WalPath = "$SaveFilePath-wal"
        ShmPath = "$SaveFilePath-shm"
        WalExists = Test-Path -LiteralPath "$SaveFilePath-wal"
        ShmExists = Test-Path -LiteralPath "$SaveFilePath-shm"
    }
}

function Get-SaveHash([System.IO.FileInfo]$SaveFile) {
    return (Get-FileHash -LiteralPath $SaveFile.FullName -Algorithm SHA256).Hash
}

function Invoke-BeforeValidation {
    $save = Resolve-SaveFile $SavePath
    $sidecars = Get-SidecarState $save.FullName
    $hash = Get-SaveHash $save
    Ensure-ValidationDirectory

    $baseline = [ordered]@{
        SavePath = $save.FullName
        Sha256 = $hash
        Length = [int64]$save.Length
        RecordedAtUtc = [DateTime]::UtcNow.ToString("o", [Globalization.CultureInfo]::InvariantCulture)
        WalExists = [bool]$sidecars.WalExists
        ShmExists = [bool]$sidecars.ShmExists
    }
    $baseline | ConvertTo-Json | Set-Content -LiteralPath $baselinePath -Encoding UTF8

    Write-Pass "save exists and has a .vcdbs extension"
    Write-Info "save path: $($save.FullName)"
    Write-Info "save length: $($save.Length) bytes"
    Write-Pass "baseline SHA-256: $hash"
    if ($sidecars.WalExists) {
        Write-WarningMessage "SQLite WAL sidecar already exists: $($sidecars.WalPath)"
    } else {
        Write-Info "SQLite WAL sidecar is absent before validation"
    }
    if ($sidecars.ShmExists) {
        Write-WarningMessage "SQLite SHM sidecar already exists: $($sidecars.ShmPath)"
    } else {
        Write-Info "SQLite SHM sidecar is absent before validation"
    }
    Write-Pass "baseline written outside the save: $baselinePath"
    Write-Output "SUMMARY: Before validation recorded"
}

function Invoke-AfterValidation {
    if (-not (Test-Path -LiteralPath $baselinePath -PathType Leaf)) {
        throw "Baseline is missing: $baselinePath. Run Before mode first."
    }

    $save = Resolve-SaveFile $SavePath
    $baseline = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json
    if (-not ([StringComparer]::OrdinalIgnoreCase.Equals($save.FullName, [string]$baseline.SavePath))) {
        throw "Save path does not match the baseline. Expected '$($baseline.SavePath)', got '$($save.FullName)'."
    }

    $currentHash = Get-SaveHash $save
    $currentLength = [int64]$save.Length
    $sidecars = Get-SidecarState $save.FullName

    if ($currentHash -eq [string]$baseline.Sha256) {
        Write-Pass "save SHA-256 unchanged: $currentHash"
    } else {
        Write-Fail "save SHA-256 changed (baseline $($baseline.Sha256), current $currentHash)"
    }
    if ($currentLength -eq [int64]$baseline.Length) {
        Write-Pass "save file size unchanged: $currentLength bytes"
    } else {
        Write-Fail "save file size changed (baseline $($baseline.Length), current $currentLength)"
    }

    if ($sidecars.WalExists -and -not [bool]$baseline.WalExists) {
        Write-Fail "new SQLite WAL sidecar detected: $($sidecars.WalPath)"
    } elseif ($sidecars.WalExists) {
        Write-Info "SQLite WAL sidecar existed before validation and remains present"
    } else {
        Write-Pass "no new SQLite WAL sidecar"
    }
    if ($sidecars.ShmExists -and -not [bool]$baseline.ShmExists) {
        Write-Fail "new SQLite SHM sidecar detected: $($sidecars.ShmPath)"
    } elseif ($sidecars.ShmExists) {
        Write-Info "SQLite SHM sidecar existed before validation and remains present"
    } else {
        Write-Pass "no new SQLite SHM sidecar"
    }

    if ($script:FailureCount -eq 0) {
        Write-Output "SUMMARY: PASS - save integrity unchanged"
    } else {
        Write-Output "SUMMARY: FAIL - $($script:FailureCount) save integrity check(s) failed"
    }
}

function Assert-ArtifactFile([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        $script:FailureCount++
        Write-Host "FAIL: $Description is missing: $Path"
        return $null
    }
    $item = Get-Item -LiteralPath $Path
    if ($item.Length -le 0) {
        $script:FailureCount++
        Write-Host "FAIL: $Description is empty: $Path"
        return $null
    }
    Write-Host "PASS: $Description exists ($($item.Length) bytes)"
    return $item
}

function Assert-ArtifactDirectory([string]$Path, [string]$Description) {
    if (Test-Path -LiteralPath $Path -PathType Container) {
        Write-Host "PASS: $Description exists"
        return $true
    }
    $script:FailureCount++
    Write-Host "FAIL: $Description is missing: $Path"
    return $false
}

function Test-ZipDirectory([string[]]$Entries, [string]$DirectoryName) {
    $prefix = "$DirectoryName/"
    return @($Entries | Where-Object {
        $_.Equals($DirectoryName, [System.StringComparison]::OrdinalIgnoreCase) -or
        $_.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)
    }).Count -gt 0
}

function Invoke-ArtifactValidation {
    Ensure-ValidationDirectory
    $artifactVersion = Resolve-ArtifactVersion
    $buildRoot = Join-Path $repositoryRoot "build"
    $appImageRoot = Join-Path $buildRoot "jpackage\app-image\VS Cartographer"
    $portableZipPath = Join-Path $buildRoot "distributions\VS-Cartographer-$artifactVersion-win-x64.zip"
    $installerPath = Join-Path $buildRoot "distributions\VS-Cartographer-Setup-$artifactVersion.exe"
    Write-Info "release version: $artifactVersion"

    $null = Assert-ArtifactFile (Join-Path $appImageRoot "VS Cartographer.exe") "native app-image launcher"
    $null = Assert-ArtifactDirectory (Join-Path $appImageRoot "app") "app-image app directory"
    $null = Assert-ArtifactDirectory (Join-Path $appImageRoot "runtime") "app-image runtime directory"
    $portableZip = Assert-ArtifactFile $portableZipPath "portable ZIP"
    $installer = Assert-ArtifactFile $installerPath "Windows installer"

    if ($null -ne $portableZip) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = $null
        try {
            $archive = [System.IO.Compression.ZipFile]::OpenRead($portableZip.FullName)
            $entries = @($archive.Entries | ForEach-Object {
                $_.FullName.Replace('\', '/').TrimStart('/')
            })
            $expectedFiles = @(
                "VS Cartographer/VS Cartographer.exe"
            )
            foreach ($entry in $expectedFiles) {
                if (@($entries | Where-Object {
                    $_.Equals($entry, [System.StringComparison]::OrdinalIgnoreCase)
                }).Count -gt 0) {
                    Write-Pass "portable ZIP contains $entry"
                } else {
                    Write-Fail "portable ZIP is missing $entry"
                }
            }
            foreach ($directory in @("VS Cartographer", "VS Cartographer/app", "VS Cartographer/runtime")) {
                if (Test-ZipDirectory $entries $directory) {
                    Write-Pass "portable ZIP contains $directory/"
                } else {
                    Write-Fail "portable ZIP is missing $directory/"
                }
            }
            Write-Pass "portable ZIP structure is valid"
        } finally {
            if ($null -ne $archive) {
                $archive.Dispose()
            }
        }
    }

    if ($null -ne $installer) {
        try {
            $signature = Get-AuthenticodeSignature -LiteralPath $installer.FullName
            if ($signature.Status -eq "NotSigned") {
                Write-Info "installer is unsigned (expected for this release stage)"
            } else {
                Write-Info "installer signature status: $($signature.Status)"
            }
        } catch {
            Write-Info "installer signature status could not be read; signing is not required"
        }
        try {
            $fileVersion = $installer.VersionInfo.FileVersion
            if ([string]::IsNullOrWhiteSpace($fileVersion)) {
                Write-Info "installer file version metadata is unavailable"
            } else {
                Write-Info "installer file version metadata: $fileVersion"
            }
        } catch {
            Write-Info "installer version metadata could not be read"
        }
    }

    if (($null -ne $portableZip) -and ($null -ne $installer)) {
        $checksumLines = @(
            "$(Get-FileHash -LiteralPath $portableZip.FullName -Algorithm SHA256 | Select-Object -ExpandProperty Hash)  $($portableZip.Name)",
            "$(Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256 | Select-Object -ExpandProperty Hash)  $($installer.Name)"
        )
        $checksumPath = Join-Path $validationRoot "SHA256SUMS.txt"
        $checksumLines | Set-Content -LiteralPath $checksumPath -Encoding ASCII
        Write-Pass "artifact SHA-256 checksums written: $checksumPath"
    }

    if ($script:FailureCount -eq 0) {
        Write-Output "SUMMARY: PASS - release artifacts are structurally valid"
    } else {
        Write-Output "SUMMARY: FAIL - $($script:FailureCount) artifact check(s) failed"
    }
}

try {
    switch ($Mode) {
        "Artifacts" { Invoke-ArtifactValidation }
        "Before" { Invoke-BeforeValidation }
        "After" { Invoke-AfterValidation }
    }
    if ($script:FailureCount -gt 0) {
        exit 1
    }
    exit 0
} catch {
    Write-Fail $_.Exception.Message
    Write-Output "SUMMARY: FAIL - validation could not complete"
    exit 1
}
