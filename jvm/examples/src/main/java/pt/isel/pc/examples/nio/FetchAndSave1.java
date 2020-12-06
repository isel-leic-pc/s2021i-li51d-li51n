package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

// Synchronous, direct-style
public class FetchAndSave1 {

    private static final Logger log = LoggerFactory.getLogger(FetchAndSave1.class);

    public static void run(URL url, String fileName) throws IOException, ExecutionException, InterruptedException {
        try (
                AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
                AsynchronousFileChannel file = AsynchronousFileChannel.open(Paths.get(fileName),
                        WRITE, CREATE)) {
            socket.setOption(StandardSocketOptions.SO_SNDBUF, 16);

            socket.connect(new InetSocketAddress(url.getHost(), url.getPort())).get();

            String requestString = "GET " + url.getPath() + " HTTP/1.1\r\n"
                    + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                    + "\r\n";
            byte[] requestBytes = requestString.getBytes(StandardCharsets.US_ASCII);
            ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);
            do {
                int sendCount = socket.write(requestBuffer).get();
                log.info("Sent {} bytes", sendCount);
            } while (requestBuffer.position() != requestBuffer.limit());
            int filePosition = 0;
            ByteBuffer copyBuffer = ByteBuffer.allocate(8);
            while (true) {
                int readCount = socket.read(copyBuffer).get();
                log.info("Read {} bytes from socket", readCount);
                if (readCount == -1) {
                    break;
                }
                copyBuffer.flip();
                int writeCount = file.write(copyBuffer, filePosition).get();
                log.info("Wrote {} bytes into file", writeCount);
                filePosition += writeCount;
                copyBuffer.clear();
            }
        }
    }
}
