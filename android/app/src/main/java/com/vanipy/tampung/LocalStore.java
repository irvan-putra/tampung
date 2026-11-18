package com.vanipy.tampung;

import android.util.AtomicFile;
import com.vanipy.tampung.core.*;
import java.io.*;
import java.util.*;

final class LocalStore implements BackupEngine.Local {
    private final AtomicFile file;
    LocalStore(File dir) { file = new AtomicFile(new File(dir, "dummy-notes.bin")); }
    public List<SnapshotCodec.Note> read() throws Exception {
        if (!file.getBaseFile().exists()) return Collections.emptyList();
        try (InputStream in = file.openRead()) { return SnapshotCodec.decode(readBounded(in, SnapshotCodec.MAX_BYTES)); }
    }
    public void replaceAtomically(List<SnapshotCodec.Note> notes) throws Exception {
        byte[] bytes = SnapshotCodec.encode(notes);
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
    }
    static byte[] readBounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > limit) throw new IOException("Download exceeds the allowed size");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
    static List<SnapshotCodec.Note> fixtures() {
        return Arrays.asList(new SnapshotCodec.Note("dummy-1", "Synthetic note: green box"), new SnapshotCodec.Note("dummy-2", "Uji cadangan. Dummy data only."));
    }
}
