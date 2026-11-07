package com.bigme.screensaver;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;

/** 生成当天屏保图并（通过 su）覆盖大我屏保的 Sandbox 副本。 */
public final class SaverUpdater {

    private static final String TAG = "BigmeDream";
    private static final String DIR = "/sdcard/Android/data/com.xrz.standby/files/Sandbox";

    /** 文件名:宽:高（2026-11-06 实测的三个 CROP 副本） */
    private static final String[] TARGETS = {
            "CROP_120261106234943809.jpeg:824:1113",
            "CROP_20261106234927039.jpg:862:1166",
            "CROP_220261106234943830.jpeg:824:1114",
    };

    private SaverUpdater() {
    }

    public static boolean update(Context ctx) {
        LocalDate today = LocalDate.now();
        boolean ok = true;
        for (String t : TARGETS) {
            String[] p = t.split(":");
            String name = p[0];
            int w = Integer.parseInt(p[1]);
            int h = Integer.parseInt(p[2]);

            Bitmap bmp = SaverImageGenerator.generate(ctx, w, h, today);
            File tmp = new File(ctx.getCacheDir(), name);
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            } catch (Exception e) {
                Log.w(TAG, "save tmp failed: " + name, e);
                ok = false;
                bmp.recycle();
                continue;
            }
            bmp.recycle();

            String cmd = "cp '" + tmp.getAbsolutePath() + "' '" + DIR + "/" + name + "'";
            try {
                Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                int code = proc.waitFor();
                if (code != 0) {
                    Log.w(TAG, "su cp failed(" + code + "): " + name);
                    ok = false;
                }
            } catch (Exception e) {
                Log.w(TAG, "su exec failed: " + name, e);
                ok = false;
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        Log.i(TAG, "update finished, ok=" + ok);
        return ok;
    }
}
