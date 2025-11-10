package com.neovision.onframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 아주 단순한 원형 색상 피커
 * - 탭/드래그한 위치의 색을 RGB로 콜백
 * - 대충 홈킷 느낌 내려고 원형 그라데이션만 그린 버전
 */
public class ColorWheelView extends View {

    public interface OnColorChangeListener {
        void onColorChanged(int r, int g, int b);
    }

    private OnColorChangeListener listener;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap cache;
    private int size;

    public ColorWheelView(Context context) {
        super(context);
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnColorChangeListener(OnColorChangeListener l) {
        this.listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        size = Math.min(w, h);
        buildCache();
    }

    private void buildCache() {
        if (size <= 0) return;
        cache = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(cache);

        // 가운데 흰색 → 바깥쪽으로 무지개 느낌
        int radius = size / 2;
        float cx = radius;
        float cy = radius;

        // 단색 그라데이션만 쓰면 무지개가 안 나와서, 그냥 HSL 돌려서 직접 찍자
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > radius) {
                    cache.setPixel(x, y, Color.TRANSPARENT);
                } else {
                    // angle: 0~360
                    float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                    if (angle < 0) angle += 360f;
                    float sat = dist / radius; // 0~1
                    int color = hsvToColor(angle, sat, 1f);
                    cache.setPixel(x, y, color);
                }
            }
        }
    }

    private int hsvToColor(float h, float s, float v) {
        float[] hsv = new float[]{h, s, v};
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cache != null) {
            canvas.drawBitmap(cache, 0, 0, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cache == null) return true;

        float x = event.getX();
        float y = event.getY();

        if (x < 0 || y < 0 || x >= cache.getWidth() || y >= cache.getHeight()) {
            return true;
        }

        int pixel = cache.getPixel((int) x, (int) y);
        if (pixel != Color.TRANSPARENT) {
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);
            if (listener != null) {
                listener.onColorChanged(r, g, b);
            }
        }

        return true;
    }
}