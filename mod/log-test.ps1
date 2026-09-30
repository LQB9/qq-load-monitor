# Background on-device check using the production logger/exporter. ASCII for PS 5.1.
param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$sdk = 'D:\deepseek\_work\android-sdk'
$bt = "$sdk\build-tools\37.0.0"
$aj = "$sdk\platforms\android-37.0\android.jar"
$javaBin = 'C:\Users\98380\AppData\Roaming\CherryStudio\Toolchain\mise\installs\java\21.0.2\bin'
$env:JAVA_HOME = Split-Path $javaBin
$sourceDir = Join-Path $PSScriptRoot 'src\com\lqb9\qqwatchmod'
New-Item -ItemType Directory -Force -Path "$OutputDirectory\classes","$OutputDirectory\dex" | Out-Null
function Checked($file, $arguments) {
    & $file @arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed: $file" }
}
$sources = @('RollingLog.java','DownloadExporter.java','WatchLog.java') | ForEach-Object { Join-Path $sourceDir $_ }
$sources += Join-Path $PSScriptRoot 'android-tests\com\lqb9\qqwatchmod\DownloadCheck.java'
Checked "$javaBin\javac.exe" (@('--release','8','-Xlint:-options','-encoding','UTF-8','-classpath',$aj,'-d',"$OutputDirectory\classes") + $sources)
Checked "$javaBin\jar.exe" @('cf',"$OutputDirectory\classes.jar",'-C',"$OutputDirectory\classes",'.')
Checked "$bt\d8.bat" @('--release','--min-api','26','--lib',$aj,'--output',"$OutputDirectory\dex","$OutputDirectory\classes.jar")
$manifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lqb9.qqwatchlog.check"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="31"/><application android:label="QQ Log Check"/><instrumentation android:name="com.lqb9.qqwatchmod.DownloadCheck" android:targetPackage="com.lqb9.qqwatchlog.check"/></manifest>'
[IO.File]::WriteAllText("$OutputDirectory\AndroidManifest.xml", $manifest, [Text.Encoding]::UTF8)
Checked "$bt\aapt2.exe" @('link','--manifest',"$OutputDirectory\AndroidManifest.xml",'-I',$aj,'-o',"$OutputDirectory\base.apk")
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$zip = [IO.Compression.ZipFile]::Open("$OutputDirectory\base.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$OutputDirectory\dex\classes.dex",'classes.dex') | Out-Null } finally { $zip.Dispose() }
Checked "$bt\zipalign.exe" @('-f','-p','4',"$OutputDirectory\base.apk","$OutputDirectory\aligned.apk")
Checked "$bt\apksigner.bat" @('sign','--ks',"$PSScriptRoot\watchmod-key.jks",'--ks-pass','pass:qqwatchmod123','--ks-key-alias','qqwatchmod','--out',"$OutputDirectory\log-download-check.apk","$OutputDirectory\aligned.apk")
Write-Host 'LOG TEST APK READY'
