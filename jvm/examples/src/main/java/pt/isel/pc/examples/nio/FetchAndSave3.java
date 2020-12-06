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
import java.util.concurrent.ExecutionException;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

// Asynchronous, state-machine
public class FetchAndSave3 implements CompletionHandler<Object, Void> {

    private static final Logger log = LoggerFactory.getLogger(FetchAndSave3.class);

    private final URL url;
    private final String fileName;
    private final CompletionHandler<Void, Void> completionHandler;
    private AsynchronousSocketChannel socket;
    private int sendCount;
    private AsynchronousFileChannel file;
    private ByteBuffer requestBuffer;
    private int filePosition;
    private int readCount;
    private ByteBuffer copyBuffer;
    private int writeCount;


    enum States {
        connect_0,
        writeRequest_1,
        afterWriteRequest_2,
        afterReadSocket_3,
        afterWriteFile_4,
        done,
    }

    private States state;

    @Override
    public void completed(Object result, Void ignore) {
        dispatch(result);
    }

    @Override
    public void failed(Throwable e, Void ignore) {
        Throwable exc = Closeables.safeClose(e, socket, file);
        completionHandler.failed(exc, null);
    }

    // Required because the NIO methods don't accept a CompletionHandler of a parent type
    // (which would be safe)
    private <T> CompletionHandler<T, Void> completionHandler() {
        return new CompletionHandler<>() {
            @Override
            public void completed(T result, Void attachment) {
                FetchAndSave3.this.completed(result, attachment);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                FetchAndSave3.this.failed(exc, attachment);
            }
        };
    }

    public FetchAndSave3(URL url, String fileName, CompletionHandler<Void, Void> completionHandler) {
        this.url = url;
        this.fileName = fileName;
        this.completionHandler = completionHandler;
    }

    public static void run(URL url, String fileName, CompletionHandler<Void, Void> completionHandler)
            throws IOException, ExecutionException, InterruptedException {
        FetchAndSave3 stateMachine = new FetchAndSave3(url, fileName, completionHandler);
        stateMachine.start();
    }

    private void start() throws InterruptedException, ExecutionException, IOException {
        state = States.connect_0;
        dispatch(null);
    }

    private void dispatch(Object result) {
        try {
            switch (state) {
                case connect_0:
                    connect_0();
                    break;
                case writeRequest_1:
                    writeRequest_1();
                    break;
                case afterWriteRequest_2:
                    sendCount = (Integer) result;
                    afterWriteRequest_2();
                    break;
                case afterReadSocket_3:
                    readCount = (Integer) result;
                    afterReadSocket_3();
                    break;
                case afterWriteFile_4:
                    writeCount = (Integer) result;
                    afterWriteFile_4();
                    break;
            }
            if (state == States.done) {
                Throwable th = Closeables.safeClose(null, socket, file);
                if(th != null) {
                    completionHandler.failed(th, null);
                }else {
                    completionHandler.completed(null, null);
                }
            }
        } catch (Throwable e) {
            failed(e, null);
        }
    }

    private void connect_0() throws IOException, ExecutionException, InterruptedException {
        socket = AsynchronousSocketChannel.open();
        file = AsynchronousFileChannel.open(Paths.get(fileName), WRITE, CREATE);
        socket.setOption(StandardSocketOptions.SO_SNDBUF, 16);
        state = States.writeRequest_1;
        socket.connect(new InetSocketAddress(url.getHost(), url.getPort()), null, completionHandler());
    }

    private void writeRequest_1() throws ExecutionException, InterruptedException {
        String requestString = "GET " + url.getPath() + " HTTP/1.1\r\n"
                + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                + "\r\n";
        byte[] requestBytes = requestString.getBytes(StandardCharsets.US_ASCII);
        requestBuffer = ByteBuffer.wrap(requestBytes);
        state = States.afterWriteRequest_2;
        socket.write(requestBuffer, null, completionHandler());
    }

    private void afterWriteRequest_2() throws ExecutionException, InterruptedException {
        log.info("Sent {} bytes", sendCount);
        if (requestBuffer.position() != requestBuffer.limit()) {
            socket.write(requestBuffer, null, completionHandler());
        } else {
            filePosition = 0;
            copyBuffer = ByteBuffer.allocate(8);
            state = States.afterReadSocket_3;
            socket.read(copyBuffer, null, completionHandler());
        }
    }

    private void afterReadSocket_3() throws ExecutionException, InterruptedException {
        log.info("Read {} bytes from socket", readCount);
        if (readCount == -1) {
            state = States.done;
            return;
        }
        copyBuffer.flip();
        state = States.afterWriteFile_4;
        file.write(copyBuffer, filePosition, null, completionHandler());
    }

    private void afterWriteFile_4() throws ExecutionException, InterruptedException {
        log.info("Wrote {} bytes into file", writeCount);
        filePosition += writeCount;
        copyBuffer.clear();
        state = States.afterReadSocket_3;
        socket.read(copyBuffer, null, completionHandler());
    }
}
