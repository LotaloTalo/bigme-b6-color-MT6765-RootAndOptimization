# Bigme B6（HiBreak / Android 14 / MT6765）工作区

> **硬件**：联发科 MT6765（Helio P35/G35 系列，ARM Cortex-A53 4×2.2GHz + 4×1.8GHz，8 核，12nm，最高 2.2GHz），10.3 英寸墨水屏，Android 14，4GB RAM。

> 记录本设备的番茄精简、root 与屏保改造。所有设备相关文件都在本目录。

## 总览：已完成的优化（分类）

### 一、系统优化
| 项 | 内容 |
|---|---|
| Root | Magisk 28.1（EDL 刷入，Zygisk 启用） |
| Hook 框架 | LSPosed v1.9.2 |
| Magisk 模块 | `bgsaver`（提供自定义背景图 `/system/etc/ssbgx.jpg`） |
| 开机脚本 | `/data/adb/service.d/99_tomato_limits.sh`（开机自动重跑番茄 appops 限制） |

### 二、锁屏个性化（屏保）
| 项 | 内容 |
|---|---|
| 背景图 | 自定义底图 + 横竖屏按比例裁剪（CENTER_CROP，不拉伸） |
| 日号 | 1-31 黑-白-黑三层描边（3-6-6）+ 中心挖空透明，Arial Regular |
| 日期格式 | `2026年11月`（原 `2026 .11`） |
| 农历/节日 | 1.6 倍字号 + 加粗 + 软阴影（星期保持原样） |
| 白底 bug | 容器背景清理（show + updateLogo + onDraw 兜底） |
| 0 点自动换日 | 每分钟刷新 + 省电时段 0:02-23:58（刷新窗口 23:58-0:02） |
| 省电 alarm | 锁屏时只留 1 个 alarm（不再每分钟唤醒 CPU），实测跨 0 点日期正常更新 |

### 三、番茄优化
| 项 | 内容 |
|---|---|
| appops 限制 | 后台运行/唤醒锁/通知 ignore + standby-bucket restricted + 移出 deviceidle 白名单 |
| 推送组件禁用 | 10 个（小米/华为/vivo/字节推送接收器与服务） |
| 锁屏冻结 | cgroup v2：熄屏冻结、亮屏解冻、开机自启（锁屏后不杀进程，解锁续读） |
| 后台进程 | 只剩 1 个进程（约 810MB） |
| 阅读页电量 | 去掉电池图标，只显示百分比 |
| 阅读页文字 | 书名/页码/时间/段末评论气泡数字全部改黑字 |
| 缓存清理 | 34MB → 4KB |


---

## 设备信息

| 项 | 值 |
|---|---|
| 型号 | Bigme B6（系统上报 HiBreak） |
| serial | `B6CLR0B2*****60422` |
| 系统 | Android 14（SDK 34） |
| 芯片 | MediaTek MT6765（Helio P35），board `k65v1_64_bsp` |
| 存储 | 4GB RAM / 64GB eMMC，A/B 分区 + super |
| 输入法 | `com.xrz.input`（Gboard 不在默认路径） |

## 当前状态（）

- ✅ 系统正常：`boot_completed=1`，桌面/设置/番茄都能启动，无 FATAL
- ✅ 番茄 7.4.3.32 已安装、协议已同意、通知权限已拒绝
- ✅ 番茄 appops 限制已设置：`RUN_IN_BACKGROUND=ignore`、`WAKE_LOCK=ignore`、`standby-bucket=restricted`、移出 deviceidle 白名单
- ⚠️ **appops 重启后会被系统重置**（已实测），每次开机需重跑 `scripts\apply_limits.ps1`
- ❌ 组件级禁用（116 项）被 ROM 拒绝：`SecurityException: Shell cannot change component state`（Android 14 + user 版限制，只能 root 后做）

## 目录

```
BigmeB6\
├── README.md                 ← 本文件
├── tools\
│   ├── platform-tools\       adb / fastboot
│   ├── mtkclient-main\       MTK BROM 工具（含 Windows libusb dll）
│   ├── _pylibs\              mtkclient 的 Python 依赖（手动 wheel 安装）
│   ├── Magisk-v28.1.apk
│   └── UsbDk_1.0.22_x64.msi  （已安装，服务 Running）
├── scripts\
│   ├── apply_limits.ps1      ← 番茄 appops 限制（安全，每次开机跑）
│   └── backup_partitions.ps1 ← EDL 救砖备份（boot/init_boot/vbmeta/dtbo A+B）
└── backup\                   ← 备份输出（目前为空）
```

