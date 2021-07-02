import java.io.PrintStream;
import java.util.ArrayList;

public class DynamicSlicingLogger{
    static final int SIZE = 1024;
    static final ArrayList<String> queue = new ArrayList<>(SIZE);
    // static final Slicer4JWriter mw = new Slicer4JWriter(queue);
    static final PrintStream outStream = System.out;
    static {
        Runtime.getRuntime().addShutdownHook(new DynamicSlicingLoggerShutdown());
    }

    public static void println(String e) {
        synchronized (queue) {
            System.out.println("SLICING:"+e);
        }
    }

    // public static void println(String e) {
    //     synchronized (queue) {
    //         queue.add(e);
    //         if (queue.size() > SIZE) {
    //             flush();
    //             // mw.start();
    //         }
    //     }
    // }
    
    public static void flush(String e) {
        synchronized (queue) {
            flush();
            outStream.println("SLICING:"+e);
        }
    }

    public static void flush() {
        synchronized (queue) {
            outStream.println("Flushing queue");
            for (String s: queue) {
                outStream.println("SLICING:"+s);
            }
            queue.clear();
        }
    }
}