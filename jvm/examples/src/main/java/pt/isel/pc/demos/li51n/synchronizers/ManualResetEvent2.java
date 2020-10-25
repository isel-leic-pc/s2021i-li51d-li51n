package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManualResetEvent2 {

    static class Request {
        boolean isDone = false;
    }

    private boolean flag;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

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
            while (queue.isNotEmpty()) {
                NodeLinkedList.Node<Request> node = queue.pull();
                node.value.isDone = true;
            }
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
            NodeLinkedList.Node<Request> node = queue.enqueue(new Request());
            while (true) {
                try {
                    condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if(!node.value.isDone) {
                        queue.remove(node);
                    }
                    throw e;
                }
                if (node.value.isDone) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
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