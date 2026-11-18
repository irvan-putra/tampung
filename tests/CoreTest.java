import com.vanipy.tampung.core.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class CoreTest {
    static int passed;
    interface Checked { void run() throws Exception; }
    static void test(String name, Checked body) throws Exception { body.run(); passed++; System.out.println("PASS " + name); }
    static void check(boolean result) { if (!result) throw new AssertionError(); }
    static void fails(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    static List<SnapshotCodec.Note> sample() {
        return Arrays.asList(new SnapshotCodec.Note("dummy-1", "Synthetic note only"), new SnapshotCodec.Note("dummy-2", "Uji cadangan 📦\nbaris dua"));
    }
    static class Fake implements BackupEngine.Remote, BackupEngine.Local {
        List<SnapshotCodec.Note> local = sample(); Map<String, byte[]> remote = new HashMap<>();
        boolean offline, corrupt, deny, localFailure;
        int writes;
        public String createSnapshot(byte[] bytes) throws Exception {
            if (offline || deny) throw new IOException("Unavailable or consent revoked");
            String id = "fake-" + (remote.size() + 1); remote.put(id, bytes.clone()); return id;
        }
        public byte[] download(String id) throws Exception {
            if (offline || deny) throw new IOException("Unavailable or consent revoked");
            byte[] result = remote.get(id); if (result == null) throw new IOException("Not found");
            result = result.clone(); if (corrupt) result[result.length-1] ^= 1; return result;
        }
        public List<SnapshotCodec.Note> read() { return local; }
        public void replaceAtomically(List<SnapshotCodec.Note> notes) throws Exception {
            if (localFailure) throw new IOException("Storage write failed before commit");
            local = new ArrayList<>(notes); writes++;
        }
        BackupEngine engine() { return new BackupEngine(this, this); }
    }
    public static void main(String[] args) throws Exception {
        test("UTF-8 snapshot round trip", () -> check(sample().equals(SnapshotCodec.decode(SnapshotCodec.encode(sample())))));
        test("empty snapshot", () -> check(SnapshotCodec.decode(SnapshotCodec.encode(Collections.emptyList())).isEmpty()));
        test("corrupt bytes", () -> { byte[] b = SnapshotCodec.encode(sample()); b[16] ^= 1; fails(() -> SnapshotCodec.decode(b)); });
        test("truncated bytes", () -> fails(() -> SnapshotCodec.decode(new byte[20])));
        test("oversized input", () -> fails(() -> SnapshotCodec.decode(new byte[65537])));
        test("duplicate IDs", () -> fails(() -> SnapshotCodec.encode(Arrays.asList(new SnapshotCodec.Note("same", "one"),new SnapshotCodec.Note("same", "two")))));
        test("too many notes", () -> { List<SnapshotCodec.Note> ns = new ArrayList<>(); for(int i=0;i<101;i++) ns.add(new SnapshotCodec.Note("n"+i,"x")); fails(() -> SnapshotCodec.encode(ns)); });
        test("unsupported schema even with valid checksum", () -> { byte[] b=SnapshotCodec.encode(sample()); b[7]=2; byte[] digest=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(b,b.length-32)); System.arraycopy(digest,0,b,b.length-32,32); fails(() -> SnapshotCodec.decode(b)); });
        test("fake backup clear restore all fields", () -> { Fake f=new Fake(); String id=f.engine().backup(); f.local=Collections.emptyList(); f.engine().restore(id); check(f.local.equals(sample())); check(f.writes==1); });
        test("retain previous fake remote snapshots", () -> { Fake f=new Fake(); String one=f.engine().backup(); String two=f.engine().backup(); check(!one.equals(two)); check(f.remote.size()==2); });
        test("offline backup leaves local intact", () -> { Fake f=new Fake(); f.offline=true; fails(() -> f.engine().backup()); check(f.local.equals(sample())); check(f.remote.isEmpty()); });
        test("revoked consent restore leaves local intact", () -> { Fake f=new Fake(); String id=f.engine().backup(); f.deny=true; fails(() -> f.engine().restore(id)); check(f.writes==0); check(f.local.equals(sample())); });
        test("corrupt restore leaves local intact", () -> { Fake f=new Fake(); String id=f.engine().backup(); f.corrupt=true; fails(() -> f.engine().restore(id)); check(f.writes==0); });
        test("remote verification catches corrupt download", () -> { Fake f=new Fake(); f.corrupt=true; fails(() -> f.engine().backup()); check(f.local.equals(sample())); });
        test("failed local transaction preserves state", () -> { Fake f=new Fake(); String id=f.engine().backup(); f.local=Collections.emptyList(); f.localFailure=true; fails(() -> f.engine().restore(id)); check(f.local.isEmpty()); check(f.writes==0); });
        test("missing remote snapshot leaves local intact", () -> { Fake f=new Fake(); fails(() -> f.engine().restore("missing")); check(f.writes==0); });
        System.out.println(passed + " tests passed. Fake transport only. No Android or Google account used.");
    }
}