## 设备 build 与分区（ 实测）

| 项 | 值 |
|---|---|
| build | `Bigme_V1.020260803`（） |
| fingerprint | `Bigme/HiBreak/Smartphone:14/UP1A.231005.007/1739236620:user/dev-keys` |
| 安全补丁 |  |
| 签名密钥 | **dev-keys**（测试密钥，解锁 bootloader 相对有利） |
| 分区 | 58 个，A/B + super（`/dev/block/by-name` 清单见 `backup\partition_list.txt`） |

关键分区（备份重点）：
`boot_a/b`(p22)、`init_boot_a/b`(p24)、`vbmeta_a/b`(p27/p43)、`vbmeta_system_*`、`vbmeta_vendor_*`、`dtbo_a/b`、`vendor_boot_a/b`(p23)、`seccfg`(p11)、`preloader_a/b`、`lk_a/b`、`super`(p42)、`userdata`(p47)

## 救砖手段现状

| 手段 | 状态 |
|---|---|
| **分区备份** | ⏳ 待做（需要 EDL；这是最可靠的一手备份，必须最先完成） |
| 官方固件 | ❌ 公开渠道没找到与 `Bigme_V1.020260803` 匹配的 MT6765 包；GSMForum 有 B531 4.x / B651 1.9.6，版本体系不同且需付费；firmwaredrive 上 HiBreak 只有 LineageOS 18.1 |
| LineageOS 18.1 | 备选（会替换系统、丢墨水屏优化，最后手段） |
| SP Flash Tool | ⏳ 未下载（有固件才需要） |
| factory reset | ✅ 已验证可用（recovery 选项） |
| 长按电源退出 EDL | ✅ 已确认可用 |

> 结论：**root 前必须先解决 EDL 连接并完成分区备份**；官方固件不可得的条件下，分区备份是唯一可靠救砖手段。

## Root 计划（参考 daaaaan/bigme-b6-mt6765）

1. **备份分区**（`scripts\backup_partitions.ps1`）：boot_a/b、init_boot_a/b、vbmeta_a/b、dtbo_a/b
2. Magisk 修补 `boot_a.img`（设备上装 Magisk → Select and Patch a File）
3. `mtk.py e metadata,userdata,md_udc`（擦数据）
4. `mtk.py da seccfg unlock`（解锁 bootloader）
5. `mtk.py da vbmeta 3`（禁用校验）
6. `mtk.py w boot_a,boot_b <patched>`（两槽都刷）
7. `mtk.py reset` 重启，完成 Magisk 附加设置
8. **USB-C 修复**（必须）：`/data/adb/service.d/fix-usb-role.sh` 写 `echo sink > /sys/class/typec/port0/port_type`

## 恢复

- 系统起不来：recovery → `Factory data reset`
- 卡 EDL：长按电源 15 秒
- 想恢复 root 前状态：`mtk.py w boot_a,boot_b boot_a.img,boot_a.img` + `mtk.py da seccfg lock` + 回刷 vbmeta

## 脚本用法

```powershell
# 每次开机后重设番茄限制（安全，不改包）
.\scripts\apply_limits.ps1

# 备份分区（需要 UsbDk + 人在旁边）
.\scripts\backup_partitions.ps1
```

> PowerShell 5.1 环境：脚本已保存为 UTF-8 with BOM，直接运行即可。

## 屏保 hook 方案（LSPosed）

