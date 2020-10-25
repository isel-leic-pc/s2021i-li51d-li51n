package pt.isel.pc.examples.synchronizers;

import java.util.concurrent.TimeUnit;

public interface NArySemaphore {

    boolean acquire(int requestedUnits, long timeout, TimeUnit timeUnit) throws InterruptedException;

    void release(int releasedUnits);
}
