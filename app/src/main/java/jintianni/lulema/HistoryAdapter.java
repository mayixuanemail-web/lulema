package jintianni.lulema;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<Map.Entry<String, Integer>> data;

    public HistoryAdapter(List<Map.Entry<String, Integer>> data) {
        // 将数据倒序排列
        Collections.reverse(data);
        this.data = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map.Entry<String, Integer> entry = data.get(position);
        // 正确地为历史前7次（最早的7次）设置文案
        int originalIndex = data.size() - 1 - position;
        String customText = null;
        switch (originalIndex) {
            case 0:
                customText = "一破！卧龙出山";
                break;
            case 1:
                customText = "双连！一战成名";
                break;
            case 2:
                customText = "三连！举世皆惊";
                break;
            case 3:
                customText = "四连！天下无敌";
                break;
            case 4:
                customText = "五连！诛天灭地";
                break;
            case 5:
                customText = "六连！诛天灭地";
                break;
            case 6:
                customText = "七连！诛天灭地";
                break;
        }
        if (customText != null) {
            holder.tvDate.setText(customText);
        } else {
            holder.tvDate.setText(entry.getKey());
        }
        holder.tvCount.setText(String.valueOf(entry.getValue()));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvCount = itemView.findViewById(R.id.tvHistoryCount);
        }
    }

    public static void checkForUpdate(final Context context) {
        // 使用清华源作为更新链接
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("发现新版本")
                    .setMessage("检测到有新版本可用，是否前往更新？")
                    .setPositiveButton("去更新", (dialog, which) -> {
                        // 清华源APK下载链接（请替换为实际APK路径）
                        String updateUrl = "https://mirrors.tuna.tsinghua.edu.cn/github-release/mayixuanemail-web/lulema/";
                        // Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
                        // context.startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }, 1000); // Simulate network delay
    }
}