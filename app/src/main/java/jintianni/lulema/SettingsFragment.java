package jintianni.lulema;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

public class SettingsFragment extends Fragment {

    private static final int REQUEST_CODE_PERMISSION = 1002;
    private static final String PREF_NAME = "word_record";
    private static final String PREF_BG_URI = "custom_bg_uri";
    private static final String PREF_THEME_MODE = "theme_mode"; // Key for theme setting
    private SharedPreferences sharedPreferences;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private android.widget.TextView tvCurrentTheme; // TextView to update theme status

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        MaterialCardView cardChangeBg = view.findViewById(R.id.cardChangeBg);
        cardChangeBg.setOnClickListener(v -> checkPermissionAndPickImage());

        MaterialCardView cardResetBg = view.findViewById(R.id.cardResetBg);
        cardResetBg.setOnClickListener(v -> resetBackground());

        // Theme Selection Logic
        MaterialCardView cardTheme = view.findViewById(R.id.cardTheme);
        tvCurrentTheme = view.findViewById(R.id.tvCurrentTheme);
        updateThemeText(); // Set initial text

        cardTheme.setOnClickListener(v -> showThemeSelectionDialog());

        MaterialCardView cardCheckUpdate = view.findViewById(R.id.cardCheckUpdate);
        android.widget.TextView tvVersion = view.findViewById(R.id.tvVersion);

        try {
            String versionName = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
            tvVersion.setText("当前版本：" + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("当前版本：未知");
        }

        cardCheckUpdate.setOnClickListener(v -> {
            Toast.makeText(getContext(), "正在检查更新...", Toast.LENGTH_SHORT).show();
            // 模拟检查过程
            v.postDelayed(() -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "当前已是最新版本", Toast.LENGTH_SHORT).show();
                }
            }, 1000);
        });

        // 在 SettingsFragment 中添加 Pin Widget 逻辑
        MaterialCardView cardPinWidget = view.findViewById(R.id.cardPinWidget); // Need to add to XML
        if (cardPinWidget != null) {
            cardPinWidget.setOnClickListener(v -> requestPinWidget());
        }

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    try {
                        requireActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                    sharedPreferences.edit().putString(PREF_BG_URI, uri.toString()).apply();

                    // 更新 Activity 的背景
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateBackground(uri);
                    }
                    Toast.makeText(getContext(), "背景已更换", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return view;
    }

    private void checkPermissionAndPickImage() {
        Context context = getContext();
        if (context == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_CODE_PERMISSION);
                return;
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSION);
                return;
            }
        }
        pickImage();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            pickImageLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法启动图片选择", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetBackground() {
        sharedPreferences.edit().remove(PREF_BG_URI).apply();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateBackground(null);
        }
        Toast.makeText(getContext(), "已恢复默认背景", Toast.LENGTH_SHORT).show();
    }

    private void requestPinWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = requireContext().getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(requireContext(), SmallWidgetProvider.class);

            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                Intent pinnedWidgetCallbackIntent = new Intent(requireContext(), MainActivity.class);
                // Use explicit intent for broadcast receiver if we had one for callbacks, but MainActivity works as a dummy target.
                // However, PendingIntent.getBroadcast might be problematic if targeting an Activity.
                // Let's use PendingIntent.getActivity to be safe for MainActivity, or just null if we don't care about callback.
                // Actually, requestPinAppWidget takes a PendingIntent.
                // If we use null for successCallback, it simply won't notify. This is safer for simple usage.

                // appWidgetManager.requestPinAppWidget(myProvider, null, successCallback);
                // Try null callback first to rule out Intent issues.
                boolean success = appWidgetManager.requestPinAppWidget(myProvider, null, null);

                if (!success) {
                     Toast.makeText(getContext(), "系统已尝试添加，请查看桌面", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "您的桌面不支持自动添加组件，请手动长按桌面添加", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getContext(), "请手动长按桌面添加“小鹿起飞”组件", Toast.LENGTH_LONG).show();
        }
    }

    private void showThemeSelectionDialog() {
        String[] items = {"跟随系统", "浅色模式", "深色模式"};
        int currentMode = sharedPreferences.getInt(PREF_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int checkedItem = 0;
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) checkedItem = 1;
        else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) checkedItem = 2;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择主题模式")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    if (which == 1) mode = AppCompatDelegate.MODE_NIGHT_NO;
                    else if (which == 2) mode = AppCompatDelegate.MODE_NIGHT_YES;

                    // Save preference
                    sharedPreferences.edit().putInt(PREF_THEME_MODE, mode).apply();

                    // Apply theme immediately
                    AppCompatDelegate.setDefaultNightMode(mode);

                    // Update text (might not look updated until activity recreates, but good to have logic)
                    updateThemeText();

                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateThemeText() {
        int mode = sharedPreferences.getInt(PREF_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        String text = "跟随系统";
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) text = "浅色模式";
        else if (mode == AppCompatDelegate.MODE_NIGHT_YES) text = "深色模式";
        tvCurrentTheme.setText(text);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage();
            } else {
                if (getView() != null && getActivity() != null) {
                    View anchor = getActivity().findViewById(R.id.navCardView);
                    Snackbar snackbar = Snackbar.make(getView(), "请授予图片访问权限以更换背景", Snackbar.LENGTH_SHORT);
                    if (anchor != null) {
                        snackbar.setAnchorView(anchor);
                    }
                    snackbar.show();
                }
            }
        }
    }
}
