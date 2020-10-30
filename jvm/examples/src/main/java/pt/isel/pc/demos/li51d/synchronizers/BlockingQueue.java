package pt.isel.pc.demos.li51d.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingQueue<E> {

    static class SendRequest<E> {
        boolean isDone = false;
        final Condition condition;
        final E elementToSend;

        SendRequest(Condition condition, E elementToSend) {
            this.condition = condition;
            this.elementToSend = elementToSend;
        }
    }

    static class ReceiveRequest<E> {
        boolean isDone = false;
        final Condition condition;
        E receivedElement = null;

        ReceiveRequest(Condition condition) {
            this.condition = condition;
        }
    }

    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<SendRequest<E>> sendQueue = new NodeLinkedList<>();
    private final NodeLinkedList<ReceiveRequest<E>> receiveQueue = new NodeLinkedList<>();

    public boolean enqueue(E element, long timeout, TimeUnit timeUnit) throws InterruptedException {
        monitor.lock();
        try {

            // fast path
            if (receiveQueue.isNotEmpty()) {
                NodeLinkedList.Node<ReceiveRequest<E>> receiver = receiveQueue.pull();
                receiver.value.receivedElement = element;
                receiver.value.isDone = true;
                receiver.value.condition.signal();
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }

            // wait path
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<SendRequest<E>> node =
                    sendQueue.enqueue(new SendRequest<>(monitor.newCondition(), element));
            while (true) {
                try {
                    node.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.isDone) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    sendQueue.remove(node);
                    throw e;
                }
                if (node.value.isDone) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    sendQueue.remove(node);
                    return false;
                }
            }

        } finally {
            monitor.unlock();
        }

    }

    public Optional<E> dequeue(long timeout, TimeUnit timeUnit) throws InterruptedException {

        monitor.lock();
        try {

            // fast path
            if (sendQueue.isNotEmpty()) {
                NodeLinkedList.Node<SendRequest<E>> sender = sendQueue.pull();
                sender.value.isDone = true;
                sender.value.condition.signal();
                return Optional.of(sender.value.elementToSend);
            }
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            // wait path
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<ReceiveRequest<E>> node =
                    receiveQueue.enqueue(new ReceiveRequest<>(monitor.newCondition()));
            while (true) {
                try {
                    node.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.isDone) {
                        Thread.currentThread().interrupt();
                        return Optional.of(node.value.receivedElement);
                    }
                    receiveQueue.remove(node);
                    throw e;
                }
                if (node.value.isDone) {
                    return Optional.of(node.value.receivedElement);
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    receiveQueue.remove(node);
                    return Optional.empty();
                }
            }

        } finally {
            monitor.unlock();
        }
    }
}
