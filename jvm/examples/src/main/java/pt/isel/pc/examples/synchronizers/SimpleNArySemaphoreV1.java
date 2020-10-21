package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;

/**
 * N-ary semaphore (i.e. allowing acquisition and release of multiple units) with FIFO policy
 * and using {@link Object#notifyAll} on the {@link SimpleNArySemaphoreV1#release} method. T
 * his algorithm is not efficient and can be improved, since it produces unneeded context switches when there are
 * multiple threads waiting on the condition.
 */
public class SimpleNArySemaphoreV1 {

    static class Request {
        public final int requestedUnits;

        Request(int requestedUnits) {
            this.requestedUnits = requestedUnits;
        }
    }

    private int units;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();
    private final Object lock = new Object();

    public SimpleNArySemaphoreV1(int units) {
        this.units = units;
    }

    public boolean acquire(int requestedUnits, long timeout, TimeUnit timeUnit) throws InterruptedException {

        synchronized (lock) {
            if (units > requestedUnits) {
                units -= requestedUnits;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            NodeLinkedList.Node<Request> request = queue.enqueue(new Request(requestedUnits));
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    queue.remove(request);
                    notifyIfNeeded();
                }
                if (queue.isHeadNode(request) && units > requestedUnits) {
                    queue.pull();
                    units -= requestedUnits;
                    lock.notifyAll();
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(request);
                    notifyIfNeeded();
                    return false;
                }
            }
        }
    }

    public void release(int releasedUnits) {
        synchronized (lock) {
            units += releasedUnits;
            lock.notifyAll();
        }
    }

    private void notifyIfNeeded() {
        if (queue.isNotEmpty() && units >= queue.getHeadValue().requestedUnits) {
            lock.notifyAll();
        }
    }

}
