param([string]$AppDirectory)
$ErrorActionPreference = 'Stop'
if ($AppDirectory) {
    $appRoot = [IO.Path]::GetFullPath($AppDirectory)
    if (-not (Test-Path -LiteralPath (Join-Path $appRoot 'app/nhatrohomu.jar'))) { throw 'Choose the folder containing NhaTroHomu.exe.' }
    $destination = Join-Path $appRoot 'app/ocr'
} elseif (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'app/nhatrohomu.jar')) {
    $destination = Join-Path $PSScriptRoot 'app/ocr'
} else {
    $destination = Join-Path $PSScriptRoot 'lib/ocr'
}
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$components = @(
    @('io/github/hzkitty/rapidocr4j/1.0.3/rapidocr4j-1.0.3.jar', 'b66c6cfc4b35aecc365118b3fe923c9db9aa954206696070de09e0c16a0ee215'),
    @('com/microsoft/onnxruntime/onnxruntime/1.18.0/onnxruntime-1.18.0.jar', '2733ead3dd8d07757c3f4c611e41e87d8dbd50130d6ff6f77a63407b25f93d30'),
    @('org/openpnp/opencv/4.6.0-0/opencv-4.6.0-0.jar', '072041a501f3788b0f142249fb0241cee480ebeebc9e54a6477e8cd137a83117')
)
foreach ($component in $components) {
    $file = Join-Path $destination ([IO.Path]::GetFileName($component[0]))
    if (Test-Path -LiteralPath $file) {
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $component[1]) { throw ('Checksum failed: ' + $file) }
        continue
    }
    $download = $file + '.download'
    try {
        Invoke-WebRequest -Uri ('https://repo.maven.apache.org/maven2/' + $component[0]) -OutFile $download
        if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant() -ne $component[1]) { throw ('Checksum failed: ' + $download) }
        Move-Item -LiteralPath $download -Destination $file
    } finally {
        if (Test-Path -LiteralPath $download) { Remove-Item -LiteralPath $download -Force }
    }
}
Write-Output ('RapidOCR installed (offline after installation): ' + $destination)
