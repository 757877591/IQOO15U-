package ls.iqooaugment.com.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Native, resolution-independent ice ribbons. Decorative only; no image files or network. */
public final class IceBackdrop extends Drawable {
    private final boolean dark;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbon = new Path();
    public IceBackdrop(boolean dark) { this.dark = dark; }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds();float w=b.width(),h=b.height();if(w<=0||h<=0)return;
        canvas.save();canvas.translate(b.left,b.top);
        paint.setShader(new LinearGradient(0,0,w,h,new int[]{dark?0xff112b43:0xffdaf2ff,dark?0xff142a40:0xffd5f0ff,dark?0xff0b1827:0xfff4fbff},new float[]{0,.55f,1},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);
        paint.setShader(new RadialGradient(w*.7f,h*.19f,w*.95f,new int[]{dark?0xff16466b:0xffa4ddff,dark?0x0013263c:0x00d7f3ff},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);
        ribbon.reset();ribbon.moveTo(w*1.14f,h*.005f);ribbon.cubicTo(w*.47f,h*.06f,w*.27f,h*.13f,w*.34f,h*.28f);ribbon.cubicTo(w*.36f,h*.40f,w*.69f,h*.42f,w*1.15f,h*.48f);ribbon.lineTo(w*1.15f,h*.65f);ribbon.cubicTo(w*.50f,h*.49f,w*.1f,h*.48f,w*.19f,h*.30f);ribbon.cubicTo(w*.26f,h*.14f,w*.64f,h*.07f,w*1.14f,h*.005f);ribbon.close();
        paint.setShader(new LinearGradient(0,h*.09f,w,h*.53f,new int[]{dark?0x242e739e:0x4de8faff,dark?0x405080a5:0xaaffffff,dark?0x09355370:0x18ffffff},new float[]{0,.57f,1},Shader.TileMode.CLAMP));canvas.drawPath(ribbon,paint);
        ribbon.reset();ribbon.moveTo(-w*.1f,h*.80f);ribbon.cubicTo(w*.28f,h*.55f,w*.71f,h*.51f,w*1.08f,h*.43f);ribbon.lineTo(w*1.08f,h*.7f);ribbon.cubicTo(w*.73f,h*.67f,w*.38f,h*.85f,w*.07f,h);ribbon.lineTo(-w*.1f,h);ribbon.close();
        paint.setShader(new LinearGradient(w*.9f,h*.49f,w*.12f,h,new int[]{dark?0x3557809d:0x85ffffff,dark?0x0615283e:0x08ffffff},null,Shader.TileMode.CLAMP));canvas.drawPath(ribbon,paint);
        paint.setShader(null);canvas.restore();
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter colorFilter) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
