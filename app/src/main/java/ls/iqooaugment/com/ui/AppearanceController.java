package ls.iqooaugment.com.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** One system-following ice-blue palette. Legacy appearance preferences are no longer read. */
public final class AppearanceController {
    public static final class Palette {
        public final boolean dark;
        public final int background, surface, surfaceEnd, text, secondary, accent, soft, border, inactive, danger;
        public final int highlight, divider, warning, warningText;
        Palette(boolean dark) {
            this.dark = dark;
            background = c(dark ? "#0C1725" : "#E4F5FF");
            surface = c(dark ? "#B5263E54" : "#70FFFFFF");
            surfaceEnd = c(dark ? "#8523384E" : "#28E8F8FF");
            text = c(dark ? "#ECF6FF" : "#14283F");
            secondary = c(dark ? "#A3BDD2" : "#5E738A");
            accent = c(dark ? "#6BC5FF" : "#087CD8");
            soft = c(dark ? "#80405E78" : "#64B5E6FF");
            border = c(dark ? "#707397B3" : "#908EBFD9");
            highlight = c(dark ? "#509DC7E0" : "#EFFFFFFF");
            divider = c(dark ? "#304D718E" : "#287FA8BE");
            inactive = c(dark ? "#445B70" : "#ABC8D9");
            danger = c(dark ? "#FFB7AE" : "#AA482C");
            warning = c(dark ? "#73554D25" : "#A8F8E29B");
            warningText = c(dark ? "#F5D992" : "#825B00");
        }
        private static int c(String value) { return Color.parseColor(value); }
    }
    private final Context context;
    public AppearanceController(Context context) { this.context = context; }
    /** Fixed compact glass cards, matching the supplied reference. No appearance settings. */
    public boolean rounded() { return false; }
    public Palette palette() {
        return new Palette((context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES);
    }
}
