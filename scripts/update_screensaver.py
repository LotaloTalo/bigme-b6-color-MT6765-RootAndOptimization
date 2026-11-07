# -*- coding: utf-8 -*-
"""
每天生成屏保图（底图 + 公历日期 + 农历）并覆盖到 Bigme B6 的大我屏保 Sandbox。

用法：
    python update_screensaver.py                 # 用今天的日期
    python update_screensaver.py 2026-11-07      # 指定日期（测试用）

依赖：
    - Pillow（已随 DSH 运行时的 Python 提供）
    - 设备已 adb 连接（USB 或无线调试）

原理（2026-11-06 实测）：
    大我屏保 com.xrz.standby 在“图片选择”选图后会生成 3 个裁剪副本到
    /sdcard/Android/data/com.xrz.standby/files/Sandbox/CROP_*.jpg，
    熄屏时直接读这些文件。覆盖它们即可改变屏保画面（无需 root、无需改 APK）。
"""
import os
import subprocess
import sys
from datetime import date, datetime

from PIL import Image, ImageDraw, ImageFont, ImageOps

# ---------------- 配置 ----------------
ADB = r"C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\tools\platform-tools\adb.exe"
SERIAL = "B6CLR0B2GG5A006000422"
BG = r"C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\backup\default_bg.jpg"
OUT_DIR = r"C:\Users\Public\DeepSeekHarnessWorkspace\BigmeB6\backup\sandbox"
FONT = r"C:\Windows\Fonts\msyh.ttc"
DEVICE_DIR = "/sdcard/Android/data/com.xrz.standby/files/Sandbox"
# 三个副本的文件名与尺寸（实测值；换屏保/换图后可能变化，用 --list 重新探测）
TARGETS = [
    ("CROP_120261106234943809.jpeg", (824, 1113)),
    ("CROP_20261106234927039.jpg", (862, 1166)),
    ("CROP_220261106234943830.jpeg", (824, 1114)),
]

# ---------------- 农历（1900-2100） ----------------
LUNAR_INFO = [
    0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,
    0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,
    0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,
    0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,
    0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,
    0x06ca0,0x0b550,0x15355,0x04da0,0x0a5b0,0x14573,0x052b0,0x0a9a8,0x0e950,0x06aa0,
    0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,
    0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6,
    0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,
    0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x05ac0,0x0ab60,0x096d5,0x092e0,
    0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,
    0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,
    0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,
    0x05aa0,0x076a3,0x096d0,0x04afb,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45,
    0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0,
    0x14b63,0x09370,0x049f8,0x04970,0x064b0,0x168a6,0x0ea50,0x06b20,0x1a6c4,0x0aae0,
    0x0a2e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4,
    0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0,
    0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b60,0x0a570,0x054e4,0x0d160,
    0x0e968,0x0d520,0x0daa0,0x16aa6,0x056d0,0x04ae0,0x0a9d4,0x0a2d0,0x0d150,0x0f252,
    0x0d520,
]
GAN = "甲乙丙丁戊己庚辛壬癸"
ZHI = "子丑寅卯辰巳午未申酉戌亥"
MONTH_NAMES = ["正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"]
DAY_NAMES = ["初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
             "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
             "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"]


def _leap_month(y):
    return LUNAR_INFO[y - 1900] & 0xf


def _leap_days(y):
    return 0 if _leap_month(y) == 0 else (30 if (LUNAR_INFO[y - 1900] & 0x10000) else 29)


def _month_days(y, m):
    return 29 if (LUNAR_INFO[y - 1900] & (0x10000 >> m)) == 0 else 30


def _year_days(y):
    info = LUNAR_INFO[y - 1900]
    s = 348
    i = 0x8000
    while i > 0x8:
        if info & i:
            s += 1
        i >>= 1
    return s + _leap_days(y)


