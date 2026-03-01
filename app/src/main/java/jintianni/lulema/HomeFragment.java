package jintianni.lulema;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    // 移除 etCount, fabSave
    // 新增 btnCheckIn
    private com.google.android.material.button.MaterialButton btnCheckIn;
    private com.google.android.material.button.MaterialButton btnSetReminder; // 新增
    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private List<Map.Entry<String, Integer>> historyList = new ArrayList<>();
    private android.content.SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "word_record";
    private static final String KEY_LAST_CHECK_IN = "last_check_in_time";
    private SoundPool soundPool;
    private int soundId;

    private TextView progressTarget;
    private Slider seekBarTarget;
    private static final String KEY_TARGET = "target_count";
    private int todayTarget = 10; // 默认目标
    private static final int TARGET_MIN = 1;
    private static final int TARGET_MAX = 100;
    private static final java.util.regex.Pattern HISTORY_KEY_PATTERN =
            java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}");
    private static final int REMINDER_REQUEST_CODE = 2001;
    private static final String KEY_TARGET_ACHIEVED_PREFIX = "target_achieved_"; // + yyyy-MM-dd

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);

        rvHistory = view.findViewById(R.id.rvHistory);
        btnCheckIn = view.findViewById(R.id.btnCheckIn);
        btnSetReminder = view.findViewById(R.id.btnSetReminder);
        // 新增目标相关
        progressTarget = view.findViewById(R.id.progressTarget);
        seekBarTarget = view.findViewById(R.id.seekBarTarget);

        int savedTarget = sharedPreferences.getInt(KEY_TARGET, 10);
        todayTarget = Math.min(Math.max(savedTarget, TARGET_MIN), TARGET_MAX);
        if (seekBarTarget != null) {
            seekBarTarget.setValue(todayTarget);

            seekBarTarget.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    todayTarget = (int) value;
                    updateProgressTarget();
                }
            });

            seekBarTarget.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override public void onStartTrackingTouch(@NonNull Slider slider) {}

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    int value = (int) slider.getValue();
                    if (value != todayTarget) {
                        todayTarget = value;
                    }
                    sharedPreferences.edit().putInt(KEY_TARGET, todayTarget).apply();
                    updateProgressTarget();

                    // 新增：用户把目标调低到已达成时，也要触发一次恭喜
                    maybeShowTargetAchievedDialog();
                }
            });
        }

        // btnSetTarget 不再使用（布局里已隐藏），避免误触
        Button btnSetTarget = view.findViewById(R.id.btnSetTarget);
        if (btnSetTarget != null) btnSetTarget.setOnClickListener(null);

        loadHistory();
        updateTodayCount();

        btnCheckIn.setOnClickListener(v -> handleCheckIn());
        btnSetReminder.setOnClickListener(v -> showTimePickerDialog());

        // Initialize SoundPool
        android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();

        return view;
    }

    private void showTimePickerDialog() {
        // Check Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1003);
                return;
            }
        }

        // Check Alarm Permission for Android 12+ (if using Exact Alarm, but we'll use Standard for simplicity or handle checking)
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
                Toast.makeText(getContext(), "请授予精确闹钟权限以确保提醒准确", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                (view, hourOfDay, minuteOfHour) -> setReminder(hourOfDay, minuteOfHour),
                hour, minute, true);
        timePickerDialog.show();
    }

    private void setReminder(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);

        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(requireContext(), ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                // 先取消旧提醒，避免被系统/重复 PendingIntent 覆盖
                alarmManager.cancel(pendingIntent);

                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);

                Toast.makeText(getContext(), String.format(Locale.getDefault(), "已设置提醒： %02d:%02d", hour, minute), Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Toast.makeText(getContext(), "设置失败，请检查权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleCheckIn() {
        // Perform Haptic Feedback
        Context context = getContext();
        if (context != null) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
        }

        long now = System.currentTimeMillis();
        long lastTime = sharedPreferences.getLong(KEY_LAST_CHECK_IN, 0);

        if (now - lastTime < 60 * 1000) { // 1 minute cooldown
            Toast.makeText(getContext(), "飞得太快了，小心铁杵磨成针", Toast.LENGTH_SHORT).show();
            return;
        }

        saveTodayCount();
        sharedPreferences.edit().putLong(KEY_LAST_CHECK_IN, now).apply();

        // 新增：检查是否达成目标
        maybeShowTargetAchievedDialog();
    }

    // 修改原来从输入框读取的逻辑，改为固定加1
    private void saveTodayCount() {
        int addCount = 1;
        String key = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        sharedPreferences.edit().putInt(key, addCount).apply();
        loadHistory();
        updateTodayCount();
        // 不在这里弹窗，避免与冷却提示/其它提示冲突；由 handleCheckIn 统一触发
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistory(); // Reload in case data changed elsewhere
        updateTodayCount();
    }

    private void loadHistory() {
        Map<String, ?> all = sharedPreferences.getAll();
        historyList.clear();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            // 仅把真正的历史打卡记录加入列表：key 必须是时间戳格式 + value 必须是 Integer
            if (entry.getValue() instanceof Integer && HISTORY_KEY_PATTERN.matcher(entry.getKey()).matches()) {
                historyList.add(new SimpleEntry(entry.getKey(), (Integer) entry.getValue()));
            }
        }
        // Sort descending by date (key)
        Collections.sort(historyList, (a, b) -> b.getKey().compareTo(a.getKey()));

        if (adapter == null) {
            adapter = new HistoryAdapter(historyList);
            // Must set LayoutManager if not set in XML (it is not set in XML usually for nested scrolling)
            if (rvHistory.getLayoutManager() == null) {
                rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
            }
            rvHistory.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }


    // 删除/不再使用 showSetTargetDialog()
    // ...existing code...

    // 新增：刷新目标进度
    private void updateProgressTarget() {
        int todayCount = getTodayCount();
        progressTarget.setText(getString(R.string.progress_format, todayCount, todayTarget));
    }

    // 新增：获取今日累计
    private int getTodayCount() {
        String todayPrefix = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Map<String, ?> all = sharedPreferences.getAll();
        int totalToday = 0;
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(todayPrefix) && entry.getValue() instanceof Integer && !entry.getKey().equals("custom_bg_uri")) {
                totalToday += (Integer) entry.getValue();
            }
        }
        return totalToday;
    }

    private void updateTodayCount() {
        String todayPrefix = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Map<String, ?> all = sharedPreferences.getAll();
        int totalToday = 0;

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(todayPrefix) && entry.getValue() instanceof Integer && !entry.getKey().equals("custom_bg_uri")) {
                totalToday += (Integer) entry.getValue();
            }
        }

        updateProgressTarget();
    }

    private void maybeShowTargetAchievedDialog() {
        if (!isAdded()) return;

        int todayCount = getTodayCount();
        if (todayTarget <= 0) return;
        if (todayCount < todayTarget) return;

        String todayPrefix = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String achievedKey = KEY_TARGET_ACHIEVED_PREFIX + todayPrefix;
        boolean alreadyShown = sharedPreferences.getBoolean(achievedKey, false);
        if (alreadyShown) return;

        sharedPreferences.edit().putBoolean(achievedKey, true).apply();

        new AlertDialog.Builder(requireContext())
                .setTitle("恭喜达成目标")
                .setMessage("今天已完成 " + todayCount + "/" + todayTarget + "！继续保持～")
                .setPositiveButton("好耶", null)
                .show();
    }

    private static class SimpleEntry implements java.util.Map.Entry<String, Integer> {
        private final String key;
        private Integer value;
        SimpleEntry(String key, Integer value) { this.key = key; this.value = value; }
        @Override public String getKey() { return key; }
        @Override public Integer getValue() { return value; }
        @Override public Integer setValue(Integer value) { Integer old = this.value; this.value = value; return old; }
    }
}
