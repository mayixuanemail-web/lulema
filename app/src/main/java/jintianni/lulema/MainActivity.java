package jintianni.lulema;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "word_record";
    private android.content.SharedPreferences sharedPreferences;

    // UI 变量
    private View bgGradient;
    private BottomNavigationView bottomNav;
    private ViewPager2 viewPager;

    private static final String PREF_BG_URI = "custom_bg_uri";
    private static final String PREF_THEME_MODE = "theme_mode"; // Add constant
    private static final int REQUEST_CODE_PERMISSION = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 读取保存的主题设置
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        // 应用主题
        AppCompatDelegate.setDefaultNightMode(themeMode);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 自动检查并请求权限
        checkAndRequestPermissions();
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 初始化 Views
        bgGradient = findViewById(R.id.bgGradient);
        viewPager = findViewById(R.id.viewPager);
        bottomNav = findViewById(R.id.bottomNav);
        // 自动检查更新
        HistoryAdapter.checkForUpdate(this);
        // Setup ViewPager2
        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setUserInputEnabled(true); // 允许滑动切换，也可设为false只允许点击
        // ViewPager 页面切换监听，同步底部栏
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        bottomNav.setSelectedItemId(R.id.action_home);
                        break;
                    case 1:
                        bottomNav.setSelectedItemId(R.id.action_analysis);
                        break;
                    case 2:
                        bottomNav.setSelectedItemId(R.id.action_settings);
                        break;
                }
            }
        });

        // 底部栏点击监听，切换 ViewPager
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_home) {
                viewPager.setCurrentItem(0, true);
                return true;
            } else if (id == R.id.action_analysis) {
                viewPager.setCurrentItem(1, true);
                return true;
            } else if (id == R.id.action_settings) {
                viewPager.setCurrentItem(2, true);
                return true;
            }
            return false;
        });

        // 动态设置 Emoji 图标
        setupEmojiIcons();

        // 加载自定义背景
        String uriStr = sharedPreferences.getString(PREF_BG_URI, null);
        if (uriStr != null) {
            try {
                Uri customBgUri = Uri.parse(uriStr);
                bgGradient.setBackground(getDrawableFromUri(customBgUri));
            } catch (Exception e) {
                bgGradient.setBackgroundResource(R.drawable.bg_gradient);
            }
        } else {
            bgGradient.setBackgroundResource(R.drawable.bg_gradient);
        }
    }

    private void setupEmojiIcons() {
        Menu menu = bottomNav.getMenu();
        // 主页 - 🫎 (驼鹿) 或 🦌 (鹿)
        menu.findItem(R.id.action_home).setIcon(createEmojiDrawable("🦌"));
        // 分析 - 📊 (图表)
        menu.findItem(R.id.action_analysis).setIcon(createEmojiDrawable("📊"));
        // 设置 - ⚙️ (齿轮)
        menu.findItem(R.id.action_settings).setIcon(createEmojiDrawable("⚙️"));
    }

    private Drawable createEmojiDrawable(String emoji) {
        int size = (int) (24 * getResources().getDisplayMetrics().density); // 24dp to px
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size * 0.8f); // 稍微调小一点字体以适应
        paint.setTextAlign(Paint.Align.CENTER);

        // 计算基线，使其垂直居中
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float top = fontMetrics.top;
        float bottom = fontMetrics.bottom;
        int baseLineY = (int) (size / 2f - top / 2f - bottom / 2f);

        canvas.drawText(emoji, size / 2f, baseLineY, paint);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用 READ_MEDIA_IMAGES
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                showPermissionRationale(() -> androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_CODE_PERMISSION));
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 - 12 使用 READ_EXTERNAL_STORAGE
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                showPermissionRationale(() -> androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSION));
            }
        }
    }

    private void showPermissionRationale(Runnable requestPermissionAction) {
        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("需要权限")
                .setMessage("为了能够自定义背景图片，本应用需要访问您的相册权限。\n\n如果点击确定后未弹出提示，请前往设置手动开启。")
                .setPositiveButton("确定", (dialog, which) -> {
                    requestPermissionAction.run();
                    // 检查是否应该显示该权限的解释（如果返回false且权限未授予，说明可能被永久拒绝）
                    // 这里做一个简单的兜底：如果用户之前永久拒绝了，系统弹窗不会出现。
                    // 可以在 onRequestPermissionsResult 中更精确判断，但这里我们可以添加一个“去设置”按钮的逻辑备选，或者简单引导。
                })
                .setNegativeButton("取消", null)
                // 增加一个跳转设置的按钮，防备用户“永久拒绝”后无法操作
                .setNeutralButton("去设置", (dialog, which) -> {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .show();
    }

    // 公开方法供 SettingsFragment 调用
    public void updateBackground(Uri uri) {
        if (uri != null) {
            bgGradient.setBackground(getDrawableFromUri(uri));
        } else {
            bgGradient.setBackgroundResource(R.drawable.bg_gradient);
        }
    }

    // 内部类 ViewPager Adapter
    private static class MainPagerAdapter extends FragmentStateAdapter {
        public MainPagerAdapter(@NonNull androidx.fragment.app.FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new HomeFragment();
                case 1:
                    return new AnalysisFragment();
                case 2:
                    return new SettingsFragment();
                default:
                    return new HomeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    private android.graphics.drawable.Drawable getDrawableFromUri(Uri uri) {
        if (uri == null) return androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_gradient);
        try {
            return android.graphics.drawable.Drawable.createFromStream(getContentResolver().openInputStream(uri), uri.toString());
        } catch (Exception e) {
            return androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_gradient);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 这里可以处理用户授权后的逻辑，比如刷新界面，但目前主要目的是确保权限被请求
    }
}
