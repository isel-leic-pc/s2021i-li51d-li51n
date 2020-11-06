package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class SimpleThreadPool {

    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<Runnable> queue = new NodeLinkedList<>();
    private int runningWorkers = 0;
    private final int maxWorkers;

    public SimpleThreadPool(int maxWorkers) {
        this.maxWorkers = maxWorkers;
    }

    public void execute(Runnable runnable) {
        monitor.lock();
        try {
            assertInvariant();
            if (runningWorkers < maxWorkers) {
                new Thread(() -> threadMethod(runnable)).start();
                runningWorkers += 1;
                return;
            }
            queue.enqueue(runnable);
        } finally {
            try {
                assertInvariant();
            }finally {
                monitor.unlock();
            }
        }
    }

    public int getRunningWorkers() {
        monitor.lock();
        try {
            return runningWorkers;
        } finally {
            monitor.unlock();
        }
    }

    private void threadMethod(Runnable runnable) {
        runnable.run();
        while (true) {
            Optional<Runnable> maybeRunnable = getWork();
            maybeRunnable.ifPresent(Runnable::run);
            if (maybeRunnable.isEmpty()) {
                return;
            }
        }
    }

    private Optional<Runnable> getWork() {
        monitor.lock();
        try {
            assertInvariant();
            if (queue.isNotEmpty()) {
                return Optional.of(queue.pull().value);
            } else {
                runningWorkers -= 1;
                return Optional.empty();
            }
        } finally {
            try {
                assertInvariant();
            }finally {
                monitor.unlock();
            }
        }
    }

    private void assertInvariant() {
        assert runningWorkers <= maxWorkers : "max workers was exceeded";
        assert implies(queue.isNotEmpty(), runningWorkers == maxWorkers) : "TODO";
    }

    private boolean implies(boolean p, boolean q) {
        return !p || q;
    }
}
