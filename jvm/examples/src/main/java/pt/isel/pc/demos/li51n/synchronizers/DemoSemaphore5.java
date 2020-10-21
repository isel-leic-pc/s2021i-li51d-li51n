package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DemoSemaphore5 {

    static class Request {
        final int units;
        final Condition condition;

        Request(int units, Condition condition) {

            this.units = units;
            this.condition = condition;
        }
    }

    private int units;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();
    private final Lock monitor = new ReentrantLock();

    public DemoSemaphore5(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(int requestedUnits, long timeoutInMs) throws InterruptedException {

        monitor.lock();
        try {
            // fast-path
            if (queue.isEmpty() && units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }

            if (Timeouts.noWait(timeoutInMs)) {
                return false;
            }

            // wait-path
            final long deadline = Timeouts.start(timeoutInMs);
            long remaining = Timeouts.remaining(deadline);
            Condition threadCondition = monitor.newCondition();
            NodeLinkedList.Node<Request> localRequest = queue.enqueue(
                    new Request(requestedUnits, threadCondition));
            while (true) {
                try {
                    threadCondition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    queue.remove(localRequest);
                    notifyIfNeeded();
                    throw e;
                }
                if (queue.isHeadNode(localRequest) && units >= requestedUnits) {
                    units -= requestedUnits;
                    queue.remove(localRequest);
                    notifyIfNeeded();
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(localRequest);
                    notifyIfNeeded();
                    return false;
                }
            }
        } finally {
            monitor.unlock();
        }
    }

    public void release(int releasedUnits) {
        monitor.lock();
        try {
            units += releasedUnits;
            notifyIfNeeded();
        } finally {
            monitor.unlock();
        }
    }

    private void notifyIfNeeded() {
        if (queue.isNotEmpty() && units >= queue.getHeadValue().units) {
            queue.getHeadValue().condition.signal();
        }
    }
}

/*
 units = 2
 queue = [T0-request(5), T1-request(1)](
 release(4) -> units = 6, notifyAll()
 T1 observes state and calls wait again
 T0 observes state
 queue = [T1-request(1)], units = 1 -> notifyAll()
 T1 observes state
 */

