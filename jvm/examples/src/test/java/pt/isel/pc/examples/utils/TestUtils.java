package pt.isel.pc.examples.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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


}
