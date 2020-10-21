package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

public class DemoSemaphore4 {

    static class Request {
        final int units;

        Request(int units) {
            this.units = units;
        }
    }

    private int units;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();
    private final Object monitor = new Object();

    public DemoSemaphore4(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(int requestedUnits, long timeoutInMs) throws InterruptedException {

        synchronized (monitor) {
            // fast-path
            if (queue.isEmpty() && units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }

            if (Timeouts.noWait(timeoutInMs)) {
                return false;
            }

            final long deadline = Timeouts.start(timeoutInMs);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<Request> localRequest = queue.enqueue(new Request(requestedUnits));
            while (true) {
                try {
                    monitor.wait(remaining);
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
        }
    }

    public void release(int releasedUnits) {
        synchronized (monitor) {
            units += releasedUnits;
            notifyIfNeeded();
        }
    }

    private void notifyIfNeeded() {
        if (queue.isNotEmpty() && units >= queue.getHeadValue().units) {
            monitor.notifyAll();
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

