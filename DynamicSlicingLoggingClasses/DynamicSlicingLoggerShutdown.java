public class DynamicSlicingLoggerShutdown extends Thread {
    DynamicSlicingLoggerShutdown() {
    }

    @Override
    public void run() {
        DynamicSlicingLogger.flush();
    }
}