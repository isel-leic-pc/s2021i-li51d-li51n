package pt.isel.pc.examples;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.examples.utils.TestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Tests demonstrating basic thread functionality, namely:
 * <ul>
 *     <li>Creation.</li>
 *     <li>Starting.</li>
 *     <li>Joining, i.e., synchronizing with thread termination.</li>
 *     <li>Interruption</li>
 * </ul>
 *
 * Important: most (if not all) of these tests do NOT assert anything.
 * Their primary aim is to illustrate the {@link Thread} API.
 * Look into the log output to see what is happening.
 */
public class ThreadBasicsTests {

    private static final Logger log = LoggerFactory.getLogger(ThreadBasicsTests.class);

    /*
     * This method defines the thread execution code:
     * - The thread will start by calling this method.
     * - When the method ends, the thread will also end.
     */
    private static void threadMethod()  {
        String name = Thread.currentThread().getName();
        log.info("Running on thread '{}'", name);
        TestUtils.sleep(Duration.ofSeconds(2));
    }

    @Test
    public void create_start_and_join_with_new_thread() throws InterruptedException {
        String name = Thread.currentThread().getName();
        log.info("Starting test on thread '{}'", name);

        // We create a thread by creating a Thread object, passing in a method reference
        Thread th = new Thread(ThreadBasicsTests::threadMethod);

        // By default threads are not ready to run after they are created.
        // Only after Thread#start is called is the thread considered in the "ready" state.
        th.start();
        log.info("New thread started, waiting for it to end");

        // The Thread#join can be used to synchronize with the thread termination.
        // Thread#join will only return after
        // - the *referenced* thread ends
        // - or the *calling* thread is interrupted
        // - or the optional timeout elapses
        th.join();
        log.info("New thread ended, finishing test");
    }

    @Test
    public void we_can_have_multiple_threads_running_the_same_method() throws InterruptedException {
        String name = Thread.currentThread().getName();
        log.info("Starting test on thread '{}'", name);

        // We can create multiples threads referencing the same method
        List<Thread> ths = List.of(
                new Thread(ThreadBasicsTests::threadMethod),
                new Thread(ThreadBasicsTests::threadMethod)
        );

        ths.forEach(Thread::start);
        log.info("New threads started, waiting for them to end");

        // Quiz: why aren't we using Thread#join here
        ths.forEach(TestUtils::uninterruptibleJoin);
        log.info("New threads ended, finishing test");
    }

    @Test
    public void create_thread_using_a_lambda() throws InterruptedException {
        int localVariableOfMainThread = 42;

        log.info("Starting test on thread '{}'", Thread.currentThread().getName());

        // Threads can be created by providing a lambda expression
        // Note that a lambda expression can use variables from the *enclosing scope*,
        // such as localVariableOfMainThread``
        // This is simultaneously useful and dangerous, since those *local* variables will now
        // be accessible from *multiple* threads.
        Thread th = new Thread(() -> {

            // Notice how in this thread we are using a local variable from a different thread,
            // (the main thread).
            log.info("Running on thread '{}', localVariableOfMainThread = {}",
                    Thread.currentThread().getName(),
                    localVariableOfMainThread);
            TestUtils.sleep(Duration.ofSeconds(2));
        });

        th.start();
        th.join();
        log.info("New thread ended, finishing test");
    }

    // Threads can also be defined by deriving from the Thread class (this is Java afterall)
    static class MyThread extends Thread {
        @Override
        public void run() {
            log.info("Running on thread '{}'", Thread.currentThread().getName());
            TestUtils.sleep(Duration.ofSeconds(2));
        }
    }

    @Test
    public void create_thread_using_derived_classes() throws InterruptedException {

        log.info("Starting test on thread '{}'", Thread.currentThread().getName());
        MyThread th = new MyThread();
        th.start();
        th.join();
        log.info("New thread ended, finishing test");
    }

    @Test
    public void threads_can_be_interrupted() throws InterruptedException {
        log.info("Starting test on thread '{}'", Thread.currentThread().getName());
        Thread th = new Thread(() -> {

            // Notice how in this thread we are using a local variable from a different thread,
            // (the main thread).
            log.info("Running on thread '{}, waiting for 4 seconds'", Thread.currentThread().getName());
            TestUtils.sleep(Duration.ofSeconds(4));
        });
        th.start();
        log.info("waiting for 2 seconds");
        TestUtils.sleep(Duration.ofSeconds(2));
        log.info("interrupting thread");
        th.interrupt();
        th.join();
        log.info("New thread ended, finishing test. Look to what happened to the created thread");
    }

    @Test
    public void InterruptedException_only_happens_on_interruptible_points() throws InterruptedException {
        log.info("Starting test on thread '{}'", Thread.currentThread().getName());
        Thread th = new Thread(() -> {

            // Notice how in this thread we are using a local variable from a different thread,
            // (the main thread).
            log.info("Running on thread '{}, running for 4 seconds'", Thread.currentThread().getName());
            Instant limit = Instant.now().plus(4, SECONDS);
            while(Instant.now().isBefore(limit)) {
                // no interruptible method is called
            }
        });
        th.start();
        log.info("waiting for 2 seconds");
        TestUtils.sleep(Duration.ofSeconds(2));
        log.info("interrupting thread");
        th.interrupt();
        th.join();
        log.info("New thread ended, finishing test. Look to what happened to the created thread");
    }
    // In this case the interruption was ignored because the target thread never called an interruptible
    // method.
    // What are interruptible methods? *Most* of the methods that wait for something to happen
    // (time to elapse, I/O to complete, synchronization with other threads)

    @Test
    public void threads_can_check_the_interrupted_state() throws InterruptedException {
        log.info("Starting test on thread '{}'", Thread.currentThread().getName());
        Thread th = new Thread(() -> {

            // Notice how in this thread we are using a local variable from a different thread,
            // (the main thread).
            log.info("Running on thread '{}, running for 4 seconds'", Thread.currentThread().getName());
            Instant limit = Instant.now().plus(4, SECONDS);
            while(Instant.now().isBefore(limit)) {
                if(Thread.interrupted()) {
                    log.info("Current thread is interrupted, ending.");
                    return;
                }
            }
        });
        th.start();
        log.info("waiting for 2 seconds");
        TestUtils.sleep(Duration.ofSeconds(2));
        log.info("interrupting thread");
        th.interrupt();
        th.join();
        log.info("New thread ended, finishing test. Look to what happened to the created thread");
    }

    @Test
    public void threads_can_throw_silently() {

        Thread th = new Thread(() -> {
            TestUtils.sleep(Duration.ofSeconds(2));
            throw new RuntimeException("Booom!");
        });
        th.start();
    }

    @Test
    public void threads_can_have_an_unhandled_exception_handler() throws InterruptedException {

        Thread th = new Thread(() -> {
            TestUtils.sleep(Duration.ofSeconds(2));
            throw new RuntimeException("Booom!");
        });
        th.setUncaughtExceptionHandler((t, e) -> {
            log.info("Uncaught exception on {}: {}", t.getName(), e.getMessage());
        });
        th.start();
        log.info("Thread started");
        th.join();
    }

    @Test
    public void there_can_be_a_default_unhandled_exception_handler() throws InterruptedException {

        Thread th = new Thread(() -> {
            TestUtils.sleep(Duration.ofSeconds(2));
            throw new RuntimeException("Booom!");
        });
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.info("Uncaught exception on {}: {}", t.getName(), e.getMessage());
        });
        th.start();
        log.info("Thread started");
        th.join();
    }

}
