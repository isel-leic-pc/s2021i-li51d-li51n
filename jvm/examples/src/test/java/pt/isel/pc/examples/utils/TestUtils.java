package pt.isel.pc.examples.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;

import static org.junit.Assert.fail;

public class TestUtils {

    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    public static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            log.info("sleep was interrupted by an InterruptedException, continuing");
        }
    }

    public static void uninterruptibleJoin(Thread th) {
        while(true){
            try {
                th.join();
                return;
            } catch (InterruptedException e) {
                log.info("Ignoring InterruptionException on join");
            }
        }
    }

    public static <E, T> E expect(Class<E> exceptionClass, Callable<T> callable) {
        try {
            callable.call();
            fail("An exception was expected and didn't occur");
            // will never reach this point
            return null;
        } catch (Exception e) {
            if (!exceptionClass.isInstance(e)) {
                fail(String.format("An exception of type '%s' was expected"
                                + "and instead an exception of type '%s' ocurred",
                        exceptionClass.getName(),
                        e.getClass().getName()));
            }
            return exceptionClass.cast(e);
        }
    }

    public static void closeSilently(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // on purpose
        }
    }


}
