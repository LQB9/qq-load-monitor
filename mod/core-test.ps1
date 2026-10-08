# Controlled background Android sampling. ASCII for PowerShell 5.1.
param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
if (-not $env:QQWATCH_SIGN_PASSWORD) { throw 'Set QQWATCH_SIGN_PASSWORD locally before signing' }
$sdk = 'D:\deepseek\_work\android-sdk'
$bt = "$sdk\build-tools\37.0.0"
$aj = "$sdk\platforms\android-37.0\android.jar"
$javaBin = 'C:\Users\98380\AppData\Roaming\CherryStudio\Toolchain\mise\installs\java\21.0.2\bin'
$env:JAVA_HOME = Split-Path $javaBin
$sourceDir = Join-Path $PSScriptRoot 'src\com\lqb9\qqwatchmod'
New-Item -ItemType Directory -Force -Path "$OutputDirectory\classes","$OutputDirectory\dex" | Out-Null
function Checked($file, $arguments) { & $file @arguments; if ($LASTEXITCODE -ne 0) { throw "Failed: $file" } }
$sources = @('CoreSamplingDiagnostics.java','CoreTimeWindow.java','CoreSnapshot.java','CoreTracker.java','RuntimeLine.java','CoreCollector.java','QqCpuTracker.java','ThreadCpuTracker.java') | ForEach-Object { Join-Path $sourceDir $_ }
$sources += Join-Path $PSScriptRoot 'android-tests\com\lqb9\qqwatchmod\CoreCheck.java'
$sources += @('ProcStat.java','CollectorFileReader.java','ProcStatReader.java','RuntimeStream.java') | ForEach-Object { Join-Path $sourceDir $_ }
$sources += @('StatReaderCheck.java','CollectorBench.java') | ForEach-Object { Join-Path $PSScriptRoot ('android-tests\com\lqb9\qqwatchmod\' + $_) }
Checked "$javaBin\javac.exe" (@('--release','8','-Xlint:-options','-encoding','UTF-8','-classpath',$aj,'-d',"$OutputDirectory\classes") + $sources)
Checked "$javaBin\jar.exe" @('cf',"$OutputDirectory\classes.jar",'-C',"$OutputDirectory\classes",'.')
Checked "$bt\d8.bat" @('--release','--min-api','26','--lib',$aj,'--output',"$OutputDirectory\dex","$OutputDirectory\classes.jar")
$manifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lqb9.qqwatchcore.check"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="32"/><application android:label="QQ Core Check"/><instrumentation android:name="com.lqb9.qqwatchmod.CoreCheck" android:targetPackage="com.lqb9.qqwatchcore.check"/></manifest>'
[IO.File]::WriteAllText("$OutputDirectory\AndroidManifest.xml", $manifest, [Text.Encoding]::UTF8)
Checked "$bt\aapt2.exe" @('link','--manifest',"$OutputDirectory\AndroidManifest.xml",'-I',$aj,'-o',"$OutputDirectory\base.apk")
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$zip = [IO.Compression.ZipFile]::Open("$OutputDirectory\base.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$OutputDirectory\dex\classes.dex",'classes.dex') | Out-Null } finally { $zip.Dispose() }
Checked "$bt\zipalign.exe" @('-f','-p','4',"$OutputDirectory\base.apk","$OutputDirectory\aligned.apk")
Checked "$bt\apksigner.bat" @('sign','--ks',"$PSScriptRoot\watchmod-key.jks",'--ks-pass','env:QQWATCH_SIGN_PASSWORD','--ks-key-alias','qqwatchmod','--out',"$OutputDirectory\core-check.apk","$OutputDirectory\aligned.apk")
Write-Host 'CORE TEST APK READY'
