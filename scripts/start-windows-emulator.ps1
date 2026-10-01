# Added for Resume Terminal, 2026-09-30 to 2026-10-01.
# SPDX-License-Identifier: GPL-3.0-or-later
param([switch]$Foreground)
$ErrorActionPreference = 'Stop'
$taskRoot = Join-Path $env:LOCALAPPDATA 'ResumeTerminal'
if (-not $Foreground) {
    $detachedScript = Join-Path $taskRoot 'start-emulator.ps1'
    if ($PSCommandPath -ne $detachedScript) { Copy-Item -LiteralPath $PSCommandPath -Destination $detachedScript -Force }
    $command = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $detachedScript + '" -Foreground'
    $result = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine = $command
        CurrentDirectory = $taskRoot
    }
    if ($result.ReturnValue -ne 0) { throw "Windows process creation failed: $($result.ReturnValue)" }
    Write-Output "Detached Windows launcher PID: $($result.ProcessId)"
    return
}
$taskSdk = Join-Path $taskRoot 'AndroidSdk'
$env:ANDROID_SDK_ROOT = $taskSdk
$env:ANDROID_AVD_HOME = Join-Path $taskRoot 'avd'
$env:ANDROID_ADB_SERVER_PORT = '5038'
$emulatorExe = Join-Path $taskSdk 'emulator\emulator.exe'
$adbExe = Join-Path $taskSdk 'platform-tools\adb.exe'
& $adbExe -P 5038 start-server
$launchOptions = @{
    FilePath = $emulatorExe
    WorkingDirectory = $taskRoot
    ArgumentList = @('-avd', 'Resume_API35', '-gpu', 'software',
        '-no-window', '-no-audio', '-no-snapshot', '-no-metrics', '-port', '5554')
    RedirectStandardOutput = Join-Path $taskRoot 'emulator.stdout.log'
    RedirectStandardError = Join-Path $taskRoot 'emulator.stderr.log'
    PassThru = $true
}
$process = Start-Process @launchOptions
$process.Id | Set-Content (Join-Path $taskRoot 'emulator.pid')
Write-Output "Windows emulator PID: $($process.Id); dedicated ADB port: 5038"
$process.WaitForExit()
