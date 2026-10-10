import java.util.*;
import ls.iqooaugment.com.core.*;

/** Lifecycle data transitions only; these do not simulate Android View layout or Root. */
public final class UiStateTests {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UiStatePolicy.ConfigReads reads = new UiStatePolicy.ConfigReads();
        long beforeSave = reads.request();
        reads.beginSave();
        check(!reads.accepts(beforeSave), "old read cannot replace a save in progress");
        long notifiedDuringSave = reads.request();
        check(!reads.accepts(notifiedDuringSave), "notification cannot apply a precommit snapshot");
        reads.endSave();
        long afterSave = reads.request();
        check(!reads.accepts(beforeSave) && !reads.accepts(notifiedDuringSave), "late old callbacks cannot undo successful save");
        check(reads.accepts(afterSave), "fresh postcommit snapshot applies");
        long firstWindowChange = reads.request(), newerWindowChange = reads.request();
        check(!reads.accepts(firstWindowChange) && reads.accepts(newerWindowChange), "out of order reads preserve latest window change");

        String owner = new AppKey(0, 0, "com.example.player").encode();
        String clone = new AppKey(27, 104, "com.example.player").encode();
        String rebuiltClone = new AppKey(27, 105, "com.example.player").encode();
        Set<ScanPolicy.SpaceKey> original = Set.of(new ScanPolicy.SpaceKey(0, 0), new ScanPolicy.SpaceKey(27, 104));
        Set<ScanPolicy.SpaceKey> recovered = UiStatePolicy.recoveryScope(Set.of(owner), original);
        check(recovered.equals(original), "restore retains full explicitly consented scope");
        check(UiStatePolicy.recoveryScope(Set.of(clone), Set.of()).equals(Set.of(new ScanPolicy.SpaceKey(27, 104))), "legacy draft reconfirms exact saved space identity");
        boolean rejected = false;
        try { UiStatePolicy.recoveryScope(Set.of(rebuiltClone), original); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "rebuilt space cannot inherit old consent");
        Set<String> picked = new LinkedHashSet<>(Arrays.asList(owner, clone));
        check(UiStatePolicy.unavailableDraft(picked, Set.of(owner, rebuiltClone), Set.of()).equals(Set.of(clone)), "recheck never substitutes same package in another serial");
        check(picked.equals(Set.of(owner, clone)), "recheck does not mutate draft");
        check(UiStatePolicy.unavailableDraft(picked, Set.of(owner, clone), Set.of(owner)).equals(Set.of(owner)), "peer saved target needs explicit draft removal");
        check(UiStatePolicy.unavailableDraft(picked, Set.of(), Set.of()).equals(picked), "failed or empty availability retains all unresolved picks");
        System.out.println("PASS: " + checks + " UI recovery state checks (not Android interaction tests).");
    }
}
