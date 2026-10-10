package ls.iqooaugment.com.root;

import android.content.Context;
import ls.iqooaugment.com.core.ShellQuote;
import ls.iqooaugment.com.core.ScanPolicy;
import ls.iqooaugment.com.core.RootProtocol;
import java.util.Collection;
import java.util.Set;
import ls.iqooaugment.com.data.Catalog;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Worker-thread only. Shell input is the quoted APK path, operation, and validated numeric space identities. */
public final class RootClient {
    /** Device refresh never enumerates applications. */
    public Catalog inspect(Context context) throws Exception { return run(context, "inspect", null); }
    public Catalog scan(Context context, Collection<ScanPolicy.SpaceKey> selected) throws Exception {
        String scope = ScanPolicy.encodeSelection(selected);
        return run(context, "scan " + ShellQuote.of(scope), ScanPolicy.parseSelection(scope));
    }
    private Catalog run(Context context, String operation, Set<ScanPolicy.SpaceKey> selected) throws Exception {
        String command = "CLASSPATH=" + ShellQuote.of(context.getApplicationInfo().sourceDir)
            + " /system/bin/app_process /system/bin " + RootMain.class.getName() + " " + operation;
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicReference<Exception> readerError = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = stream.read(buffer)) >= 0) {
                    if (bytes.size() + n > RootProtocol.MAX_OUTPUT) throw new IllegalStateException("Root 输出超过安全上限");
                    bytes.write(buffer, 0, n);
                }
            } catch (Exception e) { readerError.set(e); process.destroy(); }
        }, "IQOO-root-output");
        reader.setDaemon(true); reader.start();
        try {
            if (!process.waitFor(90, TimeUnit.SECONDS)) throw new IllegalStateException("Root 授权或应用读取超时，请在管理器中检查权限");
            reader.join(3000);
            if (reader.isAlive()) throw new IllegalStateException("Root 输出未正常结束");
            if (readerError.get() != null) throw readerError.get();
            String output = bytes.toString(StandardCharsets.UTF_8.name());
            final Catalog result;
            try { result = new Catalog(RootProtocol.result(output)); }
            catch (IllegalArgumentException error) {
                throw new IllegalStateException("Root 读取结果无效，已丢弃。请确认已授权模块，且系统允许 app_process。", error);
            }
            if (process.exitValue() != 0) throw new IllegalStateException("Root 读取进程异常退出：" + process.exitValue());
            result.validateScope(selected);
            return result;
        } finally { process.destroy(); }
    }
}
