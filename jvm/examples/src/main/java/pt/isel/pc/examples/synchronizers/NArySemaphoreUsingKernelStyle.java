package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NArySemaphoreUsingKernelStyle implements NArySemaphore {

    static class Request {
        final int units;
        final Condition condition;
        boolean isDone = false;

        Request(int units, Condition condition) {

            this.units = units;
            this.condition = condition;
        }
    }

    private int units;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();
    private final Lock monitor = new ReentrantLock();

    public NArySemaphoreUsingKernelStyle(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(int requestedUnits, long timeout, TimeUnit timeUnit) throws InterruptedException {

        monitor.lock();
        try {
            // fast-path
            if (queue.isEmpty() && units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }

            if (Timeouts.noWait(timeout)) {
                return false;
            }

            // wait-path
            final long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            Condition threadCondition = monitor.newCondition();
            NodeLinkedList.Node<Request> localRequest = queue.enqueue(
                    new Request(requestedUnits, threadCondition));
            while (true) {
                try {
                    threadCondition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (localRequest.value.isDone) {
                        // too late to give up!
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    queue.remove(localRequest);
                    notifyIfNeeded();
                    throw e;
                }
                if (localRequest.value.isDone) {
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
        while (queue.isNotEmpty() && units >= queue.getHeadValue().units) {
            NodeLinkedList.Node<Request> node = queue.pull();
            units -= node.value.units;
            node.value.isDone = true;
            node.value.condition.signal();
        }
    }
}
