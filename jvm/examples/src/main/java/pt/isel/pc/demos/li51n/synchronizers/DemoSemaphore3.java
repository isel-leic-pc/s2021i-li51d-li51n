package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.Timeouts;

public class DemoSemaphore3 {

    private int units;
    private final Object monitor = new Object();

    public DemoSemaphore3(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(int requestedUnits, long timeoutInMs) throws InterruptedException {

        synchronized (monitor) {
            // fast-path
            if (units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }

            if (Timeouts.noWait(timeoutInMs)) {
                return false;
            }

            final long deadline = Timeouts.start(timeoutInMs);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                monitor.wait(remaining);
                if(units >= requestedUnits) {
                    units -= requestedUnits;
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if(Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }
        }
    }

    public void release(int releasedUnits) {
        synchronized (monitor) {
            units += releasedUnits;
            monitor.notifyAll();
        }
    }
}
// T1 notifies T0,
// T1 exits lock,
// T2 acquires lock, ...,
// T2 exits lock,
// T0 acquires lock


