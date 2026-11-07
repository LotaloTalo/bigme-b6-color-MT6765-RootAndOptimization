package com.bigme.hooksaver;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 给大我屏保的对话框（日历时钟/图片）设置背景图。
 * 做法：hook Dialog.show() 之后，直接改「内容根 View」的背景——
 * 绕开窗口/主题那层（改窗口背景会被主题的 windowBackground 盖住）。
 */
public class HookInit implements IXposedHookLoadPackage {

    private static final String IMG = "/system/etc/ssbgx.jpg";
    private static final String TAG = "BIGME_HOOK";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.xrz.standby".equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + " loaded in " + lpparam.packageName);

        hookDialog(lpparam, "com.xrz.settings.screensaver.ScreenSaverClockDialog");
        hookDialog(lpparam, "com.xrz.settings.screensaver.ScreenSaverPicDialog");
    }

    private void hookDialog(XC_LoadPackage.LoadPackageParam lpparam, final String cls) {
        try {
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Dialog d = (Dialog) param.thisObject;
                        Bitmap bmp = BitmapFactory.decodeFile(IMG);
                        XposedBridge.log(TAG + " show " + cls + " bmp=" + (bmp != null));
                        if (bmp == null) return;
                        BitmapDrawable bg = new BitmapDrawable(d.getContext().getResources(), bmp);

                        View content = d.findViewById(android.R.id.content);
                        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                            View child = ((ViewGroup) content).getChildAt(0);
                            child.setBackground(bg);
                            XposedBridge.log(TAG + " content root bg set");
                        }
                        d.getWindow().setBackgroundDrawable(bg);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + " err " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook fail " + cls + " " + t);
        }
    }
}
