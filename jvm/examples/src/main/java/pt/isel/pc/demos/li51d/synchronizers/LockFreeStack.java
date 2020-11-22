package pt.isel.pc.demos.li51d.synchronizers;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<T> {

    static class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    private final AtomicReference<Node<T>> head = new AtomicReference<>();

    public void push(T value) {
        Node<T> node = new Node<>(value);
        Node<T> observedHead;
        do {
            observedHead = head.get();
            node.next = observedHead;
        } while (!head.compareAndSet(observedHead, node));
    }

    public Optional<T> pop() {
        Node<T> observedHead = null;
        Node<T> newHead = null;
        do {
            observedHead = head.get();
            if (observedHead == null) {
                return Optional.empty();
            }
            newHead = observedHead.next;
        } while (!head.compareAndSet(observedHead, newHead));
        return Optional.of(observedHead.value);
    }
}
