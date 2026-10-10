package ls.iqooaugment.com.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.LinearLayout;

/** Shared translucent glass and fine double edge; intentionally not a backdrop blur. */
public final class LiquidGlassLayout extends LinearLayout {
    public LiquidGlassLayout(Context context, AppearanceController appearance) {
        super(context);setOrientation(VERTICAL);
        AppearanceController.Palette p=appearance.palette();float d=getResources().getDisplayMetrics().density;
        GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{p.surface,p.surfaceEnd});
        glass.setCornerRadius(4*d);glass.setStroke(Math.max(1,Math.round(d)),p.border);
        GradientDrawable edge=new GradientDrawable();edge.setColor(0);edge.setCornerRadius(3*d);edge.setStroke(Math.max(1,Math.round(d)),p.highlight);
        LayerDrawable layers=new LayerDrawable(new android.graphics.drawable.Drawable[]{glass,edge});
        int inset=Math.max(1,Math.round(d));layers.setLayerInset(1,inset,inset,inset+1,inset+1);setBackground(layers);
    }
}
