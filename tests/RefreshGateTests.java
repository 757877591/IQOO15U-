import ls.iqooaugment.com.core.RefreshGate;

/** Event/retry fencing only; this does not emulate Android Binder delivery. */
public final class RefreshGateTests {
    static int checks;
    static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        RefreshGate.Epoch epoch = new RefreshGate.Epoch();
        Object oldResponse = epoch.capture();
        check(epoch.matches(oldResponse), "current config response initially applicable");
        epoch.invalidate();
        check(!epoch.matches(oldResponse), "configuration/readiness/death event fences in-flight response");
        Object newResponse = epoch.capture();
        check(epoch.matches(newResponse), "new response can establish readiness");
        epoch.invalidate();
        check(!epoch.matches(newResponse) && !epoch.matches(oldResponse), "old response never current again");
        RefreshGate gate = new RefreshGate();
        check(gate.begin() == null, "idle has no work");
        check(gate.request(), "real event starts work");
        check(!gate.request(), "events coalesce");
        Object series = gate.begin();
        check(gate.complete(series, false) < 0, "success goes idle without polling");
        check(gate.complete(series, true) < 0, "finished work cannot restart itself");
        check(gate.request(), "next real event starts work"); series = gate.begin();
        check(!gate.request(), "event during call queues no duplicate");
        check(gate.complete(series, false) == 0, "racing event survives successful call");
        check(gate.begin() == series, "coalesced event belongs to current series");
        check(gate.complete(series, false) < 0, "racing event consumed once");
        check(gate.request(), "failure series start"); series = gate.begin();
        for (long expected : new long[]{250, 1000, 4000}) {
            check(gate.complete(series, true) == expected, "bounded retry");
            check(gate.begin() == series, "retry belongs to same event");
        }
        check(gate.complete(series, true) < 0, "exhaustion goes idle indefinitely");
        check(gate.begin() == null, "no timer after retry exhaustion");
        check(gate.request(), "lifecycle event can retry after exhaustion"); series = gate.begin();
        check(gate.complete(series, true) == 250, "new event resets retry budget");
        gate.begin(); gate.request();
        check(gate.complete(series, true) == 0, "new event during failed request preserved");
        gate.begin(); check(gate.complete(series, false) < 0, "recovery stops retry timer");
        check(gate.request(), "start read before provider death"); Object dying = gate.begin();
        gate.cancel();
        check(gate.complete(dying, true) < 0, "death during IPC cannot reconnect through failure retry");
        check(gate.begin() == null, "death leaves no work scheduled");
        check(gate.request(), "actual lifecycle can reconnect after death"); Object replacement = gate.begin();
        check(gate.complete(dying, true) < 0, "late old completion cannot consume new lifecycle event");
        check(gate.complete(replacement, false) < 0, "new lifecycle completion remains valid");
        for (int i = 0; i < 1000; i++) {
            gate.request(); series = gate.begin(); gate.request();
            check(gate.complete(series, false) == 0, "deterministic completion race");
            gate.begin(); check(gate.complete(series, false) < 0, "no perpetual queue");
            gate.request(); dying = gate.begin(); gate.cancel(); gate.request(); replacement = gate.begin();
            check(gate.complete(dying, true) < 0, "old failure fenced after cancellation");
            check(gate.complete(replacement, false) < 0, "new lifecycle not lost");
        }
        System.out.println("PASS: " + checks + " event-only bridge/cancellation policy assertions");
    }
}
