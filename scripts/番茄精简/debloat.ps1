<#
.SYNOPSIS
  番茄小说（com.dragon.read）去臃肿脚本 —— 只看小说场景，非 root，纯 adb。

.DESCRIPTION
  用系统机制降低后台占用与耗电：
    Level 0  限制后台 bucket / appops（最安全，推荐必做）
    Level 1  禁用纯后台与纯商业组件（推送、小游戏、小程序、直播、广告、APM、保活、小组件）
    Level 2  禁用可选组件（Chromium 沙箱、下载器、定位、FM 音频、Tinker/Mira stub）—— 会牺牲对应功能

  所有操作都可用 -Rollback 恢复；不卸载、不清数据、不 root。

.EXAMPLE
  .\debloat.ps1 -DryRun -Level 2
  .\debloat.ps1 -Level 1
  .\debloat.ps1 -Rollback
#>
[CmdletBinding()]
param(
    [ValidateSet(0, 1, 2)][int]$Level = 1,
    [switch]$Rollback,
    [switch]$DryRun,
    [string]$Serial,
    [string]$AdbPath
)

$ErrorActionPreference = 'Continue'
$Pkg = 'com.dragon.read'
$script:ok = 0
$script:failed = @()

function Assert-Adb {
    if ($AdbPath) {
        if (-not (Test-Path $AdbPath)) { throw "指定的 adb 不存在: $AdbPath" }
        $script:Adb = $AdbPath
    } elseif (Get-Command adb -ErrorAction SilentlyContinue) {
        $script:Adb = 'adb'
    } else {
        throw "找不到 adb。请把 platform-tools 加入 PATH，或传 -AdbPath <adb.exe 完整路径>。"
    }
    $devices = & $script:Adb devices 2>&1 | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
    if (-not $devices) { throw "没有已连接的设备/模拟器，先执行 adb devices 确认。" }
    if (-not $Serial) {
        $script:Serial = ($devices[0] -split '\t')[0]
        Write-Host "使用设备: $script:Serial" -ForegroundColor Cyan
    }
}

function Invoke-Shell([string]$Command) {
    $adbArgs = @()
    if ($Serial) { $adbArgs += @('-s', $Serial) }
    $adbArgs += @('shell', $Command)
    if ($DryRun) {
        Write-Host ("[dry] adb " + ($adbArgs -join ' ')) -ForegroundColor DarkGray
        return $true
    }
    # adb 对"未知组件"会把异常写到 stderr；这里不能让它中断整个批次。
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = (& $script:Adb @adbArgs 2>&1 | Out-String)
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($LASTEXITCODE -eq 0) { $script:ok++; return $true }
    $script:failed += "$Command => $($out.Trim())"
    return $false
}

# ---------------------------------------------------------------- Level 0
# 都是"限制后台"的系统机制，不影响前台阅读。Android 14 上全部有效。
$Level0 = @(
    "am set-standby-bucket $Pkg restricted",
    "cmd appops set $Pkg RUN_IN_BACKGROUND ignore",
    "cmd appops set $Pkg RUN_ANY_IN_BACKGROUND ignore",
    # 注意：不要 deny START_FOREGROUND。前台服务是防被杀的机制，拒绝它反而让 App
    # 在需要保活时（听书/长任务）启动失败或直接掉前台优先级。保持默认 allow。
    "cmd appops set $Pkg WAKE_LOCK ignore",
    "cmd appops set $Pkg VIBRATE ignore",
    "cmd appops set $Pkg POST_NOTIFICATION ignore",
    "cmd appops set $Pkg SYSTEM_ALERT_WINDOW deny",
    "dumpsys deviceidle whitelist -$Pkg"
)

