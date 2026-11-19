param(
    [switch]$IncludeConnectedTests
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
$serial = $null
$backupFile = $null
try {
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    }
    if (-not $env:ANDROID_HOME) {
        $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
    $env:GRADLE_USER_HOME = Join-Path $root '.gradle-user-home'
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

    & .\gradlew.bat :android:app:assembleDebug :android:app:testDebugUnitTest :android:app:lintDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Local build verification failed with exit code $LASTEXITCODE" }

    if ($IncludeConnectedTests) {
        $emulatorLine = & $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' } | Select-Object -First 1
        if (-not $emulatorLine) {
            throw 'No connected Android emulator was found. Physical devices are not supported.'
        }
        $serial = ($emulatorLine -split '\s+')[0]
        $env:ANDROID_SERIAL = $serial
        $installed = & $adb -s $serial shell pm path com.realme 2>$null | Out-String
        if ($LASTEXITCODE -eq 0 -and $installed -match 'package:') {
            & $adb -s $serial shell am force-stop com.realme | Out-Null
            $backupDir = Join-Path $root 'verification'
            New-Item -ItemType Directory -Force $backupDir | Out-Null
            $backupFile = Join-Path $backupDir ("pre-connected-{0}.tar" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
            $startInfo = New-Object System.Diagnostics.ProcessStartInfo
            $startInfo.FileName = $adb
            $startInfo.Arguments = "-s $serial exec-out run-as com.realme tar -cf - files"
            $startInfo.UseShellExecute = $false
            $startInfo.RedirectStandardOutput = $true
            $process = [System.Diagnostics.Process]::Start($startInfo)
            $stream = [IO.File]::Create($backupFile)
            try {
                $process.StandardOutput.BaseStream.CopyTo($stream)
            } finally {
                $stream.Dispose()
            }
            $process.WaitForExit()
            if ($process.ExitCode -ne 0 -or (Get-Item $backupFile).Length -eq 0) {
                Remove-Item $backupFile -Force -ErrorAction SilentlyContinue
                $backupFile = $null
                throw 'App data backup failed; connected tests were not started.'
            }
            Write-Output "APP_DATA_BACKUP=$backupFile"
        }

        try {
            & .\gradlew.bat :android:app:connectedDebugAndroidTest --no-daemon
            if ($LASTEXITCODE -ne 0) { throw "Connected test failed with exit code $LASTEXITCODE" }
        } finally {
            if ($backupFile -and (Test-Path $backupFile)) {
                $installed = & $adb -s $serial shell pm path com.realme 2>$null | Out-String
                if ($LASTEXITCODE -ne 0 -or $installed -notmatch 'package:') {
                    & $adb -s $serial install -r (Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk')
                    if ($LASTEXITCODE -ne 0) { throw 'App reinstall after tests failed.' }
                }
                & $adb -s $serial shell am force-stop com.realme | Out-Null
                $remoteBackup = '/data/local/tmp/realme-pre-connected.tar'
                & $adb -s $serial push $backupFile $remoteBackup | Out-Null
                & $adb -s $serial shell "run-as com.realme tar -xf $remoteBackup -C ."
                if ($LASTEXITCODE -ne 0) { throw 'App data restore after tests failed.' }
                & $adb -s $serial shell rm -f $remoteBackup | Out-Null
                Write-Output "APP_DATA_RESTORED=$backupFile"
            }
        }
    }
} finally {
    Pop-Location
}
