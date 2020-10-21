package pt.isel.pc.demos.li51d.synchronizers;

import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManualResetEvent {

    private boolean flag = false;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();

    // puts the event in the set state (i.e. flag == true)
    // and frees all waiting threads
    public void set() {
        monitor.lock();
        try {
            flag = true;
            condition.signalAll();
        } finally {
            monitor.unlock();
        }
    }

    // puts the event in the reset state (i.e. flag == false)
    public void reset() {
        monitor.lock();
        try {
            flag = false;
        } finally {
            monitor.unlock();
        }
    }

    // Waits for the event to be in the 'set' state (i.e. flag == true)
    public boolean waitSet(long timeout) throws InterruptedException {
        monitor.lock();
        try {
            // fast-path
            if (flag) {
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long deadline = Timeouts.start(timeout);
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
    T1 waitSet -> lock(), condition.await(...) ...
    T2 set -> lock(), flag = true, condition.signalAll(), unlock()
    T3 reset -> lock(), flag = false, unlock()
    T1 -> ... if(flag)


 */