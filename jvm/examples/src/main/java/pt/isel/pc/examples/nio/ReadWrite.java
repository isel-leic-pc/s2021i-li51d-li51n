package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class ReadWrite {

    private static final Logger log = LoggerFactory.getLogger(ReadWrite.class);

    private final AsynchronousSocketChannel sourceChannel;
    private final AsynchronousFileChannel targetChannel;
    private final CompletionHandler<Integer, Void> ch;

    private final ByteBuffer buf = ByteBuffer.allocate(8);
    private int size = 0;
    private int filePosition = 0;

    private ReadWrite(
            AsynchronousSocketChannel sourceChannel,
            AsynchronousFileChannel targetChannel,
            CompletionHandler<Integer, Void> ch
    ) {

        this.sourceChannel = sourceChannel;
        this.targetChannel = targetChannel;
        this.ch = ch;
    }

    public static void run(AsynchronousSocketChannel sourceChannel,
                    AsynchronousFileChannel targetChannel,
                    CompletionHandler<Integer, Void> ch) {
        ReadWrite readWrite = new ReadWrite(sourceChannel, targetChannel, ch);
        readWrite.read();
    }

    private void read() {
        log.info("Start read");
        tryRun(() -> sourceChannel.read(buf, null, readHandler));
    }

    private final CompletionHandler<Integer, Void> readHandler = new CompletionHandler<>() {

        @Override
        public void completed(Integer result, Void attachment) {
            log.info("Completed read of {} bytes", result);
            tryRun(() -> {
                if (result == -1) {
                    log.info("Completed copy, exiting");
                    ch.completed(size, null);
                }else {
                    size += result;
                    buf.flip();
                    log.info("Start write of {} bytes at position {}", result, filePosition);
                    targetChannel.write(buf, filePosition, null, writeHandler);
                }
            });
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            ch.failed(exc, null);
        }
    };

    private final CompletionHandler<Integer, Void> writeHandler = new CompletionHandler<>() {

        @Override
        public void completed(Integer result, Void attachment) {
            log.info("Completed write of {} bytes", result);
            tryRun(() -> {
                filePosition += result;
                log.info("File position updated to {}", filePosition);
                buf.clear();
                log.info("Start read");
                sourceChannel.read(buf, null, readHandler);
            });
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            ch.failed(exc, null);
        }
    };

    private void tryRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable th) {
            ch.failed(th, null);
        }
    }
}
