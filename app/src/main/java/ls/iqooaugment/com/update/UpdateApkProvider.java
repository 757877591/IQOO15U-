package ls.iqooaugment.com.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Grants access only to one verified APK through an unguessable URI; never a directory. */
public final class UpdateApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        if (getContext() == null) throw new FileNotFoundException();
        SharedPreferences prefs = getContext().getSharedPreferences(UpdateManager.PREFS, 0);
        String token = prefs.getString("token", ""), hash = prefs.getString("readyHash", "");
        List<String> segments = uri.getPathSegments();
        if (!"content".equals(uri.getScheme()) || !getContext().getPackageName().concat(".updates").equals(uri.getAuthority())
                || uri.getQuery() != null || uri.getFragment() != null || token.isEmpty() || !hash.matches("[a-f0-9]{64}")
                || segments.size() != 2 || !token.equals(segments.get(0)) || !"update.apk".equals(segments.get(1)))
            throw new FileNotFoundException("Unknown update APK");
        File file = new File(new File(getContext().getFilesDir(), "updates"), hash + ".apk");
        if (!file.isFile()) throw new FileNotFoundException("Update APK unavailable");
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { try { file(uri); return "application/vnd.android.package-archive"; } catch (FileNotFoundException e) { return null; } }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = file(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns); Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "IQOOShoulderExtension-update.apk";
                else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            }
            cursor.addRow(values); return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new SecurityException("Read only"); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new SecurityException("Read only"); }
}
