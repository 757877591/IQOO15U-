package ls.iqooaugment.com.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import ls.iqooaugment.com.core.UiMetrics;

public final class UiKit {
    public static final int DETAIL_CONTENT_GAP_DP=UiMetrics.DETAIL_CONTENT_GAP_DP;
    public enum TextRole { HOME_TITLE, TITLE, BODY, SECONDARY, CAPTION, PATH }
    public final Context context;
    public final AppearanceController appearance;
    public final AppearanceController.Palette palette;
    public UiKit(Context context,AppearanceController appearance){this.context=context;this.appearance=appearance;palette=appearance.palette();}
    public int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    public LinearLayout column(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public LinearLayout row(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public TextView text(String value,TextRole role){
        TextView v=new TextView(context);v.setText(value);
        float size=role==TextRole.HOME_TITLE?24:role==TextRole.TITLE?14:role==TextRole.BODY?14:role==TextRole.SECONDARY?12:role==TextRole.PATH?UiMetrics.PATH_TEXT_SP:11.5f;
        v.setTextSize(size);v.setTextColor(role==TextRole.SECONDARY||role==TextRole.CAPTION||role==TextRole.PATH?palette.secondary:palette.text);
        v.setIncludeFontPadding(false);v.setLineSpacing(dp(2),1f);
        if(role==TextRole.HOME_TITLE||role==TextRole.TITLE)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return v;
    }
    public LiquidGlassLayout card(){LiquidGlassLayout v=new LiquidGlassLayout(context,appearance);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(UiMetrics.CARD_GAP_DP);v.setLayoutParams(lp);return v;}
    public LiquidGlassLayout preferenceCard(){LiquidGlassLayout v=card();v.setPadding(dp(12),dp(2),dp(12),dp(2));return v;}
    public TextView addDetailSectionTitle(LinearLayout content,String title,boolean first){
        TextView text=text(title,TextRole.SECONDARY);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=first?0:dp(16);lp.bottomMargin=dp(UiMetrics.SECTION_TO_CARD_DP);content.addView(text,lp);return text;
    }
    public LinearLayout detailChrome(LinearLayout host,String parent,String current,Runnable back){
        LinearLayout path=row();path.setPadding(dp(16),0,dp(16),0);
        TextView up=text(parent,TextRole.PATH);up.setGravity(Gravity.CENTER_VERTICAL);up.setMinHeight(dp(48));up.setTextColor(palette.accent);up.setOnClickListener(v->back.run());up.setBackground(touch());path.addView(up,new LinearLayout.LayoutParams(-2,-2));
        GlassIcon arrow=new GlassIcon(context,GlassIcon.Kind.RIGHT,palette.secondary);path.addView(arrow,new LinearLayout.LayoutParams(dp(22),dp(30)));
        TextView place=text(current,TextRole.PATH);place.setGravity(Gravity.CENTER_VERTICAL);place.setMinHeight(dp(48));path.addView(place,new LinearLayout.LayoutParams(0,-2,1));host.addView(path);
        LinearLayout content=column();content.setPadding(dp(16),dp(DETAIL_CONTENT_GAP_DP),dp(16),0);host.addView(content,new LinearLayout.LayoutParams(-1,0,1));return content;
    }
    public LinearLayout preferenceRow(String title,String detail,Runnable click){
        LinearLayout row=row();row.setMinimumHeight(dp(48));row.setPadding(0,dp(10),0,dp(10));LinearLayout labels=column();labels.addView(text(title,TextRole.TITLE));
        if(detail!=null&&!detail.isEmpty()){TextView sub=text(detail,TextRole.SECONDARY);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);labels.addView(sub,lp);}
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(click!=null){GlassIcon arrow=new GlassIcon(context,GlassIcon.Kind.RIGHT,palette.secondary);row.addView(arrow,new LinearLayout.LayoutParams(dp(25),dp(44)));row.setBackground(touch());row.setOnClickListener(v->click.run());}return row;
    }
    public LinearLayout featureRow(String title,String detail,IceSwitch control){LinearLayout row=preferenceRow(title,detail,null);row.setMinimumHeight(dp(UiMetrics.FEATURE_MIN_DP));row.addView(control,new LinearLayout.LayoutParams(dp(52),dp(48)));return row;}
    public View leadingIcon(GlassIcon.Kind kind){GlassIcon icon=new GlassIcon(context,kind,palette.accent);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(32),dp(40));lp.rightMargin=dp(10);icon.setLayoutParams(lp);return icon;}
    public LinearLayout actionRow(GlassIcon.Kind icon,String title,String detail,Runnable click){
        LinearLayout row=preferenceRow(title,detail,null);row.addView(leadingIcon(icon),0);row.setBackground(touch());row.setOnClickListener(v->click.run());row.setFocusable(true);return row;
    }
    public LinearLayout valueRow(String title,String value){
        LinearLayout row=row();row.setMinimumHeight(dp(48));row.setPadding(0,dp(10),0,dp(10));TextView label=text(title,TextRole.BODY);row.addView(label,new LinearLayout.LayoutParams(0,-2,.9f));TextView info=text(value,TextRole.SECONDARY);info.setGravity(Gravity.END);info.setPadding(dp(12),0,0,0);row.addView(info,new LinearLayout.LayoutParams(0,-2,1.2f));return row;
    }
    public void divider(LinearLayout host){View line=new View(context);line.setBackgroundColor(palette.divider);host.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    public Button action(String value,Runnable click){return button(value,click,false);}
    public Button primaryAction(String value,Runnable click){return button(value,click,true);}
    private Button button(String value,Runnable click,boolean primary){
        Button b=new Button(context);b.setAllCaps(false);b.setText(value);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(16),dp(8),dp(16),dp(8));b.setStateListAnimator(null);b.setElevation(0);b.setIncludeFontPadding(false);
        int enabled=primary?(palette.dark?0xff10243b:0xffffffff):palette.accent;
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{palette.secondary,enabled}));
        StateListDrawable background=new StateListDrawable();background.addState(new int[]{-android.R.attr.state_enabled},shapeRipple(palette.inactive,8));background.addState(new int[]{},shapeRipple(primary?palette.accent:palette.soft,8));b.setBackground(background);b.setOnClickListener(v->click.run());return b;
    }
    public View iconAction(GlassIcon.Kind icon,String description,Runnable action){GlassIcon v=new GlassIcon(context,icon,palette.accent);v.setContentDescription(description);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);v.setFocusable(true);v.setBackground(touch());v.setOnClickListener(w->action.run());v.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return v;}
    public EditText input(String hint){EditText e=new EditText(context);e.setSingleLine(true);e.setHint(hint);e.setTextSize(14);e.setTextColor(palette.text);e.setHintTextColor(palette.secondary);e.setMinHeight(dp(48));e.setPadding(dp(12),dp(8),dp(12),dp(8));e.setBackground(shapeRipple(palette.surface,4));return e;}
    public AlertDialog.Builder dialog(){
        ContextThemeWrapper themed=new ContextThemeWrapper(context,palette.dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);
        return new AlertDialog.Builder(themed){@Override public AlertDialog create(){AlertDialog dialog=super.create();dialog.setOnShowListener(ignored->{
            GradientDrawable background=new GradientDrawable();background.setColor(palette.dark?0xff183149:0xffedf8ff);background.setCornerRadius(dp(16));background.setStroke(dp(1),palette.highlight);if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(background);int available=context.getResources().getDisplayMetrics().widthPixels-dp(32);dialog.getWindow().setLayout(Math.min(available,dp(440)),-2);}
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button button=dialog.getButton(which);if(button!=null){button.setTextColor(enabledColors(palette.accent));button.setTextSize(14);button.setAllCaps(false);button.setMinHeight(dp(48));button.setStateListAnimator(null);button.setBackground(touch());}}
            TextView message=dialog.findViewById(android.R.id.message);if(message!=null){message.setTextColor(palette.text);message.setTextSize(14);message.setLineSpacing(dp(3),1f);}
            int titleId=context.getResources().getIdentifier("alertTitle","id","android");TextView title=dialog.findViewById(titleId);if(title!=null){title.setTextColor(palette.text);title.setTextSize(18);}
        });return dialog;}};
    }
    public ColorStateList enabledColors(int enabled){return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{palette.secondary,enabled});}
    public RippleDrawable shapeRipple(int color,float radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(radius));return new RippleDrawable(ColorStateList.valueOf(palette.divider),shape,mask);}
    public RippleDrawable touch(){return shapeRipple(0,6);}
    public RippleDrawable ripple(){return shapeRipple(palette.soft,8);}
}
