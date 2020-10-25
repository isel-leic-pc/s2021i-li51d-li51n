package pt.isel.pc.examples.utils;

import pt.isel.pc.utils.UnexpectedExceptionError;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;

public class TestHelper {

    @FunctionalInterface
    public interface TestFunction {
        void run(int index, Supplier<Boolean> isDone) throws Exception;
    }

    private final List<Thread> ths = new LinkedList<>();
    private final ConcurrentLinkedQueue<AssertionError> failures = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();
    private final Supplier<Boolean> isDone;
    private final Duration testDuration;

    public TestHelper(Duration testDuration) {
        this.testDuration = testDuration;
        final Instant deadline = Instant.now().plus(testDuration);
        isDone = () -> Instant.now().compareTo(deadline) > 0;
    }

    public void createAndStart(int index, TestFunction testFunction) {
        Thread th = new Thread(() -> {
            try {
                testFunction.run(index, isDone);
            } catch (InterruptedException e) {
                // ignore
            } catch (AssertionError e) {
                failures.add(e);
            } catch (Exception e) {
                errors.add(e);
            }
        });
        th.start();
        ths.add(th);
    }

    public void createAndStartMultiple(int n, TestFunction testFunction) {
        for (int i = 0; i < n; ++i) {
            createAndStart(i, testFunction);
        }
    }

    public void interruptAndJoin() throws InterruptedException {
        for (Thread th : ths) {
            th.interrupt();
            th.join(2000);
            assertFalse("thread should have stopped", th.isAlive());
        }
        if (!failures.isEmpty()) {
            throw failures.peek();
        }
        if (!errors.isEmpty()) {
            throw new UnexpectedExceptionError(errors.peek());
        }
    }

    public void join() throws InterruptedException {
        for (Thread th : ths) {
            th.join(testDuration.toMillis()+1000);
            if(th.isAlive()) {
                throw new AssertionError("Thread did not end in expected time");
            }
        }
        if (!failures.isEmpty()) {
            throw failures.peek();
        }
        if (!errors.isEmpty()) {
            throw new UnexpectedExceptionError(errors.peek());
        }
    }

}
