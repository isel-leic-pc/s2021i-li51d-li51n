package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManualResetEvent {

    private boolean flag;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();

    // sets the flag to false
    public void reset() {
        monitor.lock();
        try {
            flag = false;
        } finally {
            monitor.unlock();
        }
    }

    // sets the flat to true
    // and ensures all threads waiting on waitSet do return from this method
    public void set() {
        monitor.lock();
        try {
            flag = true;
            condition.signalAll();
        } finally {
            monitor.unlock();
        }
    }

    // waits until the flag is true
    public boolean waitSet(long timeoutInMs) throws InterruptedException {
        monitor.lock();
        try {
            // fast-path
            if (flag) {
                return true;
            }

            if (Timeouts.noWait(timeoutInMs)) {
                return false;
            }

            // wait-path
            long deadline = Timeouts.start(timeoutInMs);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                condition.await(remaining, TimeUnit.MILLISECONDS);
                if (flag) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }

        } finally {
            monitor.unlock();
        }
    }
}

/*
    flag == false
    T1: waitSet() -> lock(), condition.await() [blocked]
    T2: waitSet() -> lock(), condition.await() [blocked]
    T3: set() -> lock(), flag=true, condition.signalAll(), unlock();
    T4: reset() -> lock(), flag=false, unlock(); <-
    T1: ... condition.await(), flag? [false], condition.await(),
 */