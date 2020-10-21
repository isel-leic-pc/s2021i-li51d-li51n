package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.Timeouts;

public class DemoSemaphore {

    private int units;
    private final Object monitor = new Object();

    public DemoSemaphore(int initialUnits) {
        this.units = initialUnits;
    }

    public void acquire() throws InterruptedException {
        synchronized (monitor) {
            while (units == 0) {
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    if (units > 0) {
                        monitor.notify();
                    }
                    throw e;
                }
            }
            units -= 1;
        }
    }

    public void release() {
        synchronized (monitor) {
            units += 1;
            monitor.notify();
        }
    }
}
// T1 notifies T0,
// T1 exits lock,
// T2 acquires lock, ...,
// T2 exits lock,
// T0 acquires lock


