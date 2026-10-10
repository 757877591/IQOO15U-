package ls.iqooaugment.com.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import ls.iqooaugment.com.core.UiMetrics;

/** 46x28dp track, 22dp thumb, >=48dp accessible hit target. The owner must confirm a successful change. */
public final class IceSwitch extends View implements Checkable {
    public interface Request { void set(boolean value); }
    private boolean checked;
    private final AppearanceController.Palette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Request request;
    private final float d;
    public IceSwitch(Context context, AppearanceController.Palette palette) {
        super(context); this.palette = palette; d = getResources().getDisplayMetrics().density;
        setFocusable(true); setClickable(true); setMinimumWidth((int)(52*d)); setMinimumHeight((int)(48*d));
    }
    public void onRequest(Request value) { request = value; }
    @Override public void setChecked(boolean value) { checked = value; invalidate(); }
    @Override public boolean isChecked() { return checked; }
    @Override public void toggle() { if (request != null && isEnabled()) request.set(!checked); }
    @Override public boolean performClick() { super.performClick(); toggle(); return true; }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = UiMetrics.SWITCH_TRACK_W_DP*d, h = UiMetrics.SWITCH_TRACK_H_DP*d;
        float x = (getWidth()-w)/2, y = (getHeight()-h)/2;
        paint.setColor(checked ? palette.accent : palette.inactive); paint.setAlpha(isEnabled()?255:100);
        c.drawRoundRect(x, y, x+w, y+h, h/2, h/2, paint);
        paint.setColor(palette.dark ? palette.text : 0xffffffff); paint.setAlpha(isEnabled()?255:160);
        float r=UiMetrics.SWITCH_THUMB_DP*d/2;
        c.drawCircle(checked ? x+w-3*d-r : x+3*d+r, y+h/2, r, paint);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Switch");
        info.setCheckable(true); info.setChecked(checked);
    }
}
