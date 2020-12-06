package pt.isel.pc.utils;

import java.io.Closeable;

public class Closeables {

    private Closeables() {
        // static class
    }

    public static Throwable safeClose(Throwable maybeOriginalException, Closeable... closeables) {
        for (Closeable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Throwable e) {
                    if (maybeOriginalException != null) {
                        maybeOriginalException.addSuppressed(e);
                    } else {
                        maybeOriginalException = e;
                    }
                }
            }
        }
        return maybeOriginalException;
    }
}
