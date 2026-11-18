import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

/** Manual operations only; no auth tokens or credentials belong in this layer. */
public final class BackupEngine {
    public interface Remote {
        String createSnapshot(byte[] bytes) throws Exception;
        byte[] download(String id) throws Exception;
    }
    public interface Local {
        List<SnapshotCodec.Note> read() throws Exception;
        // Implementation must replace the complete set in one DB/file transaction.
        void replaceAtomically(List<SnapshotCodec.Note> notes) throws Exception;
    }
    private final Remote remote;
    private final Local local;
    public BackupEngine(Remote remote, Local local) { this.remote = remote; this.local = local; }
    public String backup() throws Exception {
        byte[] encoded = SnapshotCodec.encode(local.read());
        String id = remote.createSnapshot(encoded);
        byte[] downloaded = remote.download(id);
        if (!MessageDigest.isEqual(encoded, downloaded)) throw new IOException("Remote verification failed");
        SnapshotCodec.decode(downloaded);
        // Never deletes or overwrites any prior remote snapshot.
        return id;
    }
    public void restore(String id) throws Exception {
        List<SnapshotCodec.Note> checked = SnapshotCodec.decode(remote.download(id));
        local.replaceAtomically(checked);
    }
}
