param(
    [string]$AdbPath  = 'C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools\platform-tools\adb.exe',
    [string]$Python   = 'C:\Users\Flakt\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe',
    [string]$ToolRoot = 'C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools',
    [string]$Serial   = 'B6CLR0B2GG5A006000422'
)

# EDL 连接诊断：让设备进 EDL，观察 USB 枚举，再尝试 mtk.py printgpt
# 运行前必须确认：人在设备旁边（失败时长按电源 15 秒退出 EDL）。

$ErrorActionPreference = 'Continue'

function Test-MediaTekDevice {
    Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue |
        Where-Object { $_.FriendlyName -match 'MediaTek|MTK|Preloader|Bootloader|USB Serial' -or $_.InstanceId -match 'VID_0E8D' } |
        Select-Object Status, FriendlyName, InstanceId
}

Write-Host "=== 1. 检查 UsbDk ===" -ForegroundColor Cyan
$svc = Get-Service UsbDk -ErrorAction SilentlyContinue
if ($svc) { "UsbDk: $($svc.Status)" } else { "UsbDk 未安装（需要先装 tools\UsbDk_1.0.22_x64.msi）" }

Write-Host "=== 2. 检查 adb 设备 ===" -ForegroundColor Cyan
& $AdbPath devices -l

$dev = & $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $dev) { throw "设备不在系统里。先让设备正常开机并开 USB 调试。" }

Write-Host ""
Write-Host "=== 3. 即将进入 EDL（黑屏属正常）===" -ForegroundColor Yellow
Write-Host "   失败恢复：长按电源键 15 秒" -ForegroundColor Yellow
Write-Host "   按 Enter 继续，或 Ctrl+C 取消 ..." -ForegroundColor Yellow
Read-Host

& $AdbPath -s $Serial reboot edl
Start-Sleep -Seconds 5

Write-Host "=== 4. 观察 USB 枚举（30 秒）===" -ForegroundColor Cyan
for ($i = 0; $i -lt 10; $i++) {
    $devs = Test-MediaTekDevice
    if ($devs) {
        "  [$i] 发现:"; $devs | Format-Table -AutoSize
    } else {
        "  [$i] 未发现 MediaTek 设备"
    }
    Start-Sleep -Seconds 3
}

Write-Host "=== 5. 尝试 mtk.py printgpt（60 秒超时）===" -ForegroundColor Cyan
$env:PYTHONPATH = "$ToolRoot\mtkclient-main;$ToolRoot\_pylibs"
$env:PATH = "$ToolRoot\mtkclient-main\mtkclient\Windows;$env:PATH"
$tmpOut = Join-Path $env:TEMP 'mtk_printgpt.txt'
$p = Start-Process -FilePath $Python -ArgumentList "$ToolRoot\mtkclient-main\mtk.py","printgpt" -RedirectStandardOutput $tmpOut -RedirectStandardError "$tmpOut.err" -PassThru -NoNewWindow
if (-not $p.WaitForExit(60000)) {
    "60 秒未完成，结束进程。"
    try { $p.Kill() } catch {}
}
"--- stdout ---"
Get-Content $tmpOut -ErrorAction SilentlyContinue | Select-Object -Last 25
"--- stderr ---"
Get-Content "$tmpOut.err" -ErrorAction SilentlyContinue | Select-Object -Last 15

Write-Host ""
Write-Host "=== 结果判断 ===" -ForegroundColor Cyan
$out = (Get-Content $tmpOut -ErrorAction SilentlyContinue) -join "`n"
if ($out -match 'GPT|partition|boot_a') {
    Write-Host "EDL 连接成功，可以执行 backup_partitions.ps1" -ForegroundColor Green
} else {
    Write-Host "EDL 连接失败。设备若黑屏：长按电源 15 秒。" -ForegroundColor Yellow
    Write-Host "下一步排查：重启电脑让 UsbDk 生效 / 用 Zadig 给 MediaTek 设备装 WinUSB / 检查 VID_0E8D 枚举" -ForegroundColor Yellow
}
