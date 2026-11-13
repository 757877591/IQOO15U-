package ls.iqooaugment.com.core;

/** Exact, audited OEM symbol sets. A similar version name is not a compatible version. */
public enum GameCubeVersion {
    V14("14.0.16.00_de", 1401600L),
    V15("15.0.07.01_de", 1500701L),
    V15_NEW("15.0.07.02_de", 1500702L);

    public static final String SUPPORTED = "14.0.16.00_de / 15.0.07.01_de / 15.0.07.02_de";

    public final String versionName;
    public final long versionCode;
    public final String capabilityInstance, shoulderHardware;
    public final String presenter, viewManager, gameUtils, specialGame, reviewCategory;
    public final String assistantManager, foregroundPackage, itemType;
    public final String driverCategory, sdkCategory, inputCategory;
    public final String settingsPresenter, settingsPackage, settingsInit, settingsScheme;
    public final String preferencesParent, preferencesInit, guideInit;
    public final String switchBuilder, switchHelper, interpolation, interpolationCategory;

    GameCubeVersion(String versionName, long versionCode) {
        this.versionName = versionName;
        this.versionCode = versionCode;

        // 15.0.x 系列（1500000~1599999）均走新版 v2 接口
        boolean newer = (versionCode >= 1500000L && versionCode <= 1599999L);

        capabilityInstance = newer ? "q" : "p";
        shoulderHardware = newer ? "e1" : "O0";
        presenter = newer ? "dj.a" : "be.a";
        viewManager = newer ? "ej.i0" : "ce.i0";
        gameUtils = newer ? "com.vivo.gameassistant.utils.i0" : "com.vivo.gameassistant.utils.e0";
        specialGame = newer ? "m1" : "c1";
        reviewCategory = newer ? "e1" : "V0";
        assistantManager = newer ? "com.vivo.gameassistant.r" : "com.vivo.gameassistant.p";
        foregroundPackage = newer ? "U0" : "O0";
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
        switchHelper = newer ? "com.vivo.gameassistant.utils.x" : "com.vivo.gameassistant.utils.u";
        interpolation = newer ? "of.h" : "nb.h";
        interpolationCategory = newer ? "m" : "l";
    }

    public static GameCubeVersion find(String name, long code) {
        if (name == null) return null;

        // 剥掉 _de 等构建后缀，统一匹配
        String normalized = name;
        int us = name.indexOf('_');
        if (us > 0) {
            normalized = name.substring(0, us);
        }

        // 精确匹配（用归一化后的名字）
        for (GameCubeVersion version : values()) {
            if (version.versionCode == code) {
                // 匹配原始名或归一化名
                if (version.versionName.equals(name) || version.versionName.equals(normalized)) {
                    return version;
                }
            }
        }

        // 大版本兜底：15.0.x + code 在区间内，复用 V15_NEW 的 v2 接口
        if (normalized.startsWith("15.0.") && code >= 1500000L && code <= 1599999L) {
            return V15_NEW;
        }

        return null; // 未知版本不启用
    }
}
