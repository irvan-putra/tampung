import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

class RunTests {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        Path classes = root.resolve("build/classes"); Files.createDirectories(classes);
        int result = ToolProvider.getSystemJavaCompiler().run(null, System.out, System.err,
            "--release", "8", "-encoding", "UTF-8", "-d", classes.toString(), root.resolve("core/SnapshotCodec.java").toString(),
            root.resolve("core/BackupEngine.java").toString(), root.resolve("tests/CoreTest.java").toString());
        if (result != 0) throw new AssertionError("Compilation failed");
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            loader.loadClass("CoreTest").getMethod("main", String[].class).invoke(null, (Object)new String[0]);
        }
    }
}
