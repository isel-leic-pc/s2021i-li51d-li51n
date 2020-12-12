package pt.isel.pc.examples.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class FetchAndSaveTests {

    private static final Logger log = LoggerFactory.getLogger(FetchAndSaveTests.class);

    @Test
    public void testFetchAndSave1() throws IOException, InterruptedException, ExecutionException {
        FetchAndSave1.run(new URL("http://httpbin.org:80/get"), "build/get1.txt");
    }

    @Test
    public void testFetchAndSave2() throws IOException, InterruptedException, ExecutionException {
        FetchAndSave2.run(new URL("http://httpbin.org:80/get"), "build/get2.txt");
    }

    @Test
    public void testFetchAndSave3() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch done = new CountDownLatch(1);

        FetchAndSave3.run(new URL("http://httpbin.org:80/get"), "build/get3.txt", new CompletionHandler<>() {
            @Override
            public void completed(Void result, Void attachment) {
                done.countDown();
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                done.countDown();
            }
        });

        done.await();
    }

    @Test
    public void testFetchAndSave4() throws IOException, InterruptedException {
        CountDownLatch done = new CountDownLatch(1);

        FetchAndSave4.run(new URL("http://httpbin.org:80/get"), "build/get4.txt", new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Void attachment) {
                log.error("fetch completed with {} bytes", result);
                done.countDown();
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                log.error("fetch failed", exc);
                done.countDown();
            }
        });

        done.await();
    }

    @Test
    public void testFetchAndSave5() throws IOException, InterruptedException {
        CountDownLatch done = new CountDownLatch(1);

        FetchAndSave5.run(new URL("http://httpbin.org:80/get"), "build/get5.txt")
            .whenComplete((res, exc) -> {
                done.countDown();
                log.info("complete: ({}, {})", res, exc != null ? exc.getMessage() : "-");
            });

        done.await();
    }

}
