param([switch]$Installer)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'run.ps1') -CompileOnly
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
$buildDirectory = Join-Path $PSScriptRoot 'build'
$inputDirectory = Join-Path $buildDirectory 'package-input'
$runtimeDirectory = Join-Path $buildDirectory 'runtime'
$releaseDirectory = Join-Path $PSScriptRoot 'release/v0.10.2'
New-Item -ItemType Directory -Force -Path $inputDirectory,$releaseDirectory | Out-Null
Copy-Item -Path (Join-Path $PSScriptRoot 'lib/*.jar') -Destination $inputDirectory -Force
$jars = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'lib') -Filter '*.jar' | ForEach-Object { $_.Name })
$manifestFile = Join-Path $buildDirectory 'MANIFEST.MF'
$manifestText = "Manifest-Version: 1.0`nMain-Class: vn.nhatrohomu.app.Main`nClass-Path: " + ($jars -join ' ') + "`n`n"
[System.IO.File]::WriteAllText($manifestFile,$manifestText,[System.Text.UTF8Encoding]::new($false))
& jar --create --file (Join-Path $inputDirectory 'nhatrohomu.jar') --manifest $manifestFile -C (Join-Path $buildDirectory 'classes') .
if ($LASTEXITCODE -ne 0) { throw 'Jar packaging failed.' }
if (-not (Test-Path -LiteralPath $runtimeDirectory)) {
    & jlink --add-modules java.desktop,java.sql,java.logging,java.management,java.naming,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtimeDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Runtime packaging failed.' }
}
$appDirectory = Join-Path $releaseDirectory 'NhaTroHomu'
if (Test-Path -LiteralPath $appDirectory) { throw 'release/NhaTroHomu already exists. Move the old release before rebuilding.' }
& jpackage --type app-image --name NhaTroHomu --app-version 0.10.2 --vendor 'NhaTroHomu contributors' --input $inputDirectory --main-jar nhatrohomu.jar --main-class vn.nhatrohomu.app.Main --runtime-image $runtimeDirectory --dest $releaseDirectory --java-options '-Xms32m' --java-options '-Xmx256m'
if ($LASTEXITCODE -ne 0) { throw 'Windows app packaging failed.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'LICENSE'),(Join-Path $PSScriptRoot 'THIRD-PARTY-NOTICES.md'),(Join-Path $PSScriptRoot 'README.md'),(Join-Path $PSScriptRoot 'dependencies.sha256') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'install-ocr.ps1') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'packaging/Mo-ban-demo.cmd') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docs') -Destination (Join-Path $appDirectory 'docs') -Recurse
Compress-Archive -Path $appDirectory -DestinationPath (Join-Path $releaseDirectory 'NhaTroHomu-0.10.2-windows-x64.zip')
if ($Installer) {
    # Windows installer requires WiX 3 tools on PATH. Portable app above does not.
    & jpackage --type exe --name NhaTroHomu --app-version 0.10.2 --vendor 'NhaTroHomu contributors' --app-image $appDirectory --dest $releaseDirectory --win-per-user-install --win-shortcut --win-menu
    if ($LASTEXITCODE -ne 0) { throw 'Installer failed; portable ZIP is still available. Install WiX 3 first.' }
}
Write-Output 'Ready: release/v0.10.2/NhaTroHomu/NhaTroHomu.exe and release/v0.10.2/NhaTroHomu-0.10.2-windows-x64.zip'
