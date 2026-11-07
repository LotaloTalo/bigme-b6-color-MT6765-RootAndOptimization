param(
    [string]$AdbPath  = 'C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools\platform-tools\adb.exe',
    [string]$Serial   = 'B6CLR0B2GG5A006000422',
    [string]$Python   = 'C:\Users\Flakt\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe',
    [string]$ToolRoot = 'C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools',
    [string]$OutDir   = 'C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\backup',
    [switch]$Full
)

# 救砖备份：进入 EDL 后读取关键分区（boot / init_boot / vbmeta / dtbo，A+B 两槽）
# 需要 UsbDk 驱动（tools\UsbDk_1.0.22_x64.msi 已安装）。
# 失败恢复：设备在 EDL 黑屏时，长按电源 15 秒即可回到系统。

$ErrorActionPreference = 'Continue'
$parts = @('boot_a','boot_b','init_boot_a','init_boot_b','vbmeta_a','vbmeta_b','dtbo_a','dtbo_b')
$files = @{}
foreach ($p in $parts) { $files[$p] = "$p.img" }

if (-not (Test-Path $AdbPath)) { throw "adb 不存在: $AdbPath" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$dev = & $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $dev) { throw "没有已连接设备（需要先在系统里开 USB 调试）。" }

Write-Host "即将进入 EDL 并备份分区：$($parts -join ', ')" -ForegroundColor Yellow
Write-Host "如果 60 秒内 mtkclient 没有开始读分区，设备会停在 EDL（屏幕不亮）。" -ForegroundColor Yellow
Write-Host "恢复方法：长按电源键 15 秒。" -ForegroundColor Yellow
Write-Host ""
& $AdbPath -s $Serial reboot edl
Start-Sleep -Seconds 8

$env:PYTHONPATH = "$ToolRoot\mtkclient-main;$ToolRoot\_pylibs"
$env:PATH = "$ToolRoot\mtkclient-main\mtkclient\Windows;$env:PATH"
Set-Location "$ToolRoot\mtkclient-main"
Set-Location $OutDir

if ($Full) {
    Write-Host "执行: python mtk.py rl . --skip userdata（全分区备份，耗时较长）" -ForegroundColor Cyan
    & $Python "$ToolRoot\mtkclient-main\mtk.py" rl . --skip userdata 2>&1 | Select-Object -Last 30
} else {
    $partArg = ($parts -join ',')
    $fileArg = ($files.Values -join ',')
    Write-Host "执行: python mtk.py r $partArg $fileArg" -ForegroundColor Cyan
    & $Python "$ToolRoot\mtkclient-main\mtk.py" r $partArg $fileArg 2>&1 | Select-Object -Last 30
}

Write-Host ""
Write-Host "=== 备份结果 ===" -ForegroundColor Cyan
if ($Full) {
    Get-ChildItem $OutDir -File -ErrorAction SilentlyContinue | Sort-Object Name |
        Select-Object Name, @{n='MB';e={[math]::Round($_.Length/1MB,1)}} | Format-Table -AutoSize
} else {
    foreach ($p in $parts) {
        $f = Join-Path $OutDir $files[$p]
        if (Test-Path $f) {
            Write-Host ("  OK   {0,-14} {1,10} bytes" -f $files[$p], (Get-Item $f).Length)
        } else {
            Write-Host ("  MISS {0}" -f $files[$p]) -ForegroundColor Yellow
        }
    }
}
