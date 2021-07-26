import java.io.PrintStream;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.Base64;


public class DynamicSlicingLogger{
    static final int SIZE = 1024;
    static final ArrayList<String> queue = new ArrayList<>(SIZE);
    static final PrintStream outStream = System.out;
    static {
        Runtime.getRuntime().addShutdownHook(new DynamicSlicingLoggerShutdown());
        Thread.setDefaultUncaughtExceptionHandler(new DynamicSlicingLoggerExceptionHandler());
    }

    // public static void println(String e) {
    //     synchronized (queue) {
    //         System.out.println("SLICING: ZLIB: "+e);
    //     }
    // }

    public static void println(String e) {
        synchronized (queue) {
            queue.add(e);
            if (queue.size() > SIZE-1) {
                flush();
            }
        }
    }
    
    public static void flush(String e) {
        synchronized (queue) {
            queue.add(e);
            flush();
        }
    }

    public static void flush() {
        synchronized (queue) {
            outStream.println("Flushing queue: size is " + queue.size());
            StringBuilder sb = new StringBuilder();
            for (String s: queue) {
                sb.append(s);
                sb.append("-");
            }
            byte[] bArray = sb.toString().getBytes();
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                DeflaterOutputStream dos = new DeflaterOutputStream(os);
                dos.write(bArray);
                dos.close();
                String compressed = new String(Base64.getEncoder().encode(os.toByteArray()));
                os.close();
                outStream.println("SLICING: ZLIB: " + compressed);
            } catch (Exception e){}
            queue.clear();
        }
    }
}