**采用 hook（不改 APK、不碰签名），模块工程在 `hooksaver\`（已并入 `screensaver\` 工程构建）。**

### 环境
- Magisk 28.1（Zygisk 启用）+ LSPosed v1.9.2（zygisk 版）
- 模块：`screensaver` 工程构建的 APK（`com.bigme.screensaver`），LSPosed 里启用、作用域 `com.xrz.standby`
- 背景图：Magisk 模块 `bgsaver` 放到 `/system/etc/ssbgx.jpg`（原图 585KB）

### 已实现（HookInit.java）
1. **背景图**：hook `Dialog.show()`，设内容根 View 背景 + 清子 View 背景
2. **中间日号**：`tv_day`(0x7f090265) 替换为 `StyledTextView` 自定义绘制（透明填充 + 白描边 + 黑外圈）
3. **年月格式**：`yearTv`(0x7f09028d) `2026 .11` → `2026年11月`（hook `updateTime` 防刷新覆盖）
4. **每天0点选项**：hook `TimeFrequencyOptionAdapter` 构造追加「每天0点」(86400)，`getClockFrequenceStr` 显示
5. **0点调度**：hook `setAlarm` 把 86400 换成「到下一个 0 点的秒数」

### 遗留问题（明天处理）
1. **白底 bug**：通知弹出/刷新后背景丢失（重绘时被主题覆盖），需 hook 更底层（View.onDraw / Window.Callback）
2. **0点刷新未验证生效**：setAlarm 已排到 0 点（日志 `next midnight in 85512s`），但跨点后日期没变——查 AlarmReceiver 是否触发、Dialog 是否刷新
3. **日号描边**：字体轮廓是连续闭合的（8 腰部会连成"桥"），要做"上下两段弧"需自己画数字字形（1~31）

### 恢复
- 模块全禁：`/data/adb/modules/{bgsaver,zygisk_lsposed}/disable` 删除后重启

## 屏保细节打磨（hook 最终版）

### 日号（1-31）三层描边
  - 黑-白-黑三层实色描边（内黑 3 / 白 6 / 外黑 6 dp）
  - 中心用 `saveLayer` + `PorterDuff.CLEAR` 挖空（透明）
  - 字体 Arial Regular（`assets/arial.ttf`），字号 ×0.88 防左右截断
- 日期切换、跨天刷新均正常

### 农历 / 节日 / 星期
- 农历（0x7f090271）、节日（0x7f09026e）：**1.6 倍字号 + 加粗 + 软阴影**（shadowLayer 6dp）
- 星期（0x7f09028c）：保持原样
- 实现：`styleOthers()` 遍历所有 TextView，按 id 区分

### 白底 bug（修复）
- 现象：通知/刷新后文字容器变白底
- 修复：`clearBackgrounds()` 在 show + **updateLogo hook** 里都执行；`clearChildBgs()` 在 onDraw 监测里兜底

### 刷新后格式/颜色被还原（修复）
- 原版 `updateLogo`（在 updateTime 之后）会重设文字
- 修复：**hook `updateLogo`（after）** 重新 format yearTv + styleOthers + clearBackgrounds

### 0 点刷新（e-ink 硬限制）
- `setAlarm`/`scheduleMidnight` 都能正确排到 0 点（日志验证）
- AlarmReceiver 也触发了（`WAKE_REASON_APPLICATION, details=com.xrz.screensaver`）
- **但 e-ink 熄屏时不重画面板**：内容更新了，屏幕仍显示旧日期；解锁/锁屏才刷新
- **最终方案**：用「每分钟刷新 + 省电时段 0:02-23:58」，刷新窗口 23:58~0:02（含 0 点），0 点附近连续刷新

### 已知限制
- e-ink 熄屏不刷屏（0 点日期需解锁/锁屏后才显示）
- 通知后白底需重新锁屏恢复（已在 hook 里兜底）

### 恢复步骤
- root 丢失：EDL 重刷 `backup\edl\magisk_patched.img` + 重开 Zygisk
- LSPosed 失效：重装 `LSPosed.zip` + Magisk 里确认 Zygisk 开启
- 模块禁用：删 `/data/adb/modules/{bgsaver,zygisk_lsposed}/disable` 重启

## 0 点刷新的最终方案（验证成功）

**配置**（屏保设置 → 时钟）：
- 时钟刷新间隔 = **每分钟**
- 省电设置 = **开启，时段 0:02 - 23:58**（不刷新时钟）

**效果**：刷新窗口 = 23:58~0:02（含 0 点），窗口内每分钟触发一次刷新；跨 0 点后约 1 秒内屏幕自动刷成新日期。
**实测**： 23:50 改时间 → 次日 00:00 跨点后屏幕上显示 12 号 ✓

**原理**：每次 AlarmReceiver 触发 → `activateWakeLock2(3)` + `updateLogo()` → 屏幕重画 → e-ink 面板刷新。

**结论**：e-ink 屏保的"自动换日"不需要改代码，靠这个配置组合即可。

## 横屏背景（已解决）

- 屏保对话框内加了一个 `ImageView(CENTER_CROP)` 背景层，横竖屏都按比例裁剪，不再拉伸
- 只有一张背景图（`/system/etc/ssbgx.jpg`），横竖屏共用，动态裁剪
- 存档点：`backup\snapshot-20261107-1920`（含当时 APK 与源码）

## 番茄调整


### 冻结方案（成功）
- 目标：锁屏时**不杀**番茄，而是**冻结**；亮屏解冻，解锁后接着看
- 实现在 `ScreenFreezeService.java`：
  - 监听 `SCREEN_OFF` / `SCREEN_ON`
  - 通过 su 写 `/sys/fs/cgroup/uid_<uid>/pid_<pid>/cgroup.freeze`（cgroup v2）
  - 熄屏写 1（冻结），亮屏写 0（解冻）
- `BootReceiver`：开机自动启动冻结服务
- 实测：锁屏几分钟后解锁，番茄仍在原页面 ✓

### 部署技巧（不用重启设备）
- LSPosed 模块更新后，只需 `am force-stop com.dragon.read` + 重新打开（目标进程重启即加载新 hook）
- App 服务更新：`adb install -r` + 重新 `am start-foreground-service`

### 番茄现状
- 1 个进程（appops 限制生效，后台 :push 等未启动）
- appops 限制：RUN_IN_BACKGROUND/WakeLock ignore、standby-bucket restricted、移出 deviceidle 白名单（重启后需重跑 `apply_limits.ps1`）

## 番茄阅读页电量（，已完成）

- 目标：阅读页底部右侧「时间 + 电池图标」→ 只显示百分比
- 定位：`LinearLayout h6o`（[827,1391][974,1430]）内含 时间 TextView + 电池图标 View（自定义绘制）
- 实现（`HookInit.hookTomatoBattery`，View.onAttachedToWindow）：
  - 遍历 `h6o` 子 View，非 TextView 的（电池图标）设为 `GONE`（并 300ms 后再清理一次，防时序）
  - 新增一个 TextView 显示 `BatteryManager` 的 `BATTERY_PROPERTY_CAPACITY + "%"`
- 效果：阅读页底部只剩「时间 + 百分比」，电池图标消失 ✓

## 番茄阅读页文字转黑（）

- 顶部书名 `jer`（"第103章 【随机传送】…"）：文字里带**灰色 ForegroundColorSpan**，`setTextColor` 无效，必须先把 Span 去掉
  → hook 里对 `startsWith("第") && contains("章")` 的 TextView 做 `SpannableString` + `removeSpan(ForegroundColorSpan)` + `setTextColor(BLACK)`
- 底部页码 `jwc`、时间（h6o 里的 TextView）：`setTextColor(BLACK)`
- 段末评论气泡数字 `h2`（父容器含 ImageView 背景）：`setTextColor(BLACK)`
- 电池图标 View 的隐藏有时序问题（偶发回来），用户表示不用管

### 书名 Span 问题（已解决）

- 现象：`第103章` 能设黑，但后面的`【随机传送】深度开发【空间放逐】`一直是灰的（采样 #929292）
- 原因：番茄给书名文本加了**颜色 Span**，且是在我们 attach 之后才加的；`setTextColor` 盖不住 Span
- 解决：hook `TextView.setText(CharSequence)`，对 `startsWith("第") && contains("章")` 的 TextView：
  - 用 tag 防递归
  - `SpannableString` 移除 `ForegroundColorSpan` **和** `TextAppearanceSpan`，再 `setTextColor(BLACK)`
- 效果：书名整段全黑 ✓

## 屏保 alarm 省电（验证成功）

**问题**：屏保在省电时段（0:02-23:58）内虽然不刷新内容，但 `setAlarm` 仍每分钟排一次 alarm → 锁屏时每分钟唤醒 CPU，是待机耗电主因。

**方案**：hook `ScreenSaverClockDialog.setAlarm`，对 `freq == 60`（每分钟）：
- 读 `xrz_settings` 的 `screensaver_config_2`（JSON：powerSavingMode/Start/End）
- 当前时间在省电时段内 → 把 alarm 秒数改成「到省电结束（23:58）」的秒数
- 省电时段外（23:58-0:02 刷新窗口）→ 保持每分钟

**效果**：
- 锁屏时只剩 1 个 alarm（23:58/23:59）→ 不再每分钟唤醒
- 23:58 进入刷新窗口 → 每分钟刷新 → 跨 0 点日期更新 → 0:02 后排到次日 23:58，循环
- **实测**：设备 23:55 → 0:00 跨点，屏幕日号从 7 变成 8 ✓

**日志标记**：`setAlarm(mins) -> powerSaving end in Ns`

## 停用的 B6 内置应用（）

| 应用 | 包名 | 说明 |
|---|---|---|
| 大我语记 | `com.xrz.voice.text` | 语音转文字笔记 |
| bigme cloud | `com.xrz.bigmecloud` | 云同步服务（不用云备份） |
| MTK 语音命令 | `com.mediatek.voicecommand` | 联发科语音控制 |
| MTK 语音解锁 | `com.mediatek.voiceunlock` | 联发科语音解锁 |

恢复：`su -c 'pm enable <包名>'`
