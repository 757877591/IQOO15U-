import ls.iqooaugment.com.core.GameCubeVersion;

public final class GameCubeVersionTests {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError(assertions); }
    public static void main(String[] args) {
        check(GameCubeVersion.find("14.0.16.00_de", 1401600L) == GameCubeVersion.V14);
        check(GameCubeVersion.find("15.0.07.01_de", 1500701L) == GameCubeVersion.V15);
        check(GameCubeVersion.find("14.0.16.00_de", 1500701L) == null);
        check(GameCubeVersion.find("15.0.07.01_de", 1401600L) == null);
        check(GameCubeVersion.find("15.0.07.01", 1500701L) == null);
        check(GameCubeVersion.find("15.0.07.02_de", 1500702L) == null);
        check(GameCubeVersion.find(null, 1500701L) == null);
        check(GameCubeVersion.find("15.0.07.01_de", -1) == null);
        check(GameCubeVersion.find("15.0.07.01_de", 4296467997L) == null);
        // O0 still exists in V15 but now means eye care. Never reuse a matching old signature.
        check(GameCubeVersion.V14.shoulderHardware.equals("O0"));
        check(GameCubeVersion.V15.shoulderHardware.equals("e1"));
        check(GameCubeVersion.V14.presenter.equals("be.a"));
        check(GameCubeVersion.V15.presenter.equals("dj.a"));
        check(GameCubeVersion.V14.settingsPresenter.equals("T"));
        check(GameCubeVersion.V15.settingsPresenter.equals("l0"));
        System.out.println("PASS: " + assertions + " exact OEM version routing assertions.");
    }
}
