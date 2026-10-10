package ls.iqooaugment.com.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Uniform 24-unit stroked icons. Color is always supplied by the common palette. */
public final class GlassIcon extends View {
    public enum Kind { HOME, MODULE, INFO, ADD, BACK, RIGHT, DOWN, UP, SEARCH, REFRESH, COPY, APPS, CHECK, SHIELD, SCOPE, PULSE, REMOVE }
    private final Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;
    public GlassIcon(Context context, Kind kind, int color) { super(context); this.kind = kind; this.color = color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void color(int value) { color = value; invalidate(); }
    private void line(Canvas c, float a, float b, float x, float y) { c.drawLine(a, b, x, y, paint); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save();
        float side = Math.min(getWidth(), getHeight()) * .62f;
        c.translate((getWidth() - side) / 2, (getHeight() - side) / 2); c.scale(side / 24, side / 24);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case ADD: line(c, 12, 4, 12, 20); line(c, 4, 12, 20, 12); break;
            case BACK: line(c, 14, 5, 7, 12); line(c, 7, 12, 14, 19); break;
            case RIGHT: line(c, 9, 5, 16, 12); line(c, 16, 12, 9, 19); break;
            case DOWN: line(c, 5, 9, 12, 16); line(c, 12, 16, 19, 9); break;
            case UP: line(c, 5, 15, 12, 8); line(c, 12, 8, 19, 15); break;
            case SEARCH: c.drawCircle(10, 10, 6, paint); line(c, 15, 15, 21, 21); break;
            case INFO: c.drawCircle(12, 12, 9, paint); line(c, 12, 11, 12, 17); line(c, 12, 7, 12, 7); break;
            case COPY: c.drawRoundRect(8, 7, 20, 21, 2, 2, paint); line(c, 5, 17, 3, 17); line(c, 3, 17, 3, 3); line(c, 3, 3, 16, 3); break;
            case SHIELD:
                Path shield=new Path();shield.moveTo(12,2);shield.lineTo(21,6);shield.lineTo(20,14);shield.quadTo(18,19,12,22);shield.quadTo(6,19,4,14);shield.lineTo(3,6);shield.close();c.drawPath(shield,paint);break;
            case SCOPE: c.drawRoundRect(3,3,21,21,3,3,paint);line(c,8,12,11,15);line(c,11,15,17,8);break;
            case PULSE: line(c,2,12,7,12);line(c,7,12,10,5);line(c,10,5,14,19);line(c,14,19,17,12);line(c,17,12,22,12);break;
            case REMOVE: line(c,5,6,19,6);line(c,9,3,15,3);line(c,7,6,8,21);line(c,8,21,16,21);line(c,16,21,17,6);line(c,11,10,11,17);line(c,14,10,14,17);break;
            case CHECK: line(c, 4, 12, 10, 18); line(c, 10, 18, 21, 6); break;
            case REFRESH: c.drawArc(4, 4, 20, 20, 35, 290, false, paint); line(c, 20, 4, 20, 10); line(c, 14, 10, 20, 10); break;
            case HOME:
                Path home = new Path(); home.moveTo(3, 11); home.lineTo(12, 3); home.lineTo(21, 11);
                home.moveTo(6, 9); home.lineTo(6, 21); home.lineTo(18, 21); home.lineTo(18, 9); c.drawPath(home, paint);
                line(c, 10, 21, 10, 15); line(c, 10, 15, 14, 15); line(c, 14, 15, 14, 21); break;
            case MODULE: c.drawRoundRect(3, 6, 21, 19, 3, 3, paint); line(c, 5, 3, 9, 3); line(c, 15, 3, 19, 3); line(c, 7, 10, 7, 15); line(c, 4.5f, 12.5f, 9.5f, 12.5f); c.drawCircle(16.5f, 12.5f, 1.3f, paint); break;
            default: c.drawRoundRect(3, 3, 10, 10, 1.5f, 1.5f, paint); c.drawRoundRect(14, 3, 21, 10, 1.5f, 1.5f, paint); c.drawRoundRect(3, 14, 10, 21, 1.5f, 1.5f, paint); c.drawRoundRect(14, 14, 21, 21, 1.5f, 1.5f, paint);
        }
        c.restore();
    }
}
