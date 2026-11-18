import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Synthetic-only snapshot codec. SHA-256 detects accidental corruption, not malicious edits. */
public final class SnapshotCodec {
    public static final int MAX_BYTES = 65536;
    public static final int MAX_NOTES = 100;
    private static final int MAGIC = 0x44535031;
    private static final int VERSION = 1;

    public static final class Note {
        public final String id;
        public final String text;
        public Note(String id, String text) { this.id = id; this.text = text; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Note)) return false;
            Note n = (Note) other;
            return id.equals(n.id) && text.equals(n.text);
        }
        @Override public int hashCode() { return Objects.hash(id, text); }
    }

    public static byte[] encode(List<Note> notes) throws Exception {
        validate(notes);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(notes.size());
        for (Note n : notes) { writeString(out, n.id); writeString(out, n.text); }
        out.flush();
        byte[] payload = bytes.toByteArray();
        if (payload.length + 32 > MAX_BYTES) throw new IOException("Snapshot exceeds size limit");
        bytes.write(MessageDigest.getInstance("SHA-256").digest(payload));
        return bytes.toByteArray();
    }

    public static List<Note> decode(byte[] snapshot) throws Exception {
        if (snapshot == null || snapshot.length < 44 || snapshot.length > MAX_BYTES)
            throw new IOException("Invalid snapshot size");
        byte[] payload = Arrays.copyOf(snapshot, snapshot.length - 32);
        byte[] checksum = Arrays.copyOfRange(snapshot, snapshot.length - 32, snapshot.length);
        if (!MessageDigest.isEqual(checksum, MessageDigest.getInstance("SHA-256").digest(payload)))
            throw new IOException("Snapshot checksum mismatch");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported snapshot format");
        int count = in.readInt();
        if (count < 0 || count > MAX_NOTES) throw new IOException("Invalid note count");
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < count; i++) notes.add(new Note(readString(in), readString(in)));
        if (in.available() != 0) throw new IOException("Unexpected trailing bytes");
        validate(notes);
        return Collections.unmodifiableList(notes);
    }

    private static void validate(List<Note> notes) throws IOException {
        if (notes == null || notes.size() > MAX_NOTES) throw new IOException("Invalid note count");
        Set<String> ids = new HashSet<>();
        for (Note n : notes) {
            if (n == null || n.id == null || n.text == null || n.id.isEmpty() || !ids.add(n.id))
                throw new IOException("Invalid or duplicate note ID");
            if (n.id.getBytes(StandardCharsets.UTF_8).length > 128 || n.text.getBytes(StandardCharsets.UTF_8).length > 8192)
                throw new IOException("Note exceeds size limit");
        }
    }
    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String readString(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 8192 || size > in.available()) throw new IOException("Invalid text length");
        byte[] bytes = new byte[size]; in.readFully(bytes);
        java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
}
