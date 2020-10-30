package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingQueue<E> {

    static class EnqueueRequest<E> {
        boolean isDone = false;
        final Condition condition;
        final E elementToSend;

        EnqueueRequest(E elementToSend, Condition condition) {

            this.elementToSend = elementToSend;
            this.condition = condition;
        }
    }

    static class DequeueRequest<E> {
        boolean isDone = false;
        final Condition condition;
        E receivedElement = null;

        DequeueRequest(Condition condition) {

            this.condition = condition;
        }
    }

    private final NodeLinkedList<EnqueueRequest<E>> senders = new NodeLinkedList<>();
    private final NodeLinkedList<DequeueRequest<E>> receivers = new NodeLinkedList<>();
    private final Lock monitor = new ReentrantLock();

    public boolean enqueue(E element, long timeout, TimeUnit timeUnit) throws InterruptedException {
        monitor.lock();
        try {
            // fast path
            if (receivers.isNotEmpty()) {
                NodeLinkedList.Node<DequeueRequest<E>> receiver = receivers.pull();
                receiver.value.receivedElement = element;
                receiver.value.isDone = true;
                receiver.value.condition.signal();
                return true;
            }
            // wait path
            if (Timeouts.noWait(timeout)) {
                return false;
            }

            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<EnqueueRequest<E>> node = senders.enqueue(
                    new EnqueueRequest<>(element, monitor.newCondition()));
            while (true) {
                try {
                    node.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.isDone) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    senders.remove(node);
                    throw e;
                }
                // evaluate success condition
                if (node.value.isDone) {
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    senders.remove(node);
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
            if (senders.isNotEmpty()) {
                NodeLinkedList.Node<EnqueueRequest<E>> sender = senders.pull();
                sender.value.isDone = true;
                sender.value.condition.signal();
                return Optional.of(sender.value.elementToSend);
            }
            // wait path
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            NodeLinkedList.Node<DequeueRequest<E>> node = receivers.enqueue(
                    new DequeueRequest<>(monitor.newCondition()));
            while (true) {
                try {
                    node.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.isDone) {
                        Thread.currentThread().interrupt();
                        return Optional.of(node.value.receivedElement);
                    }
                    receivers.remove(node);
                    throw e;
                }
                // evaluate success condition
                if (node.value.isDone) {
                    return Optional.of(node.value.receivedElement);
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    receivers.remove(node);
                    return Optional.empty();
                }
            }
        } finally {
            monitor.unlock();
        }
    }
}
