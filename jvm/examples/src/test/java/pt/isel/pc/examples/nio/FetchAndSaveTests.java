package pt.isel.pc.examples.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class FetchAndSaveTests {

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

}
