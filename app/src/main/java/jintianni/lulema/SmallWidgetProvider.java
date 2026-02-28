package jintianni.lulema;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class SmallWidgetProvider extends AppWidgetProvider {

    private static final String PREF_NAME = "word_record";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_small);

        // Get Today's count
        int count = getTodayCount(context);
        views.setTextViewText(R.id.tvWidgetCount, String.valueOf(count));

        // Click to Open App (or we could make it check-in directly, but opening app is safer for now)
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.ivIcon, pendingIntent);
        views.setOnClickPendingIntent(R.id.tvWidgetCount, pendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int getTodayCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String todayPrefix = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Map<String, ?> all = prefs.getAll();
        int totalToday = 0;

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(todayPrefix) && entry.getValue() instanceof Integer && !entry.getKey().equals("custom_bg_uri")) {
                totalToday += (Integer) entry.getValue();
            }
        }
        return totalToday;
    }
}
