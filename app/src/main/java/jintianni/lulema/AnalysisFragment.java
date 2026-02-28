package jintianni.lulema;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalysisFragment extends Fragment {

    private static final String PREF_NAME = "word_record";
    private TextView tvDailyCount, tvDailyCompare;
    private TextView tvWeeklyCount, tvWeeklyCompare;
    private TextView tvMonthlyCount, tvMonthlyCompare;
    private TextView tvBestCount, tvStreakDays; // 新增
    private LineChartView chartView;
    private com.google.android.material.button.MaterialButton btnChartToggle;
    private SharedPreferences sharedPreferences;
    private boolean isMonthView = false; // Toggle state

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_analysis, container, false);

        tvDailyCount = view.findViewById(R.id.tvDailyCount);
        tvDailyCompare = view.findViewById(R.id.tvDailyCompare);
        tvWeeklyCount = view.findViewById(R.id.tvWeeklyCount);
        tvWeeklyCompare = view.findViewById(R.id.tvWeeklyCompare);
        tvMonthlyCount = view.findViewById(R.id.tvMonthlyCount);
        tvMonthlyCompare = view.findViewById(R.id.tvMonthlyCompare);
        tvBestCount = view.findViewById(R.id.tvBestCount); // 初始化
        tvStreakDays = view.findViewById(R.id.tvStreakDays); // 初始化
        chartView = view.findViewById(R.id.chartView);
        btnChartToggle = view.findViewById(R.id.btnChartToggle);

        sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        btnChartToggle.setOnClickListener(v -> toggleChartMode());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        analyzeData();
    }

    private void toggleChartMode() {
        isMonthView = !isMonthView;
        btnChartToggle.setText(isMonthView ? "近30天起飞趋势 ▾" : "近7天起飞趋势 ▾");
        analyzeData();
    }

    private void analyzeData() {
        if (chartView == null || getContext() == null) return; // 安全检查

        Map<String, ?> allEntries = sharedPreferences.getAll();
        Map<String, Integer> dailyTotals = new HashMap<>();
        SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        SimpleDateFormat sdfDay = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // 1. Aggregate data by day
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().equals("custom_bg_uri")) continue;
            try {
                // Try parsing as full timestamp first
                Date date;
                try {
                    date = sdfKey.parse(entry.getKey());
                } catch (Exception e) {
                    // Fallback for old data format if any
                    date = sdfDay.parse(entry.getKey());
                }

                if (date != null && entry.getValue() instanceof Integer) {
                    String dayKey = sdfDay.format(date);
                    int count = (Integer) entry.getValue();
                    dailyTotals.put(dayKey, dailyTotals.getOrDefault(dayKey, 0) + count);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // --- Calculate History Best ---
        int maxCount = 0;
        for (int count : dailyTotals.values()) {
            if (count > maxCount) maxCount = count;
        }
        if (tvBestCount != null) {
            tvBestCount.setText(String.valueOf(maxCount));
        }

        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        String todayStr = sdfDay.format(today);

        // --- Calculate Current Streak ---
        int streak = 0;
        // Check today
        if (dailyTotals.getOrDefault(todayStr, 0) > 0) {
            streak++;
        }
        // Check days before
        Calendar streakCal = (Calendar) cal.clone();
        streakCal.add(Calendar.DAY_OF_YEAR, -1); // Start checking from yesterday

        while (true) {
            String dateKey = sdfDay.format(streakCal.getTime());
            if (dailyTotals.getOrDefault(dateKey, 0) > 0) {
                streak++;
                streakCal.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                // Special case: if streak started today (streak=1) but yesterday missed, stop.
                // If today is 0, we check yesterday. logic handled below.
                break;
            }
        }

        // If today has 0 record, streak logic usually allows "not broken yet".
        // e.g., streak is counted up to yesterday if today is empty.
        if (dailyTotals.getOrDefault(todayStr, 0) == 0) {
            streak = 0; // 重置
            // 重新计算：如果今天没打卡，看昨天是否打卡，如果昨天打了，那就是昨天的连胜数（未断），或者更严格来说，
            // 很多App如果今天没打卡显示 "昨天已连胜X天" 或直接重置。这里简化逻辑：仅统计"连胜到昨天+今天"。
            // 修正逻辑：如果昨天有数据，连胜数为昨天为止的连胜。如果今天也没数据，昨天也没数据，则为0。

            streakCal = (Calendar) cal.clone();
            streakCal.add(Calendar.DAY_OF_YEAR, -1);
            if (dailyTotals.getOrDefault(sdfDay.format(streakCal.getTime()), 0) > 0) {
                // start counting from yesterday
                while (true) {
                    String dateKey = sdfDay.format(streakCal.getTime());
                    if (dailyTotals.getOrDefault(dateKey, 0) > 0) {
                        streak++;
                        streakCal.add(Calendar.DAY_OF_YEAR, -1);
                    } else {
                        break;
                    }
                }
            }
        }

        if (tvStreakDays != null) {
            tvStreakDays.setText(streak + " 天");
        }

        // --- Daily Stats ---
        int countToday = dailyTotals.getOrDefault(todayStr, 0);

        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayStr = sdfDay.format(cal.getTime());
        int countYesterday = dailyTotals.getOrDefault(yesterdayStr, 0);

        updateStatUI(tvDailyCount, tvDailyCompare, countToday, countYesterday, "较昨日");

        // --- Weekly Stats ---
        // Reset cal to today
        cal.setTime(today);
        int currentWeek = cal.get(Calendar.WEEK_OF_YEAR);
        int currentYear = cal.get(Calendar.YEAR);

        int countThisWeek = 0;
        int countLastWeek = 0;

        // Iterate aggregated daily totals to calculate weekly/monthly (simpler than re-parsing keys)
        for (Map.Entry<String, Integer> entry : dailyTotals.entrySet()) {
            try {
                Date d = sdfDay.parse(entry.getKey());
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                int w = c.get(Calendar.WEEK_OF_YEAR);
                int y = c.get(Calendar.YEAR);

                if (y == currentYear) {
                    if (w == currentWeek) countThisWeek += entry.getValue();
                    if (w == currentWeek - 1) countLastWeek += entry.getValue(); // Simplified logic (doesn't handle year boundary perfectly but ok for simple app)
                }
                // Handle year boundary roughly
                else if (y == currentYear - 1 && currentWeek == 1) {
                     int lastWeekOfYear = c.getActualMaximum(Calendar.WEEK_OF_YEAR);
                     if (w == lastWeekOfYear) countLastWeek += entry.getValue();
                }
            } catch (Exception e) {}
        }

        updateStatUI(tvWeeklyCount, tvWeeklyCompare, countThisWeek, countLastWeek, "较上周");

        // --- Monthly Stats ---
        cal.setTime(today);
        int currentMonth = cal.get(Calendar.MONTH);

        int countThisMonth = 0;
        int countLastMonth = 0;

        for (Map.Entry<String, Integer> entry : dailyTotals.entrySet()) {
            try {
                Date d = sdfDay.parse(entry.getKey());
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                int m = c.get(Calendar.MONTH);
                int y = c.get(Calendar.YEAR);

                if (y == currentYear) {
                    if (m == currentMonth) countThisMonth += entry.getValue();
                    if (m == currentMonth - 1) countLastMonth += entry.getValue();
                }
                else if (y == currentYear - 1 && currentMonth == 0 && m == 11) {
                    countLastMonth += entry.getValue();
                }
            } catch (Exception e) {}
        }

        updateStatUI(tvMonthlyCount, tvMonthlyCompare, countThisMonth, countLastMonth, "较上月");

        // --- Chart Data ---
        List<LineChartView.DataPoint> chartData = new ArrayList<>();
        SimpleDateFormat sdfChartLabel = new SimpleDateFormat("MM-dd", Locale.getDefault());

        int days = isMonthView ? 30 : 7;

        // Go back days-1 + today
        cal.setTime(today);
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1));

        for (int i = 0; i < days; i++) {
            String key = sdfDay.format(cal.getTime());
            String label = sdfChartLabel.format(cal.getTime());
            int val = dailyTotals.getOrDefault(key, 0);
            chartData.add(new LineChartView.DataPoint(label, val));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (chartView != null) {
            chartView.setData(chartData);
        }
    }

    private void updateStatUI(TextView tvCount, TextView tvCompare, int current, int previous, String prefix) {
        if (tvCount == null || tvCompare == null) return;
        tvCount.setText(String.valueOf(current));
        int diff = current - previous;
        String sign = diff >= 0 ? "+" : "";
        tvCompare.setText(prefix + " " + sign + diff);
        tvCompare.setTextColor(diff >= 0 ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336")); // Green for pos, Red for neg
    }
}
