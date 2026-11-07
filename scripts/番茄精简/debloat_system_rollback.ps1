param(
    [string]$AdbPath = 'C:\Users\Public\DeepSeekHarnessWorkspace\fqapp\_tools\platform-tools\adb.exe',
    [string]$Serial = 'B6CLR0B2GG5A006000422'
)

# 恢复 2026-11-06 停用的系统包（只恢复本次停用的，不动系统原本就禁用的）
$pkgs = @(
    # 第一批：测试/日志/壁纸/打印/音效/厂商娱乐
    'com.test.logcollect','com.example.test','com.mediatek.aovtestapp','com.mediatek.factorymode',
    'com.mediatek.callrecorder','com.mediatek.voicecommand','com.mediatek.voiceunlock',
    'com.android.egg','com.android.dreams.basic',
    'com.android.wallpaper.livepicker','com.android.wallpaperbackup','com.android.wallpapercropper','com.android.wallpaperpicker',
    'com.android.printspooler','com.android.bips','com.android.musicfx',
    'com.socialnmobile.colordict','com.xrz.video','com.xrz.music','com.xrz.hoverballdemo','com.xrz.soundrecord',
    # 第二批：Google 冗余套件
    'com.android.vending','com.google.android.adservices.api','com.google.mainline.adservices','com.google.mainline.telemetry',
    'com.google.android.apps.photos','com.google.android.apps.restore','com.google.android.apps.safetyhub','com.google.android.apps.turbo',
    'com.google.android.as','com.google.android.as.oss','com.google.android.calculator','com.google.android.federatedcompute',
    'com.google.android.feedback','com.google.android.gms.location.history',
    'com.google.android.health.connect.backuprestore','com.google.android.healthconnect.controller','com.google.android.nearby.halfsheet',
    'com.google.android.ondevicepersonalization.services','com.google.android.projection.gearhead','com.google.android.sdksandbox',
    'com.google.android.syncadapters.calendar','com.google.android.tts','com.google.android.partnersetup',
    'com.google.android.printservice.recommendation','com.google.android.hotspot2.osulogin','com.google.android.inputmethod.latin',
    'com.google.android.cellbroadcastreceiver','com.google.android.cellbroadcastservice'
)

$ok = 0; $fail = @()
foreach ($p in $pkgs) {
    $r = & $AdbPath -s $Serial shell "pm enable $p" 2>&1
    if ($LASTEXITCODE -eq 0 -and "$r" -match 'new state: enabled') { $ok++ } else { $fail += "$p => $r" }
}
Write-Host "已恢复 $ok / $($pkgs.Count) 个包。"
if ($fail.Count) { $fail | ForEach-Object { Write-Host "  $_" } }
Write-Host "建议重启设备让系统重新注册这些组件。"
