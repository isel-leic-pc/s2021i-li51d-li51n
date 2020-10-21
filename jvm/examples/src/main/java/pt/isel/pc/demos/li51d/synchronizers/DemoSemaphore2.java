package pt.isel.pc.demos.li51d.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

public class DemoSemaphore2 {

    static class Request {
        public final int requestedUnits;

        Request(int requestUnits) {
            this.requestedUnits = requestUnits;
        }
    }

    private int units;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    private final Object monitor = new Object();

    public DemoSemaphore2(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(int requestedUnits, long timeout) throws InterruptedException {
        synchronized (monitor) {
            // fast-path
            if (queue.isEmpty() && units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }
            // wait-path
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            final long deadline = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<Request> node = queue.enqueue(new Request(requestedUnits));
            while (true) {
                try {
                    monitor.wait(remaining);
                } catch (InterruptedException e) {
                    queue.remove(node);
                    notifyIfNeeded();
                    throw e;
                }

                if (queue.isHeadNode(node) && units >= requestedUnits) {
                    units -= requestedUnits;
                    queue.remove(node);
                    notifyIfNeeded();
                    return true;
                }

                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
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
        if (queue.isNotEmpty() && units >= queue.getHeadValue().requestedUnits) {
            monitor.notifyAll();
        }
    }
}


