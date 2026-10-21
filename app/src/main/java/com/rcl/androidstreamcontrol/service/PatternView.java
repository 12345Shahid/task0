package com.rcl.androidstreamcontrol.service;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class PatternView extends View {

    private Paint dotPaint;
    private Paint linePaint;
    private float[][] dotPositions = new float[9][2]; // 3x3 grid
    private List<Integer> currentPattern = new ArrayList<>();
    private float currentX, currentY;
    private boolean isDrawing = false;
    private float dotRadius = 30f;

    public PatternView(Context context) {
        super(context);
        init();
    }

    public PatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dotPaint = new Paint();
        dotPaint.setColor(Color.WHITE);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#4488FF"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(15f);
        linePaint.setAntiAlias(true);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateDotPositions(w, h);
    }

    private void calculateDotPositions(int w, int h) {
        // Leave some padding
        int padding = Math.min(w, h) / 6;
        int usableW = w - (padding * 2);
        int usableH = h - (padding * 2);

        float cellW = usableW / 2f;
        float cellH = usableH / 2f;

        int index = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                dotPositions[index][0] = padding + (col * cellW);
                dotPositions[index][1] = padding + (row * cellH);
                index++;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw Lines
        if (currentPattern.size() > 0) {
            for (int i = 0; i < currentPattern.size() - 1; i++) {
                int startIdx = currentPattern.get(i);
                int endIdx = currentPattern.get(i + 1);
                canvas.drawLine(
                        dotPositions[startIdx][0], dotPositions[startIdx][1],
                        dotPositions[endIdx][0], dotPositions[endIdx][1],
                        linePaint
                );
            }

            // Draw line to current finger position
            if (isDrawing) {
                int lastIdx = currentPattern.get(currentPattern.size() - 1);
                canvas.drawLine(
                        dotPositions[lastIdx][0], dotPositions[lastIdx][1],
                        currentX, currentY,
                        linePaint
                );
            }
        }

        // Draw Dots
        for (int i = 0; i < 9; i++) {
            if (currentPattern.contains(i)) {
                dotPaint.setColor(Color.parseColor("#4488FF"));
            } else {
                dotPaint.setColor(Color.WHITE);
            }
            canvas.drawCircle(dotPositions[i][0], dotPositions[i][1], dotRadius, dotPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        currentX = event.getX();
        currentY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPattern.clear();
                isDrawing = true;
                checkIntersect(currentX, currentY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (isDrawing) {
                    checkIntersect(currentX, currentY);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDrawing = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void checkIntersect(float x, float y) {
        float hitRadius = dotRadius * 3; // Allow sloppy drawing
        for (int i = 0; i < 9; i++) {
            if (!currentPattern.contains(i)) {
                float dx = dotPositions[i][0] - x;
                float dy = dotPositions[i][1] - y;
                if (Math.sqrt(dx * dx + dy * dy) < hitRadius) {
                    currentPattern.add(i);
                    break;
                }
            }
        }
    }

    public void clearPattern() {
        currentPattern.clear();
        invalidate();
    }

    public String getCapturedPattern() {
        JSONArray arr = new JSONArray();
        for (Integer i : currentPattern) {
            arr.put(i);
        }
        return arr.toString();
    }
}
