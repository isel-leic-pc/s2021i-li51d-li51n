package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.utils.CompositeThrowable;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicInteger;

public class ReadWriteParallel {

    private static final Logger log = LoggerFactory.getLogger(ReadWriteParallel.class);

    private final AsynchronousSocketChannel sourceChannel;
    private final AsynchronousFileChannel targetChannel;
    private final CompletionHandler<Integer, Void> ch;

    private final ByteBuffer[] buffers = new ByteBuffer[]{
            ByteBuffer.allocate(8),
            ByteBuffer.allocate(8),
    };
    private int readBufferIx = 0;

    private int getReadBufferIx() {
        return readBufferIx;
    }
    private int getWriteBufferIx() {
        return (readBufferIx + 1) % 2;
    }
    private void flipBuffers() {
        readBufferIx = (readBufferIx + 1) % 2;
    }

    private Throwable readException = null;
    private Throwable writeException = null;

    private int size = 0;
    private int filePosition = 0;

    private final AtomicInteger pendingOperations = new AtomicInteger();
    private boolean readCompleted = false;

    private ReadWriteParallel(
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
        ReadWriteParallel readWrite = new ReadWriteParallel(sourceChannel, targetChannel, ch);
        readWrite.read();
    }

    private void read() {
        log.info("Start read");
        pendingOperations.set(1);
        tryRun(() -> sourceChannel.read(buffers[getReadBufferIx()], null, readHandler));
    }

    private final CompletionHandler<Integer, Void> readHandler = new CompletionHandler<>() {

        @Override
        public void completed(Integer result, Void attachment) {
            log.info("Completed read of {} bytes", result);
            if (result == -1) {
                log.info("Reached end of read");
                readCompleted = true;
            } else {
                size += result;
            }
            nextStep();
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            readException = exc;
            nextStep();
        }
    };

    private final CompletionHandler<Integer, Void> writeHandler = new CompletionHandler<>() {

        @Override
        public void completed(Integer result, Void attachment) {
            log.info("Completed write of {} bytes", result);
            filePosition += result;
            log.info("File position updated to {}", filePosition);
            nextStep();
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            writeException = exc;
            nextStep();
        }
    };

    private void nextStep() {
        if (pendingOperations.decrementAndGet() != 0) {
            return;
        }
        // No pending operation, can proceed
        log.info("starting new cycle");
        if (readException != null || writeException != null) {
            log.info("At least one exception pending, ending");
            CompositeThrowable exc = CompositeThrowable.make(readException, writeException);
            ch.failed(exc, null);
            return;
        }

        if (readCompleted) {
            log.info("Ended copy, exiting");
            ch.completed(size, null);
            return;
        }
        flipBuffers();
        buffers[getReadBufferIx()].clear();
        buffers[getWriteBufferIx()].flip();
        pendingOperations.set(2);
        log.info("Starting next read");
        startRead();
        log.info("Starting next write");
        startWrite();
    }

    private void startRead() {
        try {
            sourceChannel.read(buffers[getReadBufferIx()], null, readHandler);
        } catch (Throwable e) {
            ch.failed(e, null);
        }
    }

    private void startWrite() {
        try {
            if(filePosition > 100) {
                throw new RuntimeException("Test Error");
            }
            targetChannel.write(buffers[getWriteBufferIx()], filePosition, null, writeHandler);
        } catch (Throwable e) {
            log.warn("startWrite failed with {}", e.getMessage());
            writeException = e;
            nextStep();
        }
    }

    private void tryRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable th) {
            ch.failed(th, null);
        }
    }
}
