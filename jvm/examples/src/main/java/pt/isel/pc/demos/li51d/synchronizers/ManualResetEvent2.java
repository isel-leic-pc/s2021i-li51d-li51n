package pt.isel.pc.demos.li51d.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * "Delegação de execução"
 * "Kernel-style"
 */
public class ManualResetEvent2 {

    static class Request {
        boolean isDone = false;
        final Condition condition;

        Request(Condition condition) {
            this.condition = condition;
        }
    }

    private boolean flag = false;
    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    // puts the event in the set state (i.e. flag == true)
    // and frees all waiting threads
    public void set() {
        monitor.lock();
        try {
            flag = true;
            while(queue.isNotEmpty()) {
                NodeLinkedList.Node<Request> node = queue.pull();
                node.value.isDone = true;
                node.value.condition.signal();
            }
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
            // wait-path
            long deadline = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(deadline);
            Condition threadCondition = monitor.newCondition();
            NodeLinkedList.Node<Request> localNode = queue.enqueue(
                    new Request(threadCondition));
            while (true) {
                try {
                    threadCondition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    queue.remove(localNode);
                    throw e;
                }
                // exit condition uses the thread's request
                if (localNode.value.isDone) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(localNode);
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