def lunar_text(d: date):
    offset = (d - date(1900, 1, 31)).days
    if offset < 0:
        return ""
    year = 1900
    while offset >= _year_days(year):
        offset -= _year_days(year)
        year += 1
    leap = _leap_month(year)
    is_leap = False
    m = 1
    temp = 0
    while m < 13 and offset > 0:
        if leap > 0 and m == leap + 1 and not is_leap:
            m -= 1
            is_leap = True
            temp = _leap_days(year)
        else:
            temp = _month_days(year, m)
        if is_leap and m == leap + 1:
            is_leap = False
        offset -= temp
        m += 1
    if offset == 0 and leap > 0 and m == leap + 1:
        if is_leap:
            is_leap = False
        else:
            is_leap = True
            m -= 1
    if offset < 0:
        offset += temp
        m -= 1
    day = offset + 1
    gz = GAN[(year - 4) % 10] + ZHI[(year - 4) % 12]
    month_name = ("闰" if is_leap else "") + MONTH_NAMES[m - 1] + "月"
    return gz + "年" + month_name + DAY_NAMES[day - 1]


WEEKDAYS = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]


def solar_text(d: date):
    return f"{d.year}.{d.month:02d}  {d.day:02d}  {WEEKDAYS[d.weekday()]}"


def make_image(bg_path, size, d, out_path):
    """样例布局：YYYY年 MM月（上）/ DD（屏幕正中，横纵居中）/ 农历（下）；白字+黑阴影。"""
    w, h = size
    bg = Image.open(bg_path).convert("RGB")
    im = ImageOps.fit(bg, (w, h), Image.LANCZOS)
    dr = ImageDraw.Draw(im)

    bold = r"C:\Windows\Fonts\msyhbd.ttc"
    if not os.path.exists(bold):
        bold = FONT

    ym = f"{d.year}年 {d.month:02d}月"
    dd = f"{d.day:02d}"
    lunar = lunar_text(d)

    # 整体缩放系数（以屏幕中心为基准，字号与上下偏移同比放大）
    SCALE = 1.6
    f_ym = ImageFont.truetype(FONT, int(58 * SCALE))
    f_dd = ImageFont.truetype(bold, int(210 * SCALE))
    f_lu = ImageFont.truetype(FONT, int(52 * SCALE))
    offset = int(220 * SCALE)

    cx, cy = w // 2, h // 2

    # 白字 + 等宽细描边（无偏移阴影，避免右下更粗）
    for text, font, y, sw in (
        (dd, f_dd, cy, 5),
        (ym, f_ym, cy - offset, 4),
        (lunar, f_lu, cy + offset, 4),
    ):
        dr.text((cx, y), text, font=font, fill=(255, 255, 255),
                anchor="mm", stroke_width=sw, stroke_fill=(0, 0, 0))
    im.save(out_path, "JPEG", quality=92)


def push(local, remote):
    r = subprocess.run([ADB, "-s", SERIAL, "push", local, remote],
                       capture_output=True, text=True, timeout=120)
    ok = r.returncode == 0
    print(("OK  " if ok else "FAIL ") + os.path.basename(local))
    if not ok:
        print(r.stderr.strip()[:300])
    return ok


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--list":
        r = subprocess.run([ADB, "-s", SERIAL, "shell",
                            "ls " + DEVICE_DIR], capture_output=True, text=True)
        print(r.stdout)
        return
    if len(sys.argv) > 1:
        d = datetime.strptime(sys.argv[1], "%Y-%m-%d").date()
    else:
        d = date.today()

    solar = solar_text(d)
    lunar = lunar_text(d)
    print("日期:", solar, "/", lunar)

    os.makedirs(OUT_DIR, exist_ok=True)
    for name, size in TARGETS:
        local = os.path.join(OUT_DIR, name)
        make_image(BG, size, d, local)
        push(local, f"{DEVICE_DIR}/{name}")
    print("完成。熄屏一次即可看到新日期。")


if __name__ == "__main__":
    main()
