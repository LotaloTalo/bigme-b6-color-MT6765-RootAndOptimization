# -*- coding: utf-8 -*-
"""
一次性完成：seccfg 解锁 -> 禁用 vbmeta 校验 -> 刷入 Magisk 补丁 boot_a/boot_b。
必须在「mtkclient 先启动等待、设备再进 EDL」的顺序下运行（run_root.ps1 负责编排）。
"""
import sys
import logging

sys.path.insert(0, r"C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools\mtkclient-main")
sys.path.insert(0, r"C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools\_pylibs")

from mtk_api import init, connect
from mtkclient.Library.Partitions.gpt import GptSettings

PATCHED = r"C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\backup\edl\magisk_patched.img"


def main():
    print("== connecting ==", flush=True)
    mtk = init(None, None)
    mtk.config.gpt_settings = GptSettings(0, 0, 0)
    mtk, da = connect(mtk, ".")
    if mtk is None or da is None:
        print("CONNECT_FAILED", flush=True)
        return 2

    print("== seccfg unlock ==", flush=True)
    try:
        mtk.daloader.seccfg("unlock")
        print("SECCFG_OK", flush=True)
    except Exception as e:
        print("SECCFG_ERR", type(e).__name__, e, flush=True)

    print("== vbmeta disable ==", flush=True)
    try:
        da.da_vbmeta(3)
        print("VBMETA_OK", flush=True)
    except Exception as e:
        print("VBMETA_ERR", type(e).__name__, e, flush=True)

    print("== write boot ==", flush=True)
    try:
        da.da_write("user", [PATCHED, PATCHED], ["boot_a", "boot_b"])
        print("BOOT_OK", flush=True)
    except Exception as e:
        print("BOOT_ERR", type(e).__name__, e, flush=True)

    print("== 请长按电源 15 秒重启（脚本内不再调用 reset）==", flush=True)
    print("ALL_DONE", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
