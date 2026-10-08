# build.ps1 -- QQ Watchdog standalone app (NOT an Xposed module, so no libxposed jars).
# Toolchain: android-37 android.jar + build-tools 37.0.0 + JDK 21
# javac -> jar -> d8 -> aapt2 link (with assets) -> inject classes.dex -> zipalign -> apksigner
# NOTE: this file is intentionally ASCII-only. PowerShell 5.1 reads a BOM-less .ps1 as GBK,
#       and any non-ASCII byte in it corrupts the parse (see AGENTS.md section 8).

$ErrorActionPreference = 'Stop'
if (-not $env:QQWATCH_SIGN_PASSWORD) { throw 'Set QQWATCH_SIGN_PASSWORD locally before signing' }

$root = 'D:\deepseek\qq-watchdog'
$sdk  = 'D:\deepseek\_work\android-sdk'
$bt   = "$sdk\build-tools\37.0.0"
$aj   = "$sdk\platforms\android-37.0\android.jar"
$jh   = 'C:\Users\98380\AppData\Roaming\CherryStudio\Toolchain\mise\installs\java\21.0.2'
$env:JAVA_HOME = $jh

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

# 1) signing key (kept separate from the module's key)
$ks = "$root\watchdog-key.jks"
if (-not (Test-Path $ks)) {
    Write-Host "[1/6] generating signing key"
    RunExe "$jh\bin\keytool.exe" @(
        '-genkeypair', '-keystore', $ks, '-alias', 'qqwatchdog',
        '-storepass:env', 'QQWATCH_SIGN_PASSWORD', '-keypass:env', 'QQWATCH_SIGN_PASSWORD',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=qqwatchdog,O=self,C=CN'
    ) 'keytool' | Out-Null
} else {
    Write-Host "[1/6] signing key exists"
}

# 2) javac
Write-Host "[2/6] javac"
$srcs = @(Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$javacArgs = @('--release', '8', '-encoding', 'UTF-8', '-classpath', $aj, '-d', "$out\classes") + $srcs
RunExe "$jh\bin\javac.exe" $javacArgs 'javac' | Out-Null

# 3) d8
Write-Host "[3/6] d8"
$classesJar = "$out\classes.jar"
if (Test-Path $classesJar) { Remove-Item $classesJar -Force }
RunExe "$jh\bin\jar.exe" @('cf', $classesJar, '-C', "$out\classes", '.') 'jar' | Out-Null
RunExe "$bt\d8.bat" @('--release', '--min-api', '26', '--lib', $aj,
    '--output', "$out\dex", $classesJar) 'd8' | Out-Null
if (-not (Test-Path "$out\dex\classes.dex")) { throw "d8 produced no classes.dex" }

# 4) aapt2 link  (-A packs the assets dir: qqwatch.sh must be inside the apk)
Write-Host "[4/6] aapt2 link"
RunExe "$bt\aapt2.exe" @('compile', '--dir', "$root\res", '-o', "$out\res.zip") 'aapt2compile' | Out-Null
RunExe "$bt\aapt2.exe" @(
    'link', '-o', "$out\base.apk", '-I', $aj,
    '--manifest', "$root\AndroidManifest.xml",
    '--min-sdk-version', '26', '--target-sdk-version', '34',
    '-A', "$root\assets",
    "$out\res.zip"
) 'aapt2link' | Out-Null

# 5) inject classes.dex
Write-Host "[5/6] packaging dex"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$staging = "$out\staging.apk"
Copy-Item "$out\base.apk" $staging -Force
$zip = [System.IO.Compression.ZipFile]::Open($staging, [System.IO.Compression.ZipArchiveMode]::Update)
$ex = $zip.GetEntry('classes.dex')
if ($ex) { $ex.Delete() }
$ne = $zip.CreateEntry('classes.dex', [System.IO.Compression.CompressionLevel]::Optimal)
$os = $ne.Open()
$bytes = [System.IO.File]::ReadAllBytes("$out\dex\classes.dex")
$os.Write($bytes, 0, $bytes.Length)
$os.Close()
$zip.Dispose()

# 6) zipalign + sign + verify
Write-Host "[6/6] zipalign + sign"
RunExe "$bt\zipalign.exe" @('-f', '-p', '4', $staging, "$out\aligned.apk") 'zipalign' | Out-Null
RunExe "$bt\apksigner.bat" @(
    'sign', '--ks', $ks, '--ks-pass', 'env:QQWATCH_SIGN_PASSWORD', '--key-pass', 'env:QQWATCH_SIGN_PASSWORD',
    '--ks-key-alias', 'qqwatchdog', '--out', "$out\qqwatchdog.apk", "$out\aligned.apk"
) 'apksigner' | Out-Null
RunExe "$bt\apksigner.bat" @('verify', '--print-certs', "$out\qqwatchdog.apk") 'verify' | Out-Null

Write-Host "BUILD OK"
Get-Item "$out\qqwatchdog.apk" | Select-Object FullName, Length | Format-Table -AutoSize
