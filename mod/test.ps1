# Pure JVM regression tests. Keep this file ASCII for Windows PowerShell 5.1.
param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'build\monitor-tests'))
$ErrorActionPreference = 'Stop'
$javaBin = 'C:\Users\98380\AppData\Roaming\CherryStudio\Toolchain\mise\installs\java\21.0.2\bin'
$sourceDir = Join-Path $PSScriptRoot 'src\com\lqb9\qqwatchmod'
$sources = @('CpuLoadMonitor.java', 'QqCpuTracker.java', 'ThreadCpuTracker.java', 'CoreFrequency.java', 'LoadHistory.java', 'RollingLog.java') |
    ForEach-Object { Join-Path $sourceDir $_ }
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\CpuMonitoringTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\LogTest.java'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
& "$javaBin\javac.exe" --release 8 -Xlint:-options -encoding UTF-8 -d $OutputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.CpuMonitoringTest
if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.LogTest
if ($LASTEXITCODE -ne 0) { throw 'Persistent log tests failed' }
