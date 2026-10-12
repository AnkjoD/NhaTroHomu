param([switch]$CompileOnly, [switch]$Test, [switch]$Smoke, [switch]$Demo)

$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$classesDirectory = Join-Path $projectDirectory 'build/classes'
$sourceDirectory = Join-Path $projectDirectory 'src/main/java'
$libraryDirectory = Join-Path $projectDirectory 'lib'
New-Item -ItemType Directory -Force -Path $libraryDirectory | Out-Null
$dependencies = @('com/github/librepdf/openpdf/2.0.3/openpdf-2.0.3.jar','com/formdev/flatlaf/3.6.2/flatlaf-3.6.2.jar','org/xerial/sqlite-jdbc/3.50.3.0/sqlite-jdbc-3.50.3.0.jar','org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar','org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar')
$expectedHashes = @{}
Get-Content -LiteralPath (Join-Path $projectDirectory 'dependencies.sha256') | ForEach-Object {
    if ($_ -match '^([a-f0-9]{64})  ([a-zA-Z0-9.-]+\.jar)$') { $expectedHashes[$Matches[2]] = $Matches[1] }
}
foreach ($dependency in $dependencies) {
    $libraryFile = Join-Path $libraryDirectory ([System.IO.Path]::GetFileName($dependency))
    if (-not (Test-Path -LiteralPath $libraryFile)) {
        $downloadFile = $libraryFile + '.download'
        Invoke-WebRequest -Uri ('https://repo.maven.apache.org/maven2/' + $dependency) -OutFile $downloadFile
        Move-Item -LiteralPath $downloadFile -Destination $libraryFile
    }
    $dependencyName = [System.IO.Path]::GetFileName($libraryFile)
    $actualHash = (Get-FileHash -LiteralPath $libraryFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $expectedHashes.ContainsKey($dependencyName) -or $actualHash -ne $expectedHashes[$dependencyName]) {
        throw ('Dependency checksum failed: ' + $dependencyName)
    }
}

New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null
$absoluteClasses = [System.IO.Path]::GetFullPath($classesDirectory)
$expectedClasses = [System.IO.Path]::GetFullPath((Join-Path $projectDirectory 'build/classes'))
if ($absoluteClasses -ne $expectedClasses) { throw 'Unexpected generated output path.' }
Get-ChildItem -LiteralPath $classesDirectory -File -Recurse | Remove-Item -Force
$sourceFiles = @(Get-ChildItem -LiteralPath $sourceDirectory -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName })

& javac -encoding UTF-8 --release 21 -cp "$libraryDirectory/*" -d $classesDirectory @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Biên dịch thất bại. Xem lỗi phía trên.' }
Copy-Item -Path (Join-Path $projectDirectory 'src/main/resources/*') -Destination $classesDirectory -Recurse -Force
$classPath = "$classesDirectory;$libraryDirectory/*"

if ($Test) {
    $testClasses = Join-Path $projectDirectory 'build/test-classes'
    New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
    $testFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'src/test/java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac -encoding UTF-8 --release 21 -cp $classPath -d $testClasses @testFiles
    if ($LASTEXITCODE -ne 0) { throw 'Không biên dịch được kiểm thử.' }
    & java -ea -cp "$testClasses;$classPath" vn.nhatrohomu.persistence.sqlite.AllChecks
    if ($LASTEXITCODE -ne 0) { throw 'Regression checks failed.' }
    exit 0
}

if ($Smoke) {
    $smokeDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('sotro-smoke-' + [guid]::NewGuid())
    & java "-Dsotro.data=$smokeDirectory" -cp $classPath vn.nhatrohomu.app.Main --smoke
    if ($LASTEXITCODE -ne 0) { throw 'Kiểm tra giao diện thất bại.' }
    exit 0
}

if (-not $CompileOnly) {
    $appArguments = @()
    if ($Demo) { $appArguments += "--demo" }
    & java -Xms32m -Xmx256m -cp $classPath vn.nhatrohomu.app.Main @appArguments
    if ($LASTEXITCODE -ne 0) { throw 'Ứng dụng kết thúc với lỗi.' }
}
