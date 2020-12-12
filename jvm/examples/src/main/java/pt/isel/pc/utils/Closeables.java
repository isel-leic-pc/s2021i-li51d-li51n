package pt.isel.pc.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class Closeables {

    private static final Logger log = LoggerFactory.getLogger(Closeables.class);

    private Closeables() {
        // static class
    }

    public static Throwable safeClose(Throwable maybeOriginalException, Closeable... closeables) {
        for (Closeable closeable : closeables) {
            if (closeable != null) {
                try {
                    log.info("closing {}", closeable);
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
