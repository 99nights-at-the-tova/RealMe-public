param(
    [string]$Serial
)

# RealMe: Android端末側のスクリーンショットを撮るスクリプト。
# ツールバーのカメラ（PC保存）ではなく、Android 標準のスクリーンショットを撮るため、
# アプリのスクリーンショット検知（MediaStore）が反応します。
#
# 使い方:
#   .\scripts\take-screenshot.ps1
#   .\scripts\take-screenshot.ps1 -Serial emulator-5554
#   エクスプローラーからは take-screenshot.bat をダブルクリック

$ErrorActionPreference = 'Stop'

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    throw "adb が見つかりません: $adb（ANDROID_HOME を確認してください）"
}

if (-not $Serial) {
    $devices = & $adb devices | Where-Object { $_ -match '^\S+\s+device$' }
    $emulator = $devices | Where-Object { $_ -match '^emulator-' } | Select-Object -First 1
    $target = if ($emulator) { $emulator } else { $devices | Select-Object -First 1 }
    if (-not $target) {
        throw '接続中のエミュレーター/端末がありません。エミュレーターを起動してください。'
    }
    $Serial = ($target -split '\s+')[0]
}

# KEYCODE_SYSRQ(120) は「電源＋音量下」と同じ Android 標準のスクリーンショット。
& $adb -s $Serial shell input keyevent 120
if ($LASTEXITCODE -ne 0) {
    throw "スクリーンショットに失敗しました (exit $LASTEXITCODE)"
}

Start-Sleep -Milliseconds 2500
$latest = & $adb -s $Serial shell "ls -t /sdcard/Pictures/Screenshots 2>/dev/null | head -n 1" | Out-String
$latest = $latest.Trim()
if ($latest) {
    Write-Host "スクリーンショットを撮影しました: $Serial"
    Write-Host "  /sdcard/Pictures/Screenshots/$latest"
} else {
    Write-Host "スクリーンショットを撮影しました: $Serial"
}
Write-Host "ゲームなど別のアプリを前面にした状態で実行すると、RealMe が検知します。"
