package com.bigme.screensaver;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 简单入口：启动屏保服务并显示说明。 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Bigme Dream 屏保");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
        title.setTextColor(Color.BLACK);

        TextView body = new TextView(this);
        body.setText("熄屏时显示：底图 + 日期 + 农历\n（不含时钟，跨天 00:00 更新）\n\n底图路径：\n" + ScreenSaverDream.BG_PATH
                + "\n\n服务已启动，无需保持本页。");
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        body.setTextColor(Color.BLACK);
        body.setPadding(0, 24, 0, 0);

        root.addView(title);
        root.addView(body);
        setContentView(root);

        Intent svc = new Intent(this, ScreenOffService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }
}
