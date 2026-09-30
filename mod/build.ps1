# build.ps1 -- QQ Watchdog, LSPosed module build (libxposed API 102).
# Same hand-rolled chain as qq-module: javac -> jar -> d8 -> aapt2 -> inject dex + META-INF/xposed -> zipalign -> apksigner
# NOTE: ASCII only. PowerShell 5.1 reads a BOM-less .ps1 as GBK; non-ASCII bytes corrupt the parse.

$ErrorActionPreference = 'Stop'

$root = 'D:\deepseek\qq-watchdog\mod'
$sdk  = 'D:\deepseek\_work\android-sdk'
$bt   = "$sdk\build-tools\37.0.0"
$aj   = "$sdk\platforms\android-37.0\android.jar"
$jh   = 'C:\Users\98380\AppData\Roaming\CherryStudio\Toolchain\mise\installs\java\21.0.2'
$env:JAVA_HOME = $jh

$apiJar = "$root\libs\102.0.0-api-102.0.0\classes.jar"
$ifJar  = "$root\libs\102.0.0-interface-102.0.0\classes.jar"
$svcJar = "$root\libs\102.0.0-service-102.0.0\classes.jar"
foreach ($j in @($apiJar, $ifJar, $svcJar)) {
    if (-not (Test-Path $j)) { throw "missing libxposed artifact: $j" }
}

$out = "$root\build"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$out\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$out\dex" | Out-Null

function RunExe($file, $argv, $tag) {
    $o = "$out\$tag.out"
    $e = "$out\$tag.err"
    $p = Start-Process -FilePath $file -ArgumentList $argv -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $o -RedirectStandardError $e
    if ($p.ExitCode -ne 0) {
        Write-Host "--- $tag FAILED (exit $($p.ExitCode)) ---"
        if (Test-Path $o) { Get-Content $o -Raw | Write-Host }
        if (Test-Path $e) { Get-Content $e -Raw | Write-Host }
        throw "$tag failed"
    }
    if (Test-Path $e) {
        $s = Get-Content $e -Raw
        if ($s -and $s.Trim().Length -gt 0) {
            Write-Host "[$tag stderr] " + $s.Substring(0, [Math]::Min(400, $s.Length))
        }
    }
    return $p.ExitCode
}

# 1) signing key (its own, separate from the other two projects)
$ks = "$root\watchmod-key.jks"
if (-not (Test-Path $ks)) {
    Write-Host "[1/6] generating signing key"
    RunExe "$jh\bin\keytool.exe" @(
        '-genkeypair', '-keystore', $ks, '-alias', 'qqwatchmod',
        '-storepass', 'qqwatchmod123', '-keypass', 'qqwatchmod123',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=qqwatchmod,O=self,C=CN'
    ) 'keytool' | Out-Null
} else {
    Write-Host "[1/6] signing key exists"
}

# 2) javac
Write-Host "[2/6] javac"
$srcs = @(Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$cp   = "$aj;$apiJar;$ifJar;$svcJar"
$javacArgs = @('--release', '8', '-encoding', 'UTF-8', '-classpath', $cp, '-d', "$out\classes") + $srcs
RunExe "$jh\bin\javac.exe" $javacArgs 'javac' | Out-Null

# 3) d8
Write-Host "[3/6] d8"
$classesJar = "$out\classes.jar"
if (Test-Path $classesJar) { Remove-Item $classesJar -Force }
RunExe "$jh\bin\jar.exe" @('cf', $classesJar, '-C', "$out\classes", '.') 'jar' | Out-Null
RunExe "$bt\d8.bat" @('--release', '--min-api', '26', '--lib', $aj, '--lib', $apiJar,
    '--output', "$out\dex", $classesJar, $ifJar, $svcJar) 'd8' | Out-Null
if (-not (Test-Path "$out\dex\classes.dex")) { throw "d8 produced no classes.dex" }

# 4) aapt2 link
Write-Host "[4/6] aapt2 link"
RunExe "$bt\aapt2.exe" @('compile', '--dir', "$root\res", '-o', "$out\res.zip") 'aapt2compile' | Out-Null
RunExe "$bt\aapt2.exe" @(
    'link', '-o', "$out\base.apk", '-I', $aj,
    '--manifest', "$root\AndroidManifest.xml",
    '--min-sdk-version', '26', '--target-sdk-version', '32',
    "$out\res.zip"
) 'aapt2link' | Out-Null

# 5) inject classes.dex + META-INF/xposed/*
Write-Host "[5/6] packaging dex + META-INF/xposed"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$staging = "$out\staging.apk"
Copy-Item "$out\base.apk" $staging -Force
$zip = [System.IO.Compression.ZipFile]::Open($staging, [System.IO.Compression.ZipArchiveMode]::Update)
$items = @(
    @{ n = 'classes.dex';                    f = "$out\dex\classes.dex" },
    @{ n = 'META-INF/xposed/module.prop';    f = "$root\xposed\module.prop" },
    @{ n = 'META-INF/xposed/java_init.list'; f = "$root\xposed\java_init.list" },
    @{ n = 'META-INF/xposed/scope.list';     f = "$root\xposed\scope.list" }
)
foreach ($it in $items) {
    $ex = $zip.GetEntry($it.n)
    if ($ex) { $ex.Delete() }
    $ne = $zip.CreateEntry($it.n, [System.IO.Compression.CompressionLevel]::Optimal)
    $os = $ne.Open()
    $bytes = [System.IO.File]::ReadAllBytes($it.f)
    $os.Write($bytes, 0, $bytes.Length)
    $os.Close()
}
$zip.Dispose()

# 6) zipalign + sign + verify
Write-Host "[6/6] zipalign + sign"
RunExe "$bt\zipalign.exe" @('-f', '-p', '4', $staging, "$out\aligned.apk") 'zipalign' | Out-Null
RunExe "$bt\apksigner.bat" @(
    'sign', '--ks', $ks, '--ks-pass', 'pass:qqwatchmod123', '--key-pass', 'pass:qqwatchmod123',
    '--ks-key-alias', 'qqwatchmod', '--out', "$out\qqwatchmod.apk", "$out\aligned.apk"
) 'apksigner' | Out-Null
RunExe "$bt\apksigner.bat" @('verify', '--print-certs', "$out\qqwatchmod.apk") 'verify' | Out-Null

Write-Host "BUILD OK"
Get-Item "$out\qqwatchmod.apk" | Select-Object FullName, Length | Format-Table -AutoSize
