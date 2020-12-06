package pt.isel.pc.examples.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.*;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static pt.isel.pc.examples.utils.TestUtils.closeSilently;

public class AsynchronousChannelSketches {

    private static final Logger log = LoggerFactory.getLogger(AsynchronousChannelSketches.class);

    @Test
    public void open_and_connect_channel() throws IOException, InterruptedException {
        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
        Semaphore done = new Semaphore(0);
        log.info("about to call connect");

        CompletionHandler<Void, Void> completionHandler = new CompletionHandler<>() {

            @Override
            public void completed(Void result, Void attachment) {
                log.info("connect completed successfully");
                done.release();
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                log.info("connect completed with an error - {}", exc.getMessage());
                done.release();
            }
        };

        channel.connect(new InetSocketAddress("httpbin.foo", 81), null, completionHandler);
        log.info("connect called, waiting until done");
        done.acquire();
    }

    @Test
    public void open_and_connect_multiple_channels() throws IOException, InterruptedException {
        int nOfChannels = 100;
        CountDownLatch done = new CountDownLatch(nOfChannels);
        for (int i = 0; i < nOfChannels; ++i) {
            int ix = i;
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            channel.connect(new InetSocketAddress("httpbin.org", 80), null, new CompletionHandler<Void, Void>() {

                @Override
                public void completed(Void result, Void attachment) {
                    log.info("[{}] connect completed successfully", ix);
                    closeSilently(channel);
                    done.countDown();
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    log.info("[{}] connect completed with an error - {}", ix, exc.getMessage());
                    closeSilently(channel);
                    done.countDown();
                }
            });
        }
        log.info("connect called, waiting until done");
        done.await();
    }

    @Test
    public void open_and_connect_multiple_channels_with_custom_group() throws IOException, InterruptedException {
        int nOfChannels = 100;
        CountDownLatch done = new CountDownLatch(nOfChannels);
        ExecutorService threadPool = Executors.newSingleThreadExecutor();
        AsynchronousChannelGroup channelGroup = AsynchronousChannelGroup.withThreadPool(threadPool);
        for (int i = 0; i < nOfChannels; ++i) {
            int ix = i;
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(channelGroup);
            channel.connect(new InetSocketAddress("httpbin.org", 80), null, new CompletionHandler<Void, Void>() {

                @Override
                public void completed(Void result, Void attachment) {
                    log.info("[{}] connect completed successfully", ix);
                    done.countDown();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // on purpose
                    }
                    closeSilently(channel);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    log.info("[{}] connect completed with an error - {}", ix, exc.getMessage());
                    done.countDown();
                    closeSilently(channel);
                }
            });
        }
        log.info("connect called, waiting until done");
        done.await();
    }

    @Test
    public void synchronous_fetch() throws IOException, ExecutionException, InterruptedException {
        String host = "httpbin.org";
        int port = 80;
        String path = "/delay/3";
        String fileName = "build/get.txt";
        try (
                AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
                AsynchronousFileChannel file = AsynchronousFileChannel.open(Paths.get(fileName),
                        WRITE, CREATE)) {

            socket.setOption(StandardSocketOptions.SO_SNDBUF, 16);

            Future<Void> connectFuture = socket.connect(new InetSocketAddress(host, port));
            connectFuture.get();

            String requestString = "GET " + path + " HTTP/1.1\r\n"
                    + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                    + "\r\n";
            byte[] requestBytes = requestString.getBytes(StandardCharsets.US_ASCII);
            ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);

            do {
                Future<Integer> sendRequestFuture = socket.write(requestBuffer);
                int sendCount = sendRequestFuture.get();
                log.info("Sent {} bytes", sendCount);
            } while (requestBuffer.position() != requestBuffer.limit());

            int filePosition = 0;
            ByteBuffer copyBuffer = ByteBuffer.allocate(8);
            while (true) {
                Future<Integer> readFuture = socket.read(copyBuffer);
                int readCount = readFuture.get();
                log.info("Read {} bytes from socket", readCount);
                if (readCount == -1) {
                    break;
                }
                copyBuffer.flip();
                Future<Integer> writeFuture = file.write(copyBuffer, filePosition);
                int writeCount = writeFuture.get();
                log.info("Wrote {} bytes into file", writeCount);
                filePosition += writeCount;
                copyBuffer.clear();
            }
        }
    }

}
