# Bigme B6 屏保（最终方案：原生屏保 + 每天生成图片）

> 2026-11-07 定稿。替换大我原屏保的显示内容为「自选底图 + 日期 + 农历」，无时钟。

## 为什么不用自写 App

实测这个 ROM（Android 14 / MT6765）：

- 标准 Dream 屏保**不触发**（DreamManager 只在停靠时启动，`mWhenToDream=3`，充电触发被厂商关闭）；
- 大我屏保 `com.xrz.standby` 由厂商服务在熄屏时显式启动 `ScreenSaveActivity`；
- 自写 App 监听 `SCREEN_OFF` 拉起 Activity 虽然能显示，但**熄屏后墨水屏不刷新**（唤醒时才刷），体验错误。

最终采用**大我原生屏保 + 覆盖它的图片文件**：熄屏时屏保直接读取
`/sdcard/Android/data/com.xrz.standby/files/Sandbox/CROP_*.jpg`，覆盖即可改画面（无需 root、无需改 APK）。

## 当前布局（用户定稿）

- 屏幕正中：`07`（大号粗体，横纵居中）
- 上方：`2026年 11月`
- 下方：`丙午年九月廿九`
- 白字 + 等宽黑描边（3~5px），整体缩放系数 `SCALE = 1.6`
- 底图：用户提供的《日常》插画（`backup/default_bg.jpg`），按 824×1113 居中裁剪

## 每天更新

```powershell
# 设备连电脑（USB/无线 adb）后运行：
C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\scripts\update_screensaver.ps1
```

脚本会：
1. 取当天日期 + 农历（内置 1900–2100 数据表）；
2. 按三个屏保副本尺寸生成图片；
3. `adb push` 覆盖 `Sandbox/CROP_*.jpg`。

熄屏一次即生效。

## 关键文件

- `scripts/update_screensaver.py`：生成 + 推送（核心）
- `scripts/update_screensaver.ps1`：一键包装
- `backup/default_bg.jpg`：底图（换图直接覆盖这个文件）
- `backup/sandbox/`：三张副本的本地模板

## 换底图

```powershell
# 把新图覆盖到 backup\default_bg.jpg，然后运行 update_screensaver.ps1
```

## 若屏保文件名变了（重新选过图）

`TARGETS` 里的文件名是 2026-11-06 实测的时间戳。若在屏保设置里重新选图，
`Sandbox` 下会生成新的 `CROP_*.jpg`，需要更新 `update_screensaver.py` 里的 `TARGETS`：

```powershell
adb shell ls /sdcard/Android/data/com.xrz.standby/files/Sandbox/
```

## 每日 0 点自动（待配）

前提：设备常连电脑（USB/无线 adb）。方案：Windows 计划任务每天 00:00 运行
`update_screensaver.ps1`。未配置，因为设备不常连 PC。

## 回滚

```powershell
adb shell settings put secure screensaver_default_component com.android.deskclock/com.android.deskclock.Screensaver
# 屏保设置里重新选回内置图片即可
```
