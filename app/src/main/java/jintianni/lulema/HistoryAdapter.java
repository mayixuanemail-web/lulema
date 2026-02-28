package jintianni.lulema;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<Map.Entry<String, Integer>> data;

    public HistoryAdapter(List<Map.Entry<String, Integer>> data) {
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
        holder.tvDate.setText(entry.getKey());
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
}