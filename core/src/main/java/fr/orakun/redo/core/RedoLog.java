package fr.orakun.redo.core;

/** Logging bridge: each platform plugs in Minecraft's logger. */
public final class RedoLog {
    public interface Sink {
        void info(String msg);

        void warn(String msg);

        void error(String msg, Throwable t);
    }

    private static volatile Sink sink = new Sink() {
        @Override
        public void info(String msg) {
            System.out.println("[Redo] " + msg);
        }

        @Override
        public void warn(String msg) {
            System.out.println("[Redo] WARN " + msg);
        }

        @Override
        public void error(String msg, Throwable t) {
            System.err.println("[Redo] ERROR " + msg);
            if (t != null) t.printStackTrace();
        }
    };

    private RedoLog() {
    }

    public static void setSink(Sink s) {
        sink = s;
    }

    public static void info(String msg) {
        sink.info(msg);
    }

    public static void warn(String msg) {
        sink.warn(msg);
    }

    public static void error(String msg, Throwable t) {
        sink.error(msg, t);
    }
}
