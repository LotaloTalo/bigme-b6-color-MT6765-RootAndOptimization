param(
    [string]$AdbPath = 'C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools\platform-tools\adb.exe',
    [string]$Serial = 'B6CLR0B2GG5A006000422'
)

# 番茄 appops 级限制（不改任何系统包，可重复执行）
# 注意：Android 重启后这些 appops 会被系统重置，开机后重跑一次即可。

$ErrorActionPreference = 'Continue'
$cmds = @(
    'am set-standby-bucket com.dragon.read restricted',
    'cmd appops set com.dragon.read RUN_IN_BACKGROUND ignore',
    'cmd appops set com.dragon.read RUN_ANY_IN_BACKGROUND ignore',
    'cmd appops set com.dragon.read WAKE_LOCK ignore',
    'cmd appops set com.dragon.read VIBRATE ignore',
    'cmd appops set com.dragon.read POST_NOTIFICATION ignore',
    'cmd appops set com.dragon.read SYSTEM_ALERT_WINDOW deny',
    'dumpsys deviceidle whitelist -com.dragon.read'
)

if (-not (Test-Path $AdbPath)) { throw "adb 不存在: $AdbPath" }
$dev = & $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $dev) { throw "没有已连接设备。先插好 USB 并确认 adb devices 能看到。" }

foreach ($c in $cmds) {
    $out = (& $AdbPath -s $Serial shell $c 2>&1) -join ' '
    Write-Host ("{0,-62} {1}" -f $c, $out.Trim())
}

Write-Host ""
Write-Host "=== 验证 ===" -ForegroundColor Cyan
& $AdbPath -s $Serial shell "cmd appops get com.dragon.read RUN_IN_BACKGROUND; cmd appops get com.dragon.read WAKE_LOCK; am get-standby-bucket com.dragon.read"
Write-Host "完成。重启设备后需要再次运行本脚本。" -ForegroundColor Green
