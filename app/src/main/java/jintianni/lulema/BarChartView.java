package jintianni.lulema;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BarChartView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<BarData> dataList = new ArrayList<>();
    private int barColor = Color.parseColor("#6200EE");
    private float maxValue = 100;

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);
        // DashPathEffect for dotted line if needed, but simple line is ok for now
    }

    public void setData(List<BarData> data) {
        this.dataList = data;
        maxValue = 0;
        for (BarData item : data) {
            if (item.value > maxValue) {
                maxValue = item.value;
            }
        }
        if (maxValue == 0) maxValue = 100; // default max
        invalidate(); // Redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataList == null || dataList.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();
        int paddingBottom = 60; // Space for text
        int paddingTop = 40; // Increased top padding for value text
        int paddingSide = 40;

        int availableHeight = height - paddingBottom - paddingTop;
        int count = dataList.size();

        // Draw Grid Lines (3 lines: 0%, 50%, 100%)
        float lineY0 = height - paddingBottom;
        float lineY50 = height - paddingBottom - availableHeight / 2f;
        float lineY100 = paddingTop;

        canvas.drawLine(paddingSide, lineY0, width - paddingSide, lineY0, gridPaint);
        canvas.drawLine(paddingSide, lineY50, width - paddingSide, lineY50, gridPaint);
        canvas.drawLine(paddingSide, lineY100, width - paddingSide, lineY100, gridPaint);

        // Calculate bar width
        float step = (float) (width - 2 * paddingSide) / count;
        float barWidth = step * 0.6f;

        for (int i = 0; i < count; i++) {
            BarData item = dataList.get(i);
            float barHeight = (item.value / maxValue) * availableHeight;

            float left = paddingSide + i * step + (step - barWidth) / 2;
            float right = left + barWidth;
            float bottom = height - paddingBottom;
            float top = bottom - barHeight;

            // Highlight the max value bar
            if (item.value == maxValue && maxValue > 0) {
                paint.setColor(Color.parseColor("#3700B3")); // Darker color for max
            } else {
                paint.setColor(barColor);
            }

            // Draw bar with rounded top corners if possible, or just rect
            canvas.drawRect(left, top, right, bottom, paint);

            // Draw label
            canvas.drawText(item.label, left + barWidth / 2, height - 15, textPaint);

            // Draw value above bar
            canvas.drawText(String.valueOf((int)item.value), left + barWidth / 2, top - 10, textPaint);
        }
    }

    public static class BarData {
        String label;
        float value;

        public BarData(String label, float value) {
            this.label = label;
            this.value = value;
        }
    }
}
