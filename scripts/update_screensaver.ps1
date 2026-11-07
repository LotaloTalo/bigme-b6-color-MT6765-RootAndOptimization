# 一键更新 Bigme B6 屏保图（底图 + 日期 + 农历）
# 前提：设备已 adb 连接（USB 或无线调试）。
# 用法：右键运行，或 powershell -File update_screensaver.ps1

$py = 'C:\Users\储备粮六号\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$script = 'C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\scripts\update_screensaver.py'

if (-not (Test-Path $py)) { throw "Python 不存在: $py" }
& $py $script
