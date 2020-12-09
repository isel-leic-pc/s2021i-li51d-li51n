package pt.isel.pc.examples.nio;

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
import java.util.function.Consumer;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class FetchAndSave4 {

    private final URL url;
    private final CompletionHandler<Integer, Void> completionHandler;
    private final AsynchronousSocketChannel socket;
    private final AsynchronousFileChannel file;

    FetchAndSave4(AsynchronousSocketChannel socket,
                  AsynchronousFileChannel file,
                  URL url,
                  CompletionHandler<Integer, Void> completionHandler) {
        this.socket = socket;
        this.file = file;
        this.url = url;
        this.completionHandler = completionHandler;
    }

    public static void run(URL url, String fileName, CompletionHandler<Integer, Void> completionHandler) {
        AsynchronousSocketChannel socket = null;
        AsynchronousFileChannel file = null;
        try {
            socket = AsynchronousSocketChannel.open();
            file = AsynchronousFileChannel.open(Paths.get(fileName), WRITE, CREATE);
            FetchAndSave4 fas = new FetchAndSave4(socket, file, url, completionHandler);
            fas.connect(url);
        } catch (IOException ioException) {
            Throwable th = Closeables.safeClose(ioException, socket, file);
            completionHandler.failed(th, null);
        }
    }

    private void failed(Throwable th) {
        th = Closeables.safeClose(th, socket, file);
        completionHandler.failed(th, null);
    }

    private void completed(Integer size) {
        Throwable th = Closeables.safeClose(null, socket, file);
        if(th != null) {
            completionHandler.failed(th, null);
        }else{
            completionHandler.completed(size, null);
        }
    }

    private void connect(URL url) throws IOException {
        socket.setOption(StandardSocketOptions.SO_SNDBUF, 16);
        socket.connect(new InetSocketAddress(url.getHost(), url.getPort()), null, handler(this::sendRequest, this::failed));
    }

    private void sendRequest(Void result) {
        String requestString = "GET " + url.getPath() + " HTTP/1.1\r\n"
                + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                + "\r\n";
        byte[] requestBytes = requestString.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);
        BufferWrite.run(requestBuffer, socket, handler(this::copyResponse, this::failed));
    }

    private void copyResponse(Integer ignore) {
        ReadWriteParallel.run(socket, file, handler(this::completed, this::failed));
    }

    private <R> CompletionHandler<R, Void> handler(Consumer<R> completed, Consumer<Throwable> failed) {
        return new CompletionHandler<>() {
            @Override
            public void completed(R result, Void attachment) {
                completed.accept(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                failed.accept(exc);
            }
        };
    }

}
