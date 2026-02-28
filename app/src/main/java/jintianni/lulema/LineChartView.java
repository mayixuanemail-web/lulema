package jintianni.lulema;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LineChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();

    private List<DataPoint> dataList = new ArrayList<>();
    private float maxValue = 100;
    private int lineColor = Color.parseColor("#6200EE");

    public LineChartView(Context context) {
        super(context);
        init();
    }

    public LineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context context = getContext();
        // Check if dark mode is active to adjust hardcoded colors if needed,
        // or refer to attributes. Ideally, use ?attr/colorOnSurface.
        // For simplicity in View, we'll use a dynamic color or simple logic.
        int textColor = Color.DKGRAY;
        int gridColor = Color.LTGRAY;

        int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            textColor = Color.LTGRAY; // Light text on dark bg
            gridColor = Color.DKGRAY; // Dark grid on dark bg (or lighter if bg is very dark)
            // Or better: #40FFFFFF for grid
            gridColor = Color.parseColor("#40FFFFFF");
        }

        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(8f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setColor(lineColor);
        dotPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(textColor);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);
    }

    public void setData(List<DataPoint> data) {
        this.dataList = data;
        maxValue = 0;
        for (DataPoint item : data) {
            if (item.value > maxValue) {
                maxValue = item.value;
            }
        }
        if (maxValue == 0) maxValue = 10; // default max

        // Add minimal padding to max value so the highest point isn't cut off at the top
        maxValue = maxValue * 1.2f;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataList == null || dataList.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();
        int paddingBottom = 60; // Space for date labels
        int paddingTop = 40;    // Space for values
        int paddingSide = 50;   // Side padding

        int availableHeight = height - paddingBottom - paddingTop;
        int count = dataList.size();

        if (count < 2) return; // Need at least 2 points for a line

        float step = (float) (width - 2 * paddingSide) / (count - 1);

        // Draw Grid Lines (3 lines)
        float lineY0 = height - paddingBottom;
        float lineY50 = height - paddingBottom - availableHeight / 2f;
        float lineY100 = paddingTop;

        canvas.drawLine(paddingSide, lineY0, width - paddingSide, lineY0, gridPaint);
        canvas.drawLine(paddingSide, lineY50, width - paddingSide, lineY50, gridPaint);
        canvas.drawLine(paddingSide, lineY100, width - paddingSide, lineY100, gridPaint);

        linePath.reset();

        // Calculate points
        List<Float[]> points = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            DataPoint item = dataList.get(i);
            float x = paddingSide + i * step;
            float y = height - paddingBottom - (item.value / maxValue) * availableHeight;

            points.add(new Float[]{x, y});

            if (i == 0) {
                linePath.moveTo(x, y);
            } else {
                linePath.lineTo(x, y);
            }
        }

        // Draw Line
        canvas.drawPath(linePath, linePaint);

        // Draw Dots and Text (skip text if too crowded)
        int labelInterval = 1;
        if (count > 10) labelInterval = count / 7; // Show around 7 labels max to avoid crowding

        for (int i = 0; i < count; i++) {
            DataPoint item = dataList.get(i);
            Float[] point = points.get(i);
            float x = point[0];
            float y = point[1];

            // Draw dot
            canvas.drawCircle(x, y, 10f, dotPaint);

            // Draw white inner dot for "ring" effect
            Paint whitePaint = new Paint(dotPaint);
            whitePaint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, 6f, whitePaint);

            // Draw label and value conditionally
            if (i % labelInterval == 0 || i == count - 1) {
                canvas.drawText(item.label, x, height - 15, textPaint);
                if (item.value > 0) {
                    canvas.drawText(String.valueOf((int)item.value), x, y - 20, textPaint);
                }
            }
        }
    }

    public static class DataPoint {
        String label;
        float value;

        public DataPoint(String label, float value) {
            this.label = label;
            this.value = value;
        }
    }
}
