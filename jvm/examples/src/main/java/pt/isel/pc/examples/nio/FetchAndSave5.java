package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.utils.Closeables;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class FetchAndSave5 {

    private static final Logger log = LoggerFactory.getLogger(FetchAndSave5.class);

    public static CompletableFuture<Integer> run(URL url, String fileName) {
        AsynchronousSocketChannel socket = null;
        AsynchronousFileChannel file = null;
        try {
            socket = AsynchronousSocketChannel.open();
            file = AsynchronousFileChannel.open(Paths.get(fileName), WRITE, CREATE);
            socket.setOption(StandardSocketOptions.SO_SNDBUF, 16);
            AsynchronousSocketChannel finalSocket = socket;
            CompletableFuture<Void> connected = wrap(ch -> finalSocket.connect(new InetSocketAddress(url.getHost(), url.getPort()), null, ch));
            AsynchronousFileChannel finalFile = file;
            return connected
                    .thenComposeAsync(ignore -> {
                        String requestString = "GET " + url.getPath() + " HTTP/1.1\r\n"
                                + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                                + "\r\n";
                        byte[] requestBytes = requestString.getBytes(StandardCharsets.US_ASCII);
                        ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);
                        return send(finalSocket, requestBuffer);
                    })
                    .thenComposeAsync(ignore -> copyResponse(finalSocket, finalFile, ByteBuffer.allocate(8), 0))
                    .handle((res, ex) -> {
                        Throwable th = Closeables.safeClose(ex, finalSocket, finalFile);
                        if (th != null) throw new RuntimeException(th);
                        return res;
                    });

        } catch (IOException ioException) {
            Throwable th = Closeables.safeClose(ioException, socket, file);
            return CompletableFuture.failedFuture(th);
        }
    }

    private static CompletableFuture<Void> send(AsynchronousSocketChannel channel, ByteBuffer requestBuffer) {
        return write(channel, requestBuffer)
                .thenComposeAsync(writeSize -> {
                    log.info("sent {} bytes", writeSize);
                    if (requestBuffer.position() == requestBuffer.limit()) {
                        log.info("send completed");
                        return CompletableFuture.completedFuture(null);
                    } else {
                        return send(channel, requestBuffer);
                    }
                });
    }

    private static CompletableFuture<Integer> copyResponse(
            AsynchronousSocketChannel readChannel,
            AsynchronousFileChannel writeChannel,
            ByteBuffer buf,
            int writePosition) {

        return read(readChannel, buf)
                .thenComposeAsync(size -> {
                    log.info("completed read of {} bytes", size);
                    if (size == -1) {
                        log.info("copy response completed");
                        return CompletableFuture.completedFuture(writePosition);
                    } else {
                        buf.flip();
                        return write(writeChannel, buf, writePosition)
                                .thenComposeAsync(writeSize -> {
                                    log.info("completed write of {} bytes", size);
                                    buf.clear();
                                    return copyResponse(readChannel, writeChannel, buf, writePosition + size);
                                });
                    }
                });

    }

    private static CompletableFuture<Integer> write(AsynchronousSocketChannel channel, ByteBuffer buffer) {
        return wrap(ch -> channel.write(buffer, null, ch));
    }

    private static CompletableFuture<Integer> read(AsynchronousSocketChannel channel, ByteBuffer buffer) {
        return wrap(ch -> channel.read(buffer, null, ch));
    }

    private static CompletableFuture<Integer> write(AsynchronousFileChannel channel, ByteBuffer buffer, long filePosition) {
        return wrap(ch -> channel.write(buffer, filePosition, null, ch));
    }

    private static <V> CompletableFuture<V> wrap(Consumer<CompletionHandler<V, Void>> consumer) {
        CompletableFuture<V> cf = new CompletableFuture<>();
        consumer.accept(new CompletionHandler<>() {
            @Override
            public void completed(V result, Void attachment) {
                cf.complete(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                cf.completeExceptionally(exc);
            }
        });
        return cf;
    }
}