# ---------------------------------------------------------------- 组件表
# Note: 名字是 manifest 里的全限定类名；执行时拼成 <pkg>/<name>。
# 只列出"阅读链路用不到"的组件；账户/主进程/Room/WorkManager/Authenticator 一律不动。
$Components = @(
    # ---- 推送（字节自有 + 厂商通道）----
    @{ N = 'com.ss.android.message.NotifyService'; L = 1; T = '推送' }
    @{ N = 'com.ss.android.message.log.LogService'; L = 1; T = '推送' }
    @{ N = 'com.ss.android.newmedia.redbadge.RedBadgePushProcessService'; L = 1; T = '推送/角标' }
    @{ N = 'com.bytedance.push.alliance.DsProcessService'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.alliance.partner.Service1'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.alliance.partner.Service2'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.alliance.partner.Service3'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.alliance.partner.MrService'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.alliance.partner.MdService'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.alliance.partner.RemoteViewsService1'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.alliance.partner.MainRemoteViewsService1'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.push.HwSmartFenceService'; L = 1; T = '推送/围栏' }
    @{ N = 'com.bytedance.push.event.sync.SmpSignalReceiver'; L = 1; T = '推送' }
    @{ N = 'com.bytedance.common.wschannel.server.WsChannelService'; L = 1; T = '长连接' }
    @{ N = 'com.bytedance.common.wschannel.client.WsClientService'; L = 1; T = '长连接' }
    @{ N = 'com.bytedance.common.process.service.CrossProcessServiceForPush'; L = 1; T = '推送进程' }
    @{ N = 'com.bytedance.common.process.service.CrossProcessServiceForPushService'; L = 1; T = '推送进程' }
    @{ N = 'com.bytedance.common.process.service.CrossProcessServiceForSmp'; L = 1; T = '推送进程' }
    @{ N = 'com.xiaomi.push.service.XMPushService'; L = 1; T = '小米推送' }
    @{ N = 'com.xiaomi.push.service.XMJobService'; L = 1; T = '小米推送' }
    @{ N = 'com.xiaomi.push.service.UploadLogSDKService'; L = 2; T = '小米推送日志' }
    @{ N = 'com.xiaomi.push.service.receivers.PingReceiver'; L = 1; T = '小米推送' }
    @{ N = 'com.xiaomi.mipush.sdk.PushMessageHandler'; L = 1; T = '小米推送' }
    @{ N = 'com.xiaomi.mipush.sdk.MessageHandleService'; L = 1; T = '小米推送' }
    @{ N = 'com.xiaomi.mipush.sdk.SmpPushMessageHandler'; L = 1; T = '小米推送' }
    @{ N = 'com.xiaomi.mipush.sdk.SmpMessageHandleService'; L = 1; T = '小米推送' }
    @{ N = 'com.huawei.hms.support.api.push.service.HmsMsgService'; L = 1; T = '华为推送' }
    @{ N = 'com.huawei.hms.support.api.push.PushMsgReceiver'; L = 1; T = '华为推送' }
    @{ N = 'com.huawei.hms.support.api.push.PushReceiver'; L = 1; T = '华为推送' }
    @{ N = 'com.huawei.MessageService'; L = 1; T = '华为消息' }
    @{ N = 'com.honor.HonorMessageListenerService'; L = 1; T = '荣耀推送' }
    @{ N = 'com.heytap.msp.push.service.CallkitPushMessageService'; L = 1; T = 'OPPO推送' }
    @{ N = 'com.heytap.msp.push.service.DataMessageCallbackService'; L = 1; T = 'OPPO推送' }
    @{ N = 'com.heytap.msp.push.service.CompatibleDataMessageCallbackService'; L = 1; T = 'OPPO推送' }
    @{ N = 'com.heytap.msp.push.service.SmpDataMessageCallbackService'; L = 1; T = 'OPPO推送' }
    @{ N = 'com.heytap.msp.push.service.SmpCompatibleDataMessageCallbackService'; L = 1; T = 'OPPO推送' }
    @{ N = 'com.heytap.msp.push.service.DataMessageReceivingService'; L = 1; T = 'OPPO推送' }
    @{ N = 'com.vivo.push.sdk.service.CommandClientService'; L = 1; T = 'vivo推送' }
    @{ N = 'com.vivo.push.sdk.service.MainCommandClientService'; L = 1; T = 'vivo推送' }
    @{ N = 'com.vivo.VivoPushMessageReceiver'; L = 1; T = 'vivo推送' }
    @{ N = 'com.vivo.SmpVivoPushMessageReceiver'; L = 1; T = 'vivo推送' }

    # ---- 小游戏 / 小程序 ----
    @{ N = 'com.minigame.merge.miniapphost.placeholder.MiniappService0'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.merge.miniapphost.placeholder.MiniappService1'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.merge.miniapphost.placeholder.MiniappService2'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.merge.miniapphost.placeholder.MiniappService3'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.merge.miniapphost.placeholder.MiniappService4'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.merge.miniapphost.feedback.FeedbackRecordService'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.miniapphost.placeholder.MiniappService0'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.miniapphost.placeholder.MiniappService1'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.miniapphost.placeholder.MiniappService2'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.miniapphost.placeholder.MiniappService3'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.miniapphost.placeholder.MiniappService4'; L = 1; T = '小游戏' }
    @{ N = 'com.minigame.miniapphost.feedback.FeedbackRecordService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.minigame.nativegame.download.SCDownloadService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.minigame.nativegame.download.SCMainProcessService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.minigame.merge.appbase.ipc.MiniGameMainIpcService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.minigame.bdpbase.ipc.extention.MainDefaultIpcService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.unitycontainer.service.UcAppService200'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.unitycontainer.service.UcAppService201'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.unitycontainer.service.UcAppService202'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.unitycontainer.service.UcAppService203'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.unitycontainer.download.SCDownloadService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.unitycontainer.download.SCMainProcessService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.app.interactgame.placeholder.InteractProcessXService'; L = 1; T = '小游戏' }
    @{ N = 'com.bytedance.bdp.app.miniapp.se.feedback.FeedbackRecordService'; L = 1; T = '小程序' }
    @{ N = 'com.bytedance.bdp.bdpbase.ipc.extention.MainDefaultIpcService'; L = 1; T = '小程序' }
    @{ N = 'com.tt.miniapphost.placeholder.MiniAppChildProcessMultiInsService'; L = 1; T = '小程序' }
    @{ N = 'com.tt.miniapp.livecast.core.serivce.HostLiveCastForegroundService'; L = 1; T = '小程序直播' }
    @{ N = 'com.tt.miniapp.livecast.core.serivce.MiniAppLiveCastForegroundService'; L = 1; T = '小程序直播' }

    # ---- 直播 / 短剧 ----
    @{ N = 'com.bytedance.android.livesdk.notification.AudioLiveService'; L = 1; T = '直播' }
    @{ N = 'com.bytedance.android.livesdk.bgService.LiveMediaSessionService'; L = 1; T = '直播' }
    @{ N = 'com.bytedance.android.livesdk.floatview.VideoFloatWindowService'; L = 1; T = '直播' }
    @{ N = 'com.bytedance.android.livesdk.bytecast.WireCastService'; L = 1; T = '直播' }
    @{ N = 'com.bytedance.android.livesdk.notification.multilink.MultiLinkCaptureNotificationService'; L = 1; T = '直播' }
    @{ N = 'com.bytedance.android.openlive.record.DefaultRecordService'; L = 1; T = '直播录制' }
    @{ N = 'com.dragon.read.component.shortvideo.impl.listenmode.keepalive.SeriesListenModePlaybackService'; L = 1; T = '短剧听模式' }

    # ---- 广告 / 任务 / APM / 预暖 ----
    @{ N = 'com.bytedance.android.ad.sdk.impl.ipc.AdIpcMainService'; L = 1; T = '广告' }
    @{ N = 'com.bytedance.ug.sdk.luckydog.task.CrossoverService'; L = 1; T = '任务/金币' }
    @{ N = 'com.bytedance.memory.upload.MemoryUploadService'; L = 1; T = 'APM内存上传' }
    @{ N = 'com.phoenix.read.prewarm.PreWarmService'; L = 1; T = '预暖' }
    @{ N = 'com.bytedance.applog.collector.Collector'; L = 1; T = '埋点采集' }
    @{ N = 'com.bytedance.upc.common.network.UpcNetChangeManager'; L = 1; T = '网络埋点' }
    @{ N = 'com.bytedance.depths.component.AndroidRTimeReceiver'; L = 1; T = '上报' }
    @{ N = 'com.a.b.SmpReceiver1'; L = 1; T = '推送壳' }
    @{ N = 'com.a.b.SmpReceiver2'; L = 1; T = '推送壳' }
    @{ N = 'com.a.b.SmpReceiver3'; L = 1; T = '推送壳' }
    @{ N = 'com.a.d.DpSignalReceiver'; L = 1; T = '推送壳' }
    @{ N = 'com.a.d.DpReceiver'; L = 1; T = '推送壳' }
    @{ N = 'com.a.d.MdService'; L = 1; T = '推送壳' }
    @{ N = 'com.a.d.MrService'; L = 1; T = '推送壳' }
    @{ N = 'com.a.d.DpService'; L = 1; T = '推送壳' }
    @{ N = 'com.bytedance.notification.NotificationDeleteBroadcastReceiver'; L = 1; T = '推送通知' }
    @{ N = 'com.bytedance.notification.SmpNotificationDeleteBroadcastReceiver'; L = 1; T = '推送通知' }
    @{ N = 'com.bytedance.notification.NotificationButtonClickBroadcastReceiver'; L = 1; T = '推送通知' }

    # ---- 保活 ----
    @{ N = 'com.tencent.tinker.lib.am.KeepAlive'; L = 1; T = 'Tinker保活' }
    @{ N = 'com.bytedance.mira.am.KeepAlive'; L = 1; T = 'Mira保活' }

    # ---- 桌面小组件（全部）----
    @{ N = 'com.dragon.read.appwidget.WidgetPackageReplacedReceiver'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.welfaretask.WelfareTaskAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.redpacketpattern.RedPacketPatternIsolateAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.redpacketpattern.RedPacketPatternAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.godbook.GodBookAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.redpacket.RedPacketAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.multigenre.MultiGenreNovelAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.hotbook.HotBookAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.quote.QuoteAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.quote.QuotePublishAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.welfare.WelfareAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.bookshelf.BookshelfAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.bookentry.BookEntryAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.shortvideorecent.ShortVideoRecentAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.ecomorder.EcomOrderStatusAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.search.SearchAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.multigenre.MultiGenreRecentAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.multigenre.MultiGenreRecentIsolateAppWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.dragon.read.appwidget.novelrecommend.NovelRecommendationWidgetReceiver'; L = 1; T = '小组件' }
    @{ N = 'com.bytedance.shoppingIconwidget.ShoppingIconWidgetProvider'; L = 1; T = '小组件' }
    @{ N = 'com.bytedance.kmp.toufan.widget.base.action.WidgetCallbackReceiver'; L = 1; T = '小组件' }

    # ---- Level 2：可选（会牺牲功能 / 有启动风险）----
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService0'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService1'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService2'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService3'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService4'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService5'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService6'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService7'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService8'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.SandboxedProcessService9'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.bytedance.org.chromium.content.app.PrivilegedProcessService0'; L = 2; T = 'H5沙箱' }
    @{ N = 'com.ss.android.socialbase.downloader.downloader.DownloadService'; L = 2; T = '下载器' }
    @{ N = 'com.ss.android.socialbase.downloader.downloader.IndependentProcessDownloadService'; L = 2; T = '下载器' }
    @{ N = 'com.ss.android.socialbase.appdownloader.DownloadHandlerService'; L = 2; T = '下载器' }
    @{ N = 'com.amap.api.location.APSService'; L = 2; T = '定位' }
    @{ N = 'com.xs.fm.player.sdk.component.service.FMPlayService'; L = 2; T = 'FM音频' }
    @{ N = 'com.xs.fm.player.base.component.service.FMPlayService'; L = 2; T = 'FM音频' }
    @{ N = 'com.tencent.tinker.lib.MuteInstallService'; L = 2; T = 'Tinker补丁' }
    @{ N = 'com.tencent.tinker.lib.stub.RedirectService'; L = 2; T = 'Tinker' }
    @{ N = 'com.tencent.tinker.lib.stub.p0.StubService1'; L = 2; T = 'Tinker' }
    @{ N = 'com.bytedance.mira.stub.RedirectService'; L = 2; T = 'Mira插件' }
    @{ N = 'com.bytedance.mira.stub.p0.StubService1'; L = 2; T = 'Mira插件' }
    @{ N = 'com.dragon.read.proxy.MainProcessWsChannelService'; L = 2; T = '主进程长连接' }
)

# ---------------------------------------------------------------- 主流程
if (-not $DryRun) { Assert-Adb }

if ($Rollback) {
    Write-Host "回滚所有已禁用的组件..." -ForegroundColor Yellow
    foreach ($c in $Components) {
        Invoke-Shell "pm enable --user 0 $Pkg/$($c.N)" | Out-Null
    }
    Write-Host "完成。禁用 $($script:ok) 项，失败 $($script:failed.Count) 项。" -ForegroundColor Green
    return
}

Write-Host "== Level 0：限制后台 ==" -ForegroundColor Cyan
foreach ($op in $Level0) { Invoke-Shell $op | Out-Null }

$targets = $Components | Where-Object { $_.L -le $Level }
Write-Host "== Level 1~$Level：禁用 $($targets.Count) 个组件 ==" -ForegroundColor Cyan
foreach ($c in $targets) {
    if (Invoke-Shell "pm disable-user --user 0 $Pkg/$($c.N)") {
        Write-Host ("  [ok] {0,-10} {1}" -f $c.T, $c.N) -ForegroundColor DarkGreen
    } else {
        Write-Host ("  [--] {0,-10} {1}" -f $c.T, $c.N) -ForegroundColor DarkYellow
    }
}

Write-Host ""
Write-Host "完成：成功 $($script:ok) 项，失败 $($script:failed.Count) 项。" -ForegroundColor Green
if ($script:failed.Count) {
    Write-Host "失败通常表示该组件在当前版本不存在，可忽略；若影响功能用 -Rollback 恢复。" -ForegroundColor Yellow
    $script:failed | Select-Object -First 10 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
}
Write-Host "建议重启 App 后再验证：adb shell dumpsys activity processes | findstr /i dragon" -ForegroundColor Cyan
