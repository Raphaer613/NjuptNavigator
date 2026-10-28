package com.example.njuptcampusnavigator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small code-drawn icons: no external font, image asset or dependency. */
public final class NavigationIconView extends View {
    private final int icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NavigationIconView(Context context, int icon) {
        super(context);
        this.icon = icon;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setActive(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setActive(boolean active) {
        paint.setColor(Color.parseColor(active ? "#0068B7" : "#66788A"));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 24f, getHeight() / 24f);
        switch (icon) {
            case 0: // services
                canvas.drawRoundRect(3, 3, 10, 10, 1.5f, 1.5f, paint);
                canvas.drawRoundRect(14, 3, 21, 10, 1.5f, 1.5f, paint);
                canvas.drawRoundRect(3, 14, 10, 21, 1.5f, 1.5f, paint);
                canvas.drawRoundRect(14, 14, 21, 21, 1.5f, 1.5f, paint);
                break;
            case 1: // search
                canvas.drawCircle(10, 10, 6.5f, paint);
                canvas.drawLine(15, 15, 21, 21, paint);
                break;
            case 2: // home
                Path house = new Path();
                house.moveTo(2, 11); house.lineTo(12, 3); house.lineTo(22, 11);
                house.moveTo(5, 10); house.lineTo(5, 21); house.lineTo(10, 21);
                house.lineTo(10, 15); house.lineTo(14, 15); house.lineTo(14, 21);
                house.lineTo(19, 21); house.lineTo(19, 10);
                canvas.drawPath(house, paint);
                break;
            case 3: // popular places
                Path pin = new Path();
                pin.moveTo(12, 22);
                pin.cubicTo(9, 18, 4, 13, 4, 9);
                pin.cubicTo(4, -1, 20, -1, 20, 9);
                pin.cubicTo(20, 13, 15, 18, 12, 22);
                canvas.drawPath(pin, paint);
                canvas.drawCircle(12, 9, 2.8f, paint);
                break;
            case 4: // share
                canvas.drawLine(7, 11, 17, 6, paint);
                canvas.drawLine(7, 13, 17, 18, paint);
                canvas.drawCircle(5, 12, 2.5f, paint);
                canvas.drawCircle(19, 5, 2.5f, paint);
                canvas.drawCircle(19, 19, 2.5f, paint);
                break;
            default:
                break;
        }
        canvas.restore();
    }
}
