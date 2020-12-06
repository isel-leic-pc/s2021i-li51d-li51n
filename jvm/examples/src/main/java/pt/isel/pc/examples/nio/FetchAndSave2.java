package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

// Synchronous, state-machine
public class FetchAndSave2 {

    private static final Logger log = LoggerFactory.getLogger(FetchAndSave2.class);

    private final URL url;
    private final String fileName;
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


    public FetchAndSave2(URL url, String fileName) {
        this.url = url;
        this.fileName = fileName;
    }

    public static void run(URL url, String fileName) throws IOException, ExecutionException, InterruptedException {
        new FetchAndSave2(url, fileName).loop();
    }

    private void loop() throws InterruptedException, ExecutionException, IOException {
        state = States.connect_0;
        Closeable closeSocket = () -> {
            if (socket != null) socket.close();
        };
        Closeable closeFile = () -> {
            if (file != null) file.close();
        };
        try (closeSocket; closeFile) {
            while (state != States.done) {
                switch (state) {
                    case connect_0:
                        connect_0();
                        break;
                    case writeRequest_1:
                        writeRequest_1();
                        break;
                    case afterWriteRequest_2:
                        afterWriteRequest_2();
                        break;
                    case afterReadSocket_3:
                        afterReadSocket_3();
                        break;
                    case afterWriteFile_4:
                        afterWriteFile_4();
                        break;
                }
            }
        }
    }

    private void connect_0() throws IOException, ExecutionException, InterruptedException {
        socket = AsynchronousSocketChannel.open();
        file = AsynchronousFileChannel.open(Paths.get(fileName), WRITE, CREATE);
        socket.setOption(StandardSocketOptions.SO_SNDBUF, 16);
        state = States.writeRequest_1;
        socket.connect(new InetSocketAddress(url.getHost(), url.getPort())).get();
    }

    private void writeRequest_1() throws ExecutionException, InterruptedException {
        String requestString = "GET " + url.getPath() + " HTTP/1.1\r\n"
                + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                + "\r\n";
        byte[] requestBytes = requestString.getBytes(StandardCharsets.US_ASCII);
        requestBuffer = ByteBuffer.wrap(requestBytes);
        state = States.afterWriteRequest_2;
        sendCount = socket.write(requestBuffer).get();
    }

    private void afterWriteRequest_2() throws ExecutionException, InterruptedException {
        log.info("Sent {} bytes", sendCount);
        if (requestBuffer.position() != requestBuffer.limit()) {
            sendCount = socket.write(requestBuffer).get();
        } else {
            filePosition = 0;
            copyBuffer = ByteBuffer.allocate(8);
            state = States.afterReadSocket_3;
            readCount = socket.read(copyBuffer).get();
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
        writeCount = file.write(copyBuffer, filePosition).get();
    }

    private void afterWriteFile_4() throws ExecutionException, InterruptedException {
        log.info("Wrote {} bytes into file", writeCount);
        filePosition += writeCount;
        copyBuffer.clear();
        readCount = socket.read(copyBuffer).get();
        state = States.afterReadSocket_3;
    }

}
