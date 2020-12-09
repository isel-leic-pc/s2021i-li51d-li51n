package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class BufferWrite implements CompletionHandler<Integer, Void> {

    private static final Logger log = LoggerFactory.getLogger(BufferWrite.class);

    private final ByteBuffer sourceBuffer;
    private final AsynchronousSocketChannel targetChannel;
    private final CompletionHandler<Integer, Void> ch;
    private int size = 0;

    private BufferWrite(ByteBuffer sourceBuffer, AsynchronousSocketChannel targetChannel,
                        CompletionHandler<Integer, Void> ch) {
        this.sourceBuffer = sourceBuffer;
        this.targetChannel = targetChannel;
        this.ch = ch;

    }

    public static void run(ByteBuffer sourceBuffer, AsynchronousSocketChannel targetChannel,
                           CompletionHandler<Integer, Void> ch) {
        BufferWrite bufferWrite = new BufferWrite(sourceBuffer, targetChannel, ch);
        try {
            log.info("Writing...");
            targetChannel.write(sourceBuffer, null, bufferWrite);
        } catch (Throwable e) {
            ch.failed(e, null);
        }
    }

    @Override
    public void completed(Integer result, Void attachment) {
        log.info("Completed write of {} bytes", result);
        if (sourceBuffer.position() == sourceBuffer.limit()) {
            log.info("Full write completed, exiting");
            ch.completed(size, null);
        } else {
            size += result;
            try {
                log.info("Writing...");
                targetChannel.write(sourceBuffer, null, this);
            } catch (Throwable th) {
                ch.failed(th, null);
            }
        }
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        ch.failed(exc, null);
    }
}
