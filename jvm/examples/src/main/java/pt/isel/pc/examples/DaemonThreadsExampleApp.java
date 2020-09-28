package pt.isel.pc.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class DaemonThreadsExampleApp {

    private static final Logger log = LoggerFactory.getLogger(DaemonThreadsExampleApp.class);

    public static void main(String[] args) {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown is in progress");
        }));

        Thread daemonThread = new Thread(() -> {
            log.info("Daemon thread started, waiting for 4 seconds");
            sleep(Duration.ofSeconds(4));
        });
        daemonThread.setDaemon(true);
        daemonThread.start();

        Thread nonDaemonThread = new Thread(() -> {
            log.info("Non-daemon thread started, waiting for 2 seconds");
            sleep(Duration.ofSeconds(2));
        });
        nonDaemonThread.setDaemon(false);
        nonDaemonThread.start();

        log.info("Ending main");
    }

    // Notice how the shutdown occurs approx. 2 seconds after the main method returns
    // This happens because there is a non-daemon thread that is blocked for 2 seconds.
    // Notice also how the shutdown doesn't wait for daemon thread (blocked for 4 seconds) to end.

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            log.info("sleep was interrupted by an InterruptedException, continuing");
        }
    }
}
