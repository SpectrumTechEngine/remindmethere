package com.thespectrumtechengine.remindmethere;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The card that appears over the incoming-call screen, plus a notification so the
 * reminders are still there after the call.
 */
final class CallerPopup {

    private static final String TAG = "RmtCaller";
    private static final String CHANNEL = "caller";
    private static final int NOTIFICATION_ID = 4210;
    private static final long AUTO_CLOSE_MS = 3 * 60 * 1000;

    private static final int PURPLE = Color.parseColor("#6B3BE6");
    private static final int INK = Color.parseColor("#1F1B2D");
    private static final int INK_2 = Color.parseColor("#5E5873");

    private static View current;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private CallerPopup() {}

    static void show(Context context, List<JSONObject> matches) {
        main.post(() -> {
            showNotification(context, matches);
            if (Settings.canDrawOverlays(context)) {
                try {
                    showCard(context, matches);
                } catch (Exception e) {
                    Log.e(TAG, "Couldn't draw over the call screen", e);
                }
            }
        });
    }

    static void close(Context context) {
        main.post(() -> {
            if (current == null) return;
            try {
                ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).removeView(current);
            } catch (Exception ignored) {
            }
            current = null;
        });
    }

    /* ---------- The card ---------- */

    private static void showCard(Context context, List<JSONObject> matches) {
        close(context);
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 18);
        card.setPadding(pad, dp(context, 14), pad, dp(context, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(context, 22));
        bg.setStroke(dp(context, 2), PURPLE);
        card.setBackground(bg);
        card.setElevation(dp(context, 12));

        // Header: "📞 Mam is calling"   ✕
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(context, "📞 " + contactName(matches) + " is calling", 18, INK, true);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView x = text(context, "✕", 22, INK_2, true);
        x.setPadding(dp(context, 12), 0, 0, 0);
        x.setContentDescription("Close");
        head.addView(x);
        card.addView(head);

        // What to mention
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView sub = text(context, "Don't forget to mention", 14, PURPLE, true);
        sub.setPadding(0, dp(context, 8), 0, 0);
        list.addView(sub);
        for (JSONObject m : matches) {
            JSONArray rems = m.optJSONArray("reminders");
            for (int i = 0; rems != null && i < rems.length(); i++) {
                JSONObject r = rems.optJSONObject(i);
                if (r == null) continue;
                TextView line = text(context, "•  " + r.optString("text"), 15, INK, false);
                line.setPadding(0, dp(context, 4), 0, 0);
                list.addView(line);
                JSONArray items = r.optJSONArray("items");
                for (int j = 0; items != null && j < items.length(); j++) {
                    TextView item = text(context, "☐  " + items.optString(j), 14, INK_2, false);
                    item.setPadding(dp(context, 20), dp(context, 2), 0, 0);
                    list.addView(item);
                }
            }
        }
        // Long lists scroll instead of covering the whole call screen
        int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.45);
        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(list);
        card.addView(scroll);

        TextView foot = text(context, "Remind Me There · tap to close", 12, INK_2, false);
        foot.setPadding(0, dp(context, 12), 0, 0);
        card.addView(foot);

        FrameLayout frame = new FrameLayout(context);
        int side = dp(context, 12);
        frame.setPadding(side, 0, side, 0);
        frame.addView(card);
        View.OnClickListener dismiss = v -> close(context);
        card.setOnClickListener(dismiss);
        x.setOnClickListener(dismiss);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        lp.y = dp(context, 110); // below the caller's name, well above the answer buttons

        wm.addView(frame, lp);
        current = frame;
        View shown = frame;
        main.postDelayed(() -> { if (current == shown) close(context); }, AUTO_CLOSE_MS);
    }

    /* ---------- The notification ---------- */

    private static void showNotification(Context context, List<JSONObject> matches) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        if (!nm.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "When someone calls", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Shows a contact's reminders when they ring you");
            context.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        StringBuilder summary = new StringBuilder();
        for (JSONObject m : matches) {
            JSONArray rems = m.optJSONArray("reminders");
            for (int i = 0; rems != null && i < rems.length(); i++) {
                String t = rems.optJSONObject(i) != null ? rems.optJSONObject(i).optString("text") : "";
                if (t.isEmpty()) continue;
                style.addLine("• " + t);
                if (summary.length() == 0) summary.append(t);
            }
        }
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setColor(PURPLE)
                .setContentTitle("Reminders for " + contactName(matches))
                .setContentText(summary.toString())
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            nm.notify(NOTIFICATION_ID, b.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notifications not allowed", e);
        }
    }

    /* ---------- Helpers ---------- */

    private static String contactName(List<JSONObject> matches) {
        for (JSONObject m : matches) {
            String n = m.optString("contactName");
            if (!n.isEmpty()) return n;
        }
        return "A linked contact";
    }

    private static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
