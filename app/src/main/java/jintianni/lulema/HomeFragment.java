package jintianni.lulema;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

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
    private TextView tvDate, tvTodayCount;
    private android.content.SharedPreferences sharedPreferences;
    private String today;
    private static final String PREF_NAME = "word_record";
    private static final String KEY_LAST_CHECK_IN = "last_check_in_time";
    private SoundPool soundPool;
    private int soundId;

    private TextView progressTarget;
    private android.widget.Button btnSetTarget;
    private static final String KEY_TARGET = "target_count";
    private int todayTarget = 10; // 默认目标

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);

        tvDate = view.findViewById(R.id.tvDate);
        tvTodayCount = view.findViewById(R.id.tvTodayCount);
        rvHistory = view.findViewById(R.id.rvHistory);
        btnCheckIn = view.findViewById(R.id.btnCheckIn);
        btnSetReminder = view.findViewById(R.id.btnSetReminder);
        // 新增目标相关
        progressTarget = view.findViewById(R.id.progressTarget);
        btnSetTarget = view.findViewById(R.id.btnSetTarget);
        // 读取目标
        todayTarget = sharedPreferences.getInt(KEY_TARGET, 10);
        today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        tvDate.setText(today);

        loadHistory();
        updateTodayCount();

        btnCheckIn.setOnClickListener(v -> handleCheckIn());
        btnSetReminder.setOnClickListener(v -> showTimePickerDialog());
        // 设置目标按钮监听
        btnSetTarget.setOnClickListener(v -> showSetTargetDialog());

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
        PendingIntent pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
                }
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
    }

    // 修改原来从输入框读取的逻辑，改为固定加1
    private void saveTodayCount() {
        int addCount = 1;
        String key = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        sharedPreferences.edit().putInt(key, addCount).apply();
        loadHistory();
        updateTodayCount(); // 新增：同步刷新目标进度
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
            // Filter out non-record keys and ensure value is Integer
            // Also filter out any keys that don't look like dates if possible,
            // but primarily filter 'last_check_in_time' which is Long, so instanceof Integer handles it.
            if (entry.getValue() instanceof Integer) {
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

    // 新增：弹窗设置目标
    private void showSetTargetDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("设置今日目标");
        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("请输入目标次数");
        input.setText(String.valueOf(todayTarget));
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!val.isEmpty()) {
                int t = Integer.parseInt(val);
                if (t > 0) {
                    todayTarget = t;
                    sharedPreferences.edit().putInt(KEY_TARGET, todayTarget).apply();
                    updateProgressTarget();
                } else {
                    Toast.makeText(getContext(), "目标需大于0", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 新增：刷新目标进度显示
    private void updateProgressTarget() {
        int todayCount = getTodayCount();
        progressTarget.setText(todayCount + "/" + todayTarget);
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
        tvTodayCount.setText("今日起飞 " + totalToday + " 次");
        updateProgressTarget(); // 新增：同步刷新目标进度
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
