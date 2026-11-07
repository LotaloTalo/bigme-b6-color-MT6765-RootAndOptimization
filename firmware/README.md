# Firmware（MT6765 / Bigme B6）

适用于 **大我 B6（彩屏版，HiBreak）**，联发科 MT6765，Android 14。

## 文件说明

| 文件 | 说明 |
|---|---|
| `boot_a.img` / `boot_b.img` | 原始 boot 分区（A/B 双槽） |
| `init_boot_a.img` / `init_boot_b.img` | 原始 init_boot 分区 |
| `vbmeta_a.img` / `vbmeta_b.img` | 原始 vbmeta 分区（校验禁用用） |
| `magisk_patched.img` | 已用 Magisk 28.1 打补丁的 boot（可直接刷入获取 root） |

## 使用注意

- **仅适用于同型号设备**（MT6765 / Bigme B6 彩屏版），刷入其他机型有变砖风险
- 刷入前务必先备份自己设备的原始分区
- `magisk_patched.img` 已包含 Magisk 补丁，刷入后需安装 Magisk App 完成配置
- 刷入方式：fastboot / EDL（详见主 README 的 root 章节）

## 来源

从本人设备上备份导出，未做任何修改（除 `magisk_patched.img` 为 Magisk 打补丁生成）。
