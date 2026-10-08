# Pure JVM regression tests. Keep this file ASCII for Windows PowerShell 5.1.
param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'build\monitor-tests'))
$ErrorActionPreference = 'Stop'
$javaBin = 'C:\Users\98380\AppData\Roaming\CherryStudio\Toolchain\mise\installs\java\21.0.2\bin'
$sourceDir = Join-Path $PSScriptRoot 'src\com\lqb9\qqwatchmod'
$sources = @('CpuLoadMonitor.java', 'QqCpuTracker.java', 'ThreadCpuTracker.java', 'CoreFrequency.java', 'LoadHistory.java', 'RollingLog.java','CoreSamplingDiagnostics.java','CoreTimeWindow.java','CoreSnapshot.java','CoreTracker.java','RuntimeLine.java','TaskDiagnostics.java','TaskExecutions.java','TaskRegistry.java','ProcessingHistory.java','GifTaskRule.java','ModuleInfo.java','SettingsCodec.java','LoadPolicy.java','ThreadLoadMonitor.java','DualLoadPolicy.java','ThreadTaskAttribution.java','GifHostAdapter.java','TaskHandlingRule.java','HandlingRequest.java','HandlingDecision.java','GifHandlingRule.java','FutureHandlingRule.java','TaskRuleRegistry.java') |
    ForEach-Object { Join-Path $sourceDir $_ }
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\CpuMonitoringTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\LogTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\CoreHandlingTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\TaskDiagnosticsTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\TaskExecutionsTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\CoreSamplingDiagnosticsTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\CoreTimeWindowTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\HandlerExecutionsTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\GifTaskRuleTest.java'
$sources += Join-Path $sourceDir 'ExecutorRegistry.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\ArchitectureTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\ExecutorRegistryTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\ThreadRuleTest.java'
$sources += Join-Path $sourceDir 'ThreadRuleDisplay.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\ThreadRuleDisplayTest.java'
$sources += Join-Path $sourceDir 'CollectorStatus.java'
$sources += Join-Path $sourceDir 'ThreadRuleReport.java'
$sources += Join-Path $sourceDir 'ThreadLoadHistory.java'
$sources += Join-Path $sourceDir 'QqTaskAdapter.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\QqTaskAdapterTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\TotalRuleTest.java'
$sources += Join-Path $sourceDir 'ProcStat.java'
$sources += Join-Path $sourceDir 'CollectorFileReader.java'
$sources += Join-Path $sourceDir 'RuntimeStream.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\CollectorPerformanceTest.java'
$sources += Join-Path $PSScriptRoot 'tests\com\lqb9\qqwatchmod\RuleMetadataTest.java'
$sources += @(Get-ChildItem (Join-Path $PSScriptRoot 'test-fixtures') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
& "$javaBin\javac.exe" --release 8 -Xlint:-options -encoding UTF-8 -d $OutputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.CpuMonitoringTest
if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.LogTest
if ($LASTEXITCODE -ne 0) { throw 'Persistent log tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.CoreHandlingTest
if ($LASTEXITCODE -ne 0) { throw 'Core handling tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.TaskDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Task diagnostics tests failed' }
& "$javaBin\java.exe" --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp $OutputDirectory com.lqb9.qqwatchmod.TaskExecutionsTest
if ($LASTEXITCODE -ne 0) { throw 'Task execution tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.CoreSamplingDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Core sampling diagnostics tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.CoreTimeWindowTest
if ($LASTEXITCODE -ne 0) { throw 'Aligned core sampling tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.HandlerExecutionsTest
if ($LASTEXITCODE -ne 0) { throw 'Handler execution tests failed' }
& "$javaBin\java.exe" --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp $OutputDirectory com.lqb9.qqwatchmod.GifTaskRuleTest
if ($LASTEXITCODE -ne 0) { throw 'GIF object rule tests failed' }
& "$javaBin\java.exe" --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp $OutputDirectory com.lqb9.qqwatchmod.ArchitectureTest
if ($LASTEXITCODE -ne 0) { throw 'Architecture invariant tests failed' }
& "$javaBin\java.exe" --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp $OutputDirectory com.lqb9.qqwatchmod.ExecutorRegistryTest
if ($LASTEXITCODE -ne 0) { throw 'Executor ownership tests failed' }

& "$javaBin\java.exe" --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp $OutputDirectory com.lqb9.qqwatchmod.ThreadRuleTest
if ($LASTEXITCODE -ne 0) { throw 'Independent thread rule tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.ThreadRuleDisplayTest
if ($LASTEXITCODE -ne 0) { throw 'Thread presentation-state tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.RuleMetadataTest
if ($LASTEXITCODE -ne 0) { throw 'Collector status and metadata tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.TotalRuleTest
if ($LASTEXITCODE -ne 0) { throw 'Independent total switch and thread history tests failed' }
& "$javaBin\java.exe" -cp $OutputDirectory com.lqb9.qqwatchmod.CollectorPerformanceTest
if ($LASTEXITCODE -ne 0) { throw 'Collector parser and reader tests failed' }

& "$javaBin\java.exe" --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp $OutputDirectory com.lqb9.qqwatchmod.QqTaskAdapterTest
if ($LASTEXITCODE -ne 0) { throw 'QQ adapter identity tests failed' }
