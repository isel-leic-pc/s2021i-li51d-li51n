package pt.isel.pc.examples.futures;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.*;

public class CompletableFutureExampleTests {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureExampleTests.class);
    private static final Executor executor = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    private static <V> CompletableFuture<V> delay(V value, long delay, TimeUnit timeUnit) {
        log.info("Starting delay");
        CompletableFuture<V> cf = new CompletableFuture<>();
        scheduledExecutor.schedule(() -> {
            log.info("Completing future after delay");
            cf.complete(value);
        }, delay, timeUnit);
        log.info("Returning delay future");
        return cf;
    }

    @Test
    public void mapExample() throws InterruptedException {

        CountDownLatch cdl = new CountDownLatch(1);

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        cf
                .thenApply(x -> {
                    log.info("thenApply fn called with {}", x);
                    return x + 1;
                })
                .thenApplyAsync(x -> {
                    log.info("thenApplyAsync fn called with {}", x);
                    return x + 2;
                })
                .whenComplete((res, err) -> {
                    log.info("whenComplete action called with ({},{})", res, err);
                    cdl.countDown();
                });

        executor.execute(() -> cf.complete(42));

        cdl.await();
    }

    @Test
    public void flatMapExample() throws InterruptedException {

        CountDownLatch cdl = new CountDownLatch(1);

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        cf
                .thenApply(x -> {
                    log.info("thenApply fn called with {}", x);
                    return x + 1;
                })
                .thenApplyAsync(x -> {
                    log.info("thenApplyAsync fn called with {}", x);
                    return x + 2;
                })
                .thenCompose(x -> {
                    log.info("thenCompose fn called wit {}", x);
                    return delay(x + 3, 1000, TimeUnit.MILLISECONDS);
                })
                .whenComplete((res, err) -> {
                    log.info("whenComplete action called with ({},{})", res, err);
                    cdl.countDown();
                });

        executor.execute(() -> cf.complete(42));

        cdl.await();
    }

    @Test
    public void errorExample() throws InterruptedException {

        CountDownLatch cdl = new CountDownLatch(1);

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        cf
                .thenApply(x -> {
                    log.info("thenApply fn called with {}", x);
                    if(Instant.now().getEpochSecond() < Long.MAX_VALUE) throw new RuntimeException("error");
                    return x + 1;
                })
                .thenApplyAsync(x -> {
                    log.info("thenApplyAsync fn called with {}", x);
                    return x + 2;
                })
                .thenCompose(x -> {
                    log.info("thenCompose fn called wit {}", x);
                    return delay(x + 3, 1000, TimeUnit.MILLISECONDS);
                })
                .whenComplete((res, err) -> {
                    log.info("whenComplete action called with ({},{})", res, err.getMessage());
                    cdl.countDown();
                });

        executor.execute(() -> cf.complete(42));

        cdl.await();
    }

    @Test
    public void errorCatchExample() throws InterruptedException {

        CountDownLatch cdl = new CountDownLatch(1);

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        cf
                .thenApply(x -> {
                    log.info("thenApply fn called with {}", x);
                    if(Instant.now().getEpochSecond() < Long.MAX_VALUE) throw new RuntimeException("error");
                    return x + 1;
                })
                .thenApplyAsync(x -> {
                    log.info("thenApplyAsync fn called with {}", x);
                    return x + 2;
                })
                .exceptionally(e -> 13)
                .thenCompose(x -> {
                    log.info("thenCompose fn called with {}", x);
                    return delay(x + 3, 1000, TimeUnit.MILLISECONDS);
                })
                .whenComplete((res, err) -> {
                    log.info("whenComplete action called with ({},{})", res, err);
                    cdl.countDown();
                });

        executor.execute(() -> cf.complete(42));

        cdl.await();
    }

}
