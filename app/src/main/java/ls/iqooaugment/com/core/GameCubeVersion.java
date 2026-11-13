package ls.iqooaugment.com.core;

/** Exact, audited OEM symbol sets. A similar ver */
public enum GameCubeVersion {
    V14("14.0.16.00_de", 1401600L),
    V15("15.0.07.01_de", 1500701L),
    V15_NEW("15.0.07.02_de", 1500702L); // 新增精确匹配

    public static final String SUPPORTED = "14.0..."; // 保持你原值不变
    public final String versionName;
    public final long versionCode;
    public final String capabilityInstance, shoulderHardware, presenter, viewManager, gameUtils;
    public final String specialGame, reviewCategory, assistantManager, foregroundPackage, itemType;
    public final String driverCategory, sdkCategory, inputCategory, settingsPresenter, settingsPackage;
    public final String settingsInit, settingsScheme, preferencesParent, preferencesInit, guideInit;
    public final String switchBuilder, switchHelper, interpolation, interpolationCategory;

    GameCubeVersion(String versionName, long versionCode) {
        // 剥掉 _de 等构建后缀，存纯净版本名
        String raw = versionName == null ? "" : versionName;
        int us = raw.indexOf('_');
        this.versionName = us > 0 ? raw.substring(0, us) : raw;
        this.versionCode = versionCode;

        // 改造：15.0.x 系列（1500000~1599999）均走 v2 接口（含 1500701/1500702）
        boolean newer = (versionCode >= 1500000L && versionCode <= 1599999L);

        capabilityInstance = newer ? "q" : "p";
        shoulderHardware = newer ? "e1" : "00";
        presenter = newer ? "dj.a" : "be.a";
        viewManager = newer ? "ej.i0" : "ce.i0";
        gameUtils = newer ? "com.vivo.gameassistant.utils..." : "..."; // 保持你原完整字符串
        specialGame = newer ? "m1" : "c1";
        reviewCategory = newer ? "e1" : "V0";
        assistantManager = newer ? "com.vivo.gameassistant..." : "..."; // 保持原完整
        foregroundPackage = newer ? "U0" : "00";
        itemType = newer ? "o" : "n";
        driverCategory = newer ? "l" : "m";
        sdkCategory = newer ? "w" : "u";
        inputCategory = newer ? "g" : "h";
        settingsPresenter = newer ? "l0" : "T";
        settingsPackage = newer ? "j0" : "R";
        settingsInit = newer ? "g1" : "X0";
        settingsScheme = newer ? "b1" : "c1";
        preferencesParent = newer ? "f0" : "N";
        preferencesInit = newer ? "B0" : "x0";
        guideInit = newer ? "E0" : "A0";
        switchBuilder = newer ? "fh.b" : "mc.b";
        switchHelper = newer ? "com.vivo.gameassistant.utils..." : "..."; // 保持原完整
        interpolation = newer ? "of.h" : "nb.h";
        interpolationCategory = newer ? "m" : "l";
    }

    public static GameCubeVersion find(String name, long code) {
        if (name == null) return null;
        // 剥后缀：15.0.07.02_de -> 15.0.07.02
        int us = name.indexOf('_');
        String normalized = us > 0 ? name.substring(0, us) : name;

        for (GameCubeVersion version : values()) {
            if (version.versionCode == code && version.versionName.equals(normalized)) {
                return version;
            }
        }
        // 大版本兜底：15.0.x + code 在区间内，复用 V15_NEW（v2接口）
        if (normalized.startsWith("15.0.") && code >= 1500000L && code <= 1599999L) {
            return V15_NEW;
        }
        return null; // 未知版本不启用（符合原约束）
    }
}
