package com.bigme.hooksaver;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 大我屏保 hook：
 * 1) 背景图；
 * 2) 中间日号 tv_day 替换为自定义绘制（透明填充 + 白实色描边 + 黑实色外圈）；
 * 3) 年月文字 2026 .11 → 2026年11月；
 * 4) 其余文字：白字 + 黑边（shadowLayer 近似）。
 */
public class HookInit implements IXposedHookLoadPackage {

    private static final String IMG = "/system/etc/ssbgx.jpg";
    private static final String TAG = "BIGME_HOOK";
    private static final int TV_DAY_ID = 0x7f090265;
    private static final int YEAR_TV_ID = 0x7f09028d;
    private static final int LUNAR_ID = 0x7f090271;
    private static final int JIEQI_ID = 0x7f09026e;

    private static Bitmap cachedBg;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})\\s*[.．]\\s*(\\d{1,2})");

    /** 番茄要隐藏的 Tab 文字 */
    private static final java.util.Set<String> TOMATO_HIDE =
            new java.util.HashSet<>(java.util.Arrays.asList("看剧", "漫画", "商城", "短剧", "赚钱"));

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.dragon.read".equals(lpparam.packageName)) {
            hookTomatoBattery(lpparam);
            return;
        }
        if (!"com.xrz.standby".equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + " loaded in " + lpparam.packageName);

        // 「每天0点」：把 86400 换算成「到下一个 0 点的秒数」，让刷新真正落在 0 点
        try {
            XposedHelpers.findAndHookMethod("com.xrz.settings.screensaver.ScreenSaverClockDialog",
                    lpparam.classLoader, "setAlarm", android.content.Context.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                int freq = (int) (Integer) param.args[1];
                                if (freq == 86400) {
                                    java.util.Calendar now = java.util.Calendar.getInstance();
                                    java.util.Calendar next = java.util.Calendar.getInstance();
                                    next.set(java.util.Calendar.HOUR_OF_DAY, 0);
                                    next.set(java.util.Calendar.MINUTE, 0);
                                    next.set(java.util.Calendar.SECOND, 0);
                                    next.set(java.util.Calendar.MILLISECOND, 0);
                                    next.add(java.util.Calendar.DAY_OF_MONTH, 1);
                                    int secs = (int) ((next.getTimeInMillis() - now.getTimeInMillis()) / 1000);
                                    if (secs > 0) {
                                        param.args[1] = secs;
                                        XposedBridge.log(TAG + " setAlarm -> next midnight in " + secs + "s");
                                    }
                                } else if (freq == 60) {
                                    // 每分钟刷新：若在省电时段（如 0:02-23:58），把 alarm 推到省电结束，锁屏时不再每分钟唤醒
                                    try {
                                        android.content.Context ctx = (android.content.Context) param.args[0];
                                        android.content.SharedPreferences sp = ctx.getSharedPreferences("xrz_settings", 0);
                                        org.json.JSONObject cfg = new org.json.JSONObject(sp.getString("screensaver_config_2", "{}"));
                                        if (cfg.optInt("powerSavingMode", 0) == 1) {
                                            int sh = cfg.optInt("powerSavingStartHour", 0);
                                            int sm = cfg.optInt("powerSavingStartMinute", 0);
                                            int eh = cfg.optInt("powerSavingEndHour", 23);
                                            int em = cfg.optInt("powerSavingEndMinute", 58);
                                            java.util.Calendar now = java.util.Calendar.getInstance();
                                            int nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
                                            int startMin = sh * 60 + sm;
                                            int endMin = eh * 60 + em;
                                            boolean within;
                                            if (startMin <= endMin) {
                                                within = nowMin >= startMin && nowMin < endMin;
                                            } else {
                                                within = nowMin >= startMin || nowMin < endMin;
                                            }
                                            if (within) {
                                                java.util.Calendar target = java.util.Calendar.getInstance();
                                                target.set(java.util.Calendar.HOUR_OF_DAY, eh);
                                                target.set(java.util.Calendar.MINUTE, em);
                                                target.set(java.util.Calendar.SECOND, 0);
                                                target.set(java.util.Calendar.MILLISECOND, 0);
                                                long secs2 = (target.getTimeInMillis() - now.getTimeInMillis()) / 1000;
                                                if (secs2 > 0) {
                                                    param.args[1] = (int) secs2;
                                                    XposedBridge.log(TAG + " setAlarm(mins) -> powerSaving end in " + secs2 + "s");
                                                }
                                            }
                                        }
                                    } catch (Throwable ignore2) {
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " setAlarm err " + t);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook setAlarm fail " + t);
        }

        // 在「时钟刷新间隔」里追加「每天0点」选项
        try {
            XposedHelpers.findAndHookConstructor("com.xrz.settings.adapter.TimeFrequencyOptionAdapter",
                    lpparam.classLoader, java.util.List.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                java.util.List list = (java.util.List) param.args[0];
                                Class<?> optCls = Class.forName("com.xrz.settings.adapter.TimeFrequencyOptionAdapter$TimeOption", false, lpparam.classLoader);
                                java.lang.reflect.Constructor<?> ctor = optCls.getConstructor(String.class, int.class);
                                Object opt = ctor.newInstance("每天0点", 86400);
                                list.add(opt);
                                XposedBridge.log(TAG + " added 每天0点 option");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " add option err " + t);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook adapter fail " + t);
        }

        // 让「86400」在设置页显示成「每天0点」
        try {
            XposedHelpers.findAndHookMethod("com.xrz.settings.screensaver.ScreenSaverStyleSetActivity",
                    lpparam.classLoader, "getClockFrequenceStr", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if ((int) (Integer) param.args[0] == 86400) {
                                param.setResult("每天0点");
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook frequence str fail " + t);
        }

        // 原屏保定时刷新会重设文字，刷新后再套一遍
        try {
            XposedHelpers.findAndHookMethod("com.xrz.settings.screensaver.ScreenSaverClockDialog",
                lpparam.classLoader, "updateTime", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Dialog d = (Dialog) param.thisObject;
                            View yv = d.findViewById(YEAR_TV_ID);
                            if (yv instanceof TextView) {
                                TextView y = (TextView) yv;
                                String src = y.getText() == null ? "" : y.getText().toString();
                                String dst = formatYearMonth(src);
                                if (!dst.equals(src)) y.setText(dst);
                            }
                            // 重新套文字样式 + 清容器背景（防止刷新把白底带回来）
                            View content = d.findViewById(android.R.id.content);
                            if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                                View first = ((ViewGroup) content).getChildAt(0);
                                clearBackgrounds(first);
                                styleOthers((ViewGroup) content);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + " updateTime hook err " + t);
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook updateTime fail " + t);
        }

        // AlarmReceiver 收到 0 点广播后：重建对话框（强制 e-ink 刷新屏幕）
        try {
            XposedHelpers.findAndHookMethod("com.xrz.settings.screensaver.AlarmReceiver",
                    lpparam.classLoader, "onReceive", android.content.Context.class, android.content.Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Class<?> saCls = lpparam.classLoader.loadClass("com.xrz.settings.screensaver.ScreenSaveActivity");
                                java.lang.reflect.Field df = saCls.getField("dialog");
                                Object dlg = df.get(null);
                                if (dlg == null) return;
                                // 只请求重绘（不重建，避免系统崩溃）
                                View dv = ((android.app.Dialog) dlg).getWindow().getDecorView();
                                dv.invalidate();
                                XposedBridge.log(TAG + " alarm fired -> invalidate only");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " rebuild err " + t);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook AlarmReceiver fail " + t);
        }

        // 内容根 onDraw 监测：背景丢失就补回（修通知/重绘后的白底）
        try {
            XposedHelpers.findAndHookMethod(View.class, "onDraw", android.graphics.Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    View v = (View) param.thisObject;
                    try {
                        if (v.getTag(0x7f0f0001) == null) return;
                        if (v.getBackground() instanceof BitmapDrawable) {
                            clearChildBgs(v);
                            return;
                        }
                        if (cachedBg == null) cachedBg = BitmapFactory.decodeFile(IMG);
                        if (cachedBg == null) return;
                        BitmapDrawable nb = new BitmapDrawable(v.getResources(), cachedBg);
                        v.setBackground(nb);
                        XposedBridge.log(TAG + " bg restored");
                        clearChildBgs(v);
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook bg fail " + t);
        }

        // updateLogo：每次刷新（含 updateTime 之后）重新套格式/颜色/背景
        try {
            XposedHelpers.findAndHookMethod("com.xrz.settings.screensaver.ScreenSaverClockDialog",
                    lpparam.classLoader, "updateLogo", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Dialog d = (Dialog) param.thisObject;
                                View yv = d.findViewById(YEAR_TV_ID);
                                if (yv instanceof TextView) {
                                    TextView y = (TextView) yv;
                                    String src = y.getText() == null ? "" : y.getText().toString();
                                    String dst = formatYearMonth(src);
                                    if (!dst.equals(src)) y.setText(dst);
                                }
                                View content = d.findViewById(android.R.id.content);
                                if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                                    View first = ((ViewGroup) content).getChildAt(0);
                                    clearBackgrounds(first);
                                    styleOthers((ViewGroup) content);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " updateLogo err " + t);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook updateLogo fail " + t);
        }

        // 日号：hook TextView.onDraw，直接接管绘制（不替换 View）
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "onDraw", android.graphics.Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    TextView tv = (TextView) param.thisObject;
                    if (tv.getId() != TV_DAY_ID) return;
                    param.setResult(null);
                    try {
                        drawStyled((android.graphics.Canvas) param.args[0], tv);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + " draw err " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook onDraw fail " + t);
        }

        XposedBridge.hookAllMethods(Dialog.class, "show", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Dialog d = (Dialog) param.thisObject;
                    String cn = d.getClass().getName();
                    if (!cn.startsWith("com.xrz.settings.screensaver.ScreenSaver")) return;

                    Bitmap bmp = BitmapFactory.decodeFile(IMG);
                    XposedBridge.log(TAG + " show " + cn + " bmp=" + (bmp != null));
                    if (bmp != null) {
                        BitmapDrawable bg = new BitmapDrawable(d.getContext().getResources(), bmp);
                        View content = d.findViewById(android.R.id.content);
                        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                            View first = ((ViewGroup) content).getChildAt(0);
                            clearBackgrounds(first);
                            first.setBackground(bg);
                            first.setTag(0x7f0f0001, Boolean.TRUE);   // 标记内容根

                            // 裁剪背景层（ImageView CENTER_CROP，横竖屏都不拉伸）
                            try {
                                android.widget.ImageView bgIv = new android.widget.ImageView(d.getContext());
                                bgIv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                bgIv.setImageBitmap(bmp);
                                android.view.ViewGroup.LayoutParams lp;
                                if (first instanceof android.widget.RelativeLayout) {
                                    lp = new android.widget.RelativeLayout.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                                } else {
                                    lp = new android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                                }
                                ((ViewGroup) first).addView(bgIv, 0, lp);
                                XposedBridge.log(TAG + " imageview bg added");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " bg imageview err " + t);
                            }
                        }
                        d.getWindow().setBackgroundDrawable(bg);
                    }

                    applyTexts(d);

                    // 每天0点刷新：直接复刻原版 AlarmReceiver 广播（原版 setAlarm 没被调用）
                    scheduleMidnight(d);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + " err " + t);
                }
            }
        });
    }



    /** 隐藏 Tab 后，把剩余 Tab 均匀分布（按父容器的宽度均分）。 */
    private static void reflowTabs(View tab) {
        try {
            android.view.ViewParent p = tab.getParent();
            if (!(p instanceof ViewGroup)) return;
            ViewGroup container = (ViewGroup) p;
            int n = 0;
            for (int i = 0; i < container.getChildCount(); i++) {
                if (container.getChildAt(i).getVisibility() == View.VISIBLE) n++;
            }
            if (n <= 0) return;
            int total = container.getWidth();
            if (total <= 0) total = container.getMeasuredWidth();
            if (total <= 0) return;
            int w = total / n;
            for (int i = 0; i < container.getChildCount(); i++) {
                View c = container.getChildAt(i);
                if (c.getVisibility() != View.VISIBLE) continue;
                ViewGroup.LayoutParams lp = c.getLayoutParams();
                if (lp != null) {
                    lp.width = w;
                    c.setLayoutParams(lp);
                }
            }
            XposedBridge.log("BIGME_TOMATO reflow " + n + " tabs, w=" + w);
        } catch (Throwable ignore) {
        }
    }

    /** 番茄：隐藏指定的底部/频道 Tab（按文字）。 */
    private void hookTomato(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("BIGME_TOMATO loaded");
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View v = (View) param.thisObject;
                        if (!(v instanceof TextView)) return;
                        CharSequence cs = ((TextView) v).getText();
                        if (cs == null) return;
                        String t = cs.toString();
                        if (!TOMATO_HIDE.contains(t)) return;
                        v.setVisibility(View.GONE);
                        // 移除「Tab 点击容器」（祖父级），彻底去掉点击区
                        android.view.ViewParent vp = v.getParent();
                        if (vp instanceof View) {
                            android.view.ViewParent gp = ((View) vp).getParent();
                            if (gp instanceof ViewGroup) {
                                ((ViewGroup) gp).removeView((View) vp);
                                XposedBridge.log("BIGME_TOMATO removed-ancestor [" + t + "]");
                            } else {
                                ((ViewGroup) vp).removeView(v);
                                XposedBridge.log("BIGME_TOMATO removed [" + t + "]");
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BIGME_TOMATO hook fail " + t);
        }

        // 频道栏的 text 是 attach 之后才设置，hook setText
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        TextView tv = (TextView) param.thisObject;
                        CharSequence cs = tv.getText();
                        if (cs == null) return;
                        String t = cs.toString();
                        if (!TOMATO_HIDE.contains(t)) return;
                        tv.setVisibility(View.GONE);
                        android.view.ViewParent vp = tv.getParent();
                        if (vp instanceof View) {
                            android.view.ViewParent gp = ((View) vp).getParent();
                            if (gp instanceof ViewGroup) {
                                ((ViewGroup) gp).removeView((View) vp);
                                XposedBridge.log("BIGME_TOMATO removed-ancestor(setText) [" + t + "]");
                            } else {
                                ((ViewGroup) vp).removeView(tv);
                                XposedBridge.log("BIGME_TOMATO removed(setText) [" + t + "]");
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BIGME_TOMATO setText hook fail " + t);
        }

        // 拦截底部栏隐藏 Tab 的点击（在 Activity 层按屏幕坐标吞掉）
        try {
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "dispatchTouchEvent", android.view.MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        android.app.Activity act = (android.app.Activity) param.thisObject;
                        if (!act.getPackageName().equals("com.dragon.read")) return;
                        android.view.MotionEvent e = (android.view.MotionEvent) param.args[0];
                        if (e.getActionMasked() != android.view.MotionEvent.ACTION_UP) return;
                        float x = e.getRawX();
                        float y = e.getRawY();
                        // 底部栏区域（y>1300）且 x 在短剧(292-364)/赚钱(500-572) 原位置
                        if (y >= 1300f && x >= 280f && x <= 590f) {
                            param.setResult(true);
                            XposedBridge.log("BIGME_TOMATO consumed click rawX=" + x + " rawY=" + y);
                        }
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BIGME_TOMATO touch hook fail " + t);
        }

        // 启动后默认切到「书架」（首次 onResume 时点一下书架 Tab）
        try {
            XposedHelpers.findAndHookMethod("com.dragon.read.pages.main.MainFragmentActivity",
                    lpparam.classLoader, "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                final android.app.Activity act = (android.app.Activity) param.thisObject;
                                if (tomatoStarted) return;
                                tomatoStarted = true;
                                final android.view.View decor = act.getWindow().getDecorView();
                                final int[] tries = {0};
                                decor.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            View bookshelf = findText(decor, "书架");
                                            if (bookshelf != null) {
                                                View parent = (View) bookshelf.getParent();
                                                boolean ok = parent.performClick();
                                                XposedBridge.log("BIGME_TOMATO bookshelf click=" + ok + " tries=" + tries[0]);
                                                return;
                                            }
                                            if (++tries[0] < 15) {
                                                decor.postDelayed(this, 60);
                                            } else {
                                                XposedBridge.log("BIGME_TOMATO bookshelf not found");
                                            }
                                        } catch (Throwable t) {
                                            XposedBridge.log("BIGME_TOMATO bookshelf err " + t);
                                        }
                                    }
                                });
                            } catch (Throwable ignore) {
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("BIGME_TOMATO resume hook fail " + t);
        }
    }

    private static boolean tomatoStarted = false;

    private static View findText(View root, String text) {
        if (root instanceof android.widget.TextView) {
            CharSequence cs = ((android.widget.TextView) root).getText();
            if (cs != null && text.contentEquals(cs)) return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findText(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }



    /** 番茄阅读页：底部时间旁的电池图标 View 换成百分比文字。 */
    private void hookTomatoBattery(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("BIGME_TOMATO battery loaded");
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View v = (View) param.thisObject;
                        int id = v.getId();
                        if (id == 0) return;
                        String name;
                        try {
                            name = v.getResources().getResourceEntryName(id);
                        } catch (Throwable t) {
                            return;
                        }
                        // 电池图标 View（父容器是 h6o）：直接隐藏
                        android.view.ViewParent par = v.getParent();
                        if (par instanceof ViewGroup && !(v instanceof android.widget.TextView)) {
                            String pnm = null;
                            try {
                                int pid = ((ViewGroup) par).getId();
                                if (pid != 0) pnm = ((ViewGroup) par).getResources().getResourceEntryName(pid);
                            } catch (Throwable ignore) {
                            }
                            if ("h6o".equals(pnm)) {
                                v.setVisibility(View.GONE);
                                return;
                            }
                        }
                        // 阅读页底部信息（页码 jwc / 时间）转黑字
                        if ("jwc".equals(name) && v instanceof android.widget.TextView) {
                            ((android.widget.TextView) v).setTextColor(android.graphics.Color.BLACK);
                        }
                        // 顶部书名（"第X章 ..."）转黑字（并去掉灰色的 Span）
                        if (v instanceof android.widget.TextView) {
                            CharSequence cs = ((android.widget.TextView) v).getText();
                            if (cs != null) {
                                String s = cs.toString();
                                if (s.startsWith("第") && s.contains("章")) {
                                    ((android.widget.TextView) v).setTextColor(android.graphics.Color.BLACK);
                                    if (cs instanceof android.text.Spannable) {
                                        android.text.SpannableString ss = new android.text.SpannableString(cs);
                                        for (android.text.style.ForegroundColorSpan sp : ss.getSpans(0, ss.length(), android.text.style.ForegroundColorSpan.class)) {
                                            ss.removeSpan(sp);
                                        }
                                        ((android.widget.TextView) v).setText(ss);
                                    }
                                }
                            }
                        }
                        // 段末评论气泡的数字：改成黑字（气泡 = 父容器里有 ImageView 背景）
                        if ("h2".equals(name) && v instanceof android.widget.TextView) {
                            android.view.ViewParent pv = v.getParent();
                            if (pv instanceof ViewGroup) {
                                ViewGroup bubble = (ViewGroup) pv;
                                boolean isBubble = false;
                                for (int b = 0; b < bubble.getChildCount(); b++) {
                                    if (bubble.getChildAt(b) instanceof android.widget.ImageView) {
                                        isBubble = true;
                                        break;
                                    }
                                }
                                if (isBubble) {
                                    ((android.widget.TextView) v).setTextColor(android.graphics.Color.BLACK);
                                    XposedBridge.log("BIGME_TOMATO bubble text black");
                                }
                            }
                            return;
                        }
                        if (!"h6o".equals(name) || !(v instanceof ViewGroup)) return;
                        final ViewGroup h = (ViewGroup) v;
                        // 每次 attach 都清理：隐藏非 TextView（电池图标）、时间转黑
                        for (int i = 0; i < h.getChildCount(); i++) {
                            View c = h.getChildAt(i);
                            if (c instanceof android.widget.TextView) {
                                ((android.widget.TextView) c).setTextColor(android.graphics.Color.BLACK);
                            } else {
                                c.setVisibility(View.GONE);
                            }
                        }
                        if (h.getTag() == null) {
                            h.setTag(Boolean.TRUE);
                            h.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    for (int i = 0; i < h.getChildCount(); i++) {
                                        View c = h.getChildAt(i);
                                        if (!(c instanceof android.widget.TextView)) {
                                            c.setVisibility(View.GONE);
                                        }
                                    }
                                }
                            }, 300);
                        }
                        // 加百分比
                        int pct = -1;
                        try {
                            android.os.BatteryManager bm = (android.os.BatteryManager) h.getContext().getSystemService(android.content.Context.BATTERY_SERVICE);
                            pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                        } catch (Throwable ignore) {
                        }
                        if (pct >= 0) {
                            android.widget.TextView tv = new android.widget.TextView(h.getContext());
                            tv.setText(pct + "%");
                            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30f);
                            tv.setTextColor(android.graphics.Color.BLACK);
                            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                            lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                            h.addView(tv, lp);
                            XposedBridge.log("BIGME_TOMATO battery pct=" + pct + "%");
                        }
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BIGME_TOMATO battery fail " + t);
        }

        // 书名：hook setText，每次设置后彻底去掉颜色 Span 并设黑（防番茄后加 Span）
        try {
            XposedHelpers.findAndHookMethod(android.widget.TextView.class, "setText", CharSequence.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                        if (Boolean.TRUE.equals(tv.getTag(0x7f0f0002))) return;
                        CharSequence cs = tv.getText();
                        if (cs == null) return;
                        String s = cs.toString();
                        if (!s.startsWith("第") || !s.contains("章")) return;
                        tv.setTag(0x7f0f0002, Boolean.TRUE);
                        tv.setTextColor(android.graphics.Color.BLACK);
                        if (cs instanceof android.text.Spannable) {
                            android.text.SpannableString ss = new android.text.SpannableString(cs);
                            // 去掉所有会覆盖颜色的 Span
                            for (android.text.style.ForegroundColorSpan sp : ss.getSpans(0, ss.length(), android.text.style.ForegroundColorSpan.class)) {
                                ss.removeSpan(sp);
                            }
                            for (android.text.style.TextAppearanceSpan sp : ss.getSpans(0, ss.length(), android.text.style.TextAppearanceSpan.class)) {
                                ss.removeSpan(sp);
                            }
                            tv.setText(ss);
                        }
                        XposedBridge.log("BIGME_TOMATO title black");
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BIGME_TOMATO setText title fail " + t);
        }

        // 书名（第X章...）：番茄会重新上色，这里强制黑
        try {
            XposedHelpers.findAndHookMethod(android.widget.TextView.class, "setTextColor", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                        CharSequence cs = tv.getText();
                        if (cs == null) return;
                        String s = cs.toString();
                        if (s.startsWith("第") && s.contains("章")) {
                            param.args[0] = android.graphics.Color.BLACK;
                        }
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("BIGME_TOMATO color hook fail " + t);
        }
    }

    private static void clearBackgrounds(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                clearBackgrounds(child);
                if (child.getBackground() != null) {
                    child.setBackground(null);
                }
            }
        }
    }

    /** 设置「每天0点」刷新：发原版的 :screensaver_alarm 广播给 AlarmReceiver。 */
    private void scheduleMidnight(Dialog d) {
        try {
            android.content.Context ctx = d.getContext();
            Object bean = XposedHelpers.getObjectField(d, "saverConfigBean");
            if (bean == null) return;
            int freq = (int) XposedHelpers.callMethod(bean, "getClockFrequence");
            if (freq != 86400) return;

            android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(android.content.Context.ALARM_SERVICE);
            android.content.Intent intent = new android.content.Intent();
            intent.setClassName(ctx, "com.xrz.settings.screensaver.AlarmReceiver");
            intent.setAction(ctx.getPackageName() + ":screensaver_alarm");
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(ctx, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

            java.util.Calendar next = java.util.Calendar.getInstance();
            next.set(java.util.Calendar.HOUR_OF_DAY, 0);
            next.set(java.util.Calendar.MINUTE, 0);
            next.set(java.util.Calendar.SECOND, 0);
            next.set(java.util.Calendar.MILLISECOND, 0);
            next.add(java.util.Calendar.DAY_OF_MONTH, 1);
            long trigger = next.getTimeInMillis();
            try {
                am.setExact(android.app.AlarmManager.RTC_WAKEUP, trigger, pi);
            } catch (Throwable t) {
                am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pi);
            }
            XposedBridge.log(TAG + " midnight alarm set -> " + next.getTime().toLocaleString());
        } catch (Throwable t) {
            XposedBridge.log(TAG + " midnight alarm err " + t);
        }
    }
    private void applyTexts(Dialog d) {
        View content = d.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        // 1) 中间日号：替换为自定义绘制
        View dayView = d.findViewById(TV_DAY_ID);
        if (dayView instanceof TextView) {
            // 日号改用 onDraw hook（不替换 View）
        }

        // 2) 年月文字：2026 .11 -> 2026年11月
        View yearView = d.findViewById(YEAR_TV_ID);
        if (yearView instanceof TextView) {
            TextView y = (TextView) yearView;
            String src = y.getText() == null ? "" : y.getText().toString();
            String dst = formatYearMonth(src);
            if (!dst.equals(src)) {
                y.setText(dst);
                XposedBridge.log(TAG + " yearTv [" + src + "] -> [" + dst + "]");
            }
        }

        // 3) 其余 TextView：白字 + 黑边
        styleOthers((ViewGroup) content);
    }

    private static String formatYearMonth(String s) {
        if (s == null || s.isEmpty()) return s;
        Matcher m = YEAR_PATTERN.matcher(s);
        if (m.find()) {
            return m.group(1) + "年" + m.group(2) + "月";
        }
        return s;
    }

    private void replaceWithStyled(TextView tv, boolean scale) {
        if (tv instanceof StyledTextView) return;   // 已经替换过，不再重复
        try {
            ViewGroup parent = (ViewGroup) tv.getParent();
            if (parent == null) return;
            float density = tv.getResources().getDisplayMetrics().density;
            String text = tv.getText() == null ? "" : tv.getText().toString();

            boolean isDigits = text.matches("[0-9]+");
            float useSize = isDigits ? tv.getTextSize() : tv.getTextSize() * (scale ? 1.45f : 1f);
            StyledTextView styled = new StyledTextView(tv.getContext(), text, useSize, tv.getTypeface(),
                    Color.TRANSPARENT,            // 填充透明
                    Color.BLACK, 20f * density,   // 外圈黑实色
                    Color.WHITE, 8f * density);   // 内圈白实色

            int idx = parent.indexOfChild(tv);
            ViewGroup.LayoutParams lp = tv.getLayoutParams();
            parent.removeViewAt(idx);
            parent.addView(styled, idx, lp);

            // 反射修复 DailyTimeView 内部对 tvDay 的引用
            try {
                View p2 = parent;
                while (p2 != null && !p2.getClass().getName().endsWith("DailyTimeView")) {
                    ViewParent vp = p2.getParent();
                    p2 = (vp instanceof View) ? (View) vp : null;
                }
                if (p2 != null) {
                    java.lang.reflect.Field f = p2.getClass().getDeclaredField("tvDay");
                    f.setAccessible(true);
                    f.set(p2, styled);
                    XposedBridge.log(TAG + " tvDay ref fixed");
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + " ref fix err " + t);
            }
            XposedBridge.log(TAG + " tv_day replaced, text=[" + text + "]");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " replace err " + t);
        }
    }


    /** 日号绘制：黑-白-黑三层边 + 中心挖空（不依赖替换 View）。 */
    private static void drawStyled(android.graphics.Canvas canvas, TextView tv) {
        CharSequence cs = tv.getText();
        String text = cs == null ? "" : cs.toString();
        if (text.isEmpty()) return;
        float density = tv.getResources().getDisplayMetrics().density;
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        android.graphics.Typeface tf = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD);
        try {
            tf = android.graphics.Typeface.createFromAsset(tv.getContext().getAssets(), "arial.ttf");
        } catch (Throwable ignore) {
        }
        p.setTypeface(tf);
        p.setTextSize(tv.getTextSize() * 0.88f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);

        float cx = tv.getWidth() / 2f;
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        float cy = tv.getHeight() / 2f - (fm.ascent + fm.descent) / 2f;

        boolean digit = text.matches("[0-9]+");
        if (digit) {
            int save = canvas.saveLayer(null, null);
            float innerBlack = 3f * density;
            float white = 6f * density;
            float outerBlack = 6f * density;

            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(2f * (outerBlack + white + innerBlack));
            p.setColor(Color.BLACK);
            canvas.drawText(text, cx, cy, p);

            p.setStrokeWidth(2f * (white + innerBlack));
            p.setColor(Color.WHITE);
            canvas.drawText(text, cx, cy, p);

            p.setStrokeWidth(2f * innerBlack);
            p.setColor(Color.BLACK);
            canvas.drawText(text, cx, cy, p);

            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            canvas.drawText(text, cx, cy, p);
            p.setXfermode(null);
            canvas.restoreToCount(save);
        } else {
            float stroke = 4f * density;
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(stroke * 2f);
            p.setColor(Color.BLACK);
            canvas.drawText(text, cx, cy, p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(Color.WHITE);
            canvas.drawText(text, cx, cy, p);
        }
    }

    private static void clearChildBgs(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c.getBackground() != null) c.setBackground(null);
                clearChildBgs(c);
            }
        }
    }

    private void styleOthers(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            if (v instanceof ViewGroup) {
                styleOthers((ViewGroup) v);
                continue;
            }
            if (v instanceof StyledTextView) continue;
            if (!(v instanceof TextView)) continue;
            TextView t = (TextView) v;
            float density = t.getResources().getDisplayMetrics().density;
            int id = t.getId();
            boolean big = (id == LUNAR_ID || id == JIEQI_ID);
            if (big) {
                if (t.getTag() == null) {
                    t.setTag(Boolean.TRUE);
                    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * 1.6f);
                }
                t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            }
            t.setTextColor(Color.WHITE);
            t.setShadowLayer(6f * density, 0f, 0f, Color.BLACK);
            XposedBridge.log(TAG + " style " + Integer.toHexString(id) + " [" + t.getText() + "] big=" + big);
        }
    }
}
