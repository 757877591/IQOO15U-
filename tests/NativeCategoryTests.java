import ls.iqooaugment.com.core.AppKey;
import ls.iqooaugment.com.core.NativeCategoryScope;

/** Real thread-local scope tests; not a simulation of native Android input. */
public final class NativeCategoryTests {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        NativeCategoryScope scope = new NativeCategoryScope();
        AppKey key = new AppKey(0, 7, "com.example.game");
        Object firstConfig = new Object(), replacement = new Object();
        check(!scope.allows(key.packageName, firstConfig), "outside scope remains native");
        NativeCategoryScope.Frame allowed = scope.enter(key, firstConfig);
        check(scope.allows(key.packageName, firstConfig), "selected exact scope");
        check(!scope.allows("com.example.other", firstConfig), "different package remains native");
        check(!scope.allows(key.packageName, replacement), "replacement config cannot borrow scope");
        NativeCategoryScope.Frame review = scope.enter(null, null);
        check(!scope.allows(key.packageName, firstConfig), "nested GAME_REVIEW caller masks generic classification");
        AppKey clone = new AppKey(999, 8, key.packageName);
        NativeCategoryScope.Frame independentlyChecked = scope.enter(clone, replacement);
        check(scope.current().key.equals(clone), "separately resolved nested instance");
        check(!scope.allows(key.packageName, firstConfig), "old evidence cannot authorize new frame");
        scope.leave(independentlyChecked);
        check(!scope.allows(key.packageName, firstConfig), "native barrier restored");
        scope.leave(review);
        check(scope.allows(key.packageName, firstConfig), "outer scope restored");
        final boolean[] other = {true};
        Thread thread = new Thread(() -> other[0] = scope.allows(key.packageName, firstConfig));
        thread.start(); thread.join();
        check(!other[0], "category evidence never crosses thread");
        NativeCategoryScope.Frame input = scope.enter(key, firstConfig, true);
        check(scope.cacheOnly(), "input category scope is cache only");
        NativeCategoryScope.Frame inputBarrier = scope.enter(null, null);
        check(scope.cacheOnly(), "native barrier cannot turn input into a new probe");
        NativeCategoryScope.Frame nestedMenu = scope.enter(key, firstConfig, false);
        check(scope.cacheOnly(), "nested menu/capability retains input cache only");
        scope.leave(nestedMenu); scope.leave(inputBarrier); scope.leave(input);
        check(!scope.cacheOnly(), "input cache-only mode does not leak after return");
        scope.leave(allowed);
        check(scope.current() == null, "permission removed on native return");
        scope.leave(null);
        check(scope.current() == null, "failed before-entry leaves no scope");
        for (int i=0;i<8;i++) {
            NativeCategoryScope.Frame frame = scope.enter(key, firstConfig);
            try { throw new IllegalStateException("simulated OEM exception"); }
            catch (IllegalStateException expected) { }
            finally { scope.leave(frame); }
            check(scope.current() == null, "exception exit restores scope");
        }
        System.out.println("PASS: " + checks + " native-category isolation assertions");
    }
}
