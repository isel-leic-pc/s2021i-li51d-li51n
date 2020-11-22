package pt.isel.pc.demos.li51n.synchronizers;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<E> {

    private static class Node<E> {
        final E value;
        Node<E> next;

        Node(E value) {
            this.value = value;
        }
    }

    private final AtomicReference<Node<E>> head = new AtomicReference<>(null);

    public void push(E value) {
        Node<E> node = new Node<>(value);
        Node<E> observedHead;
        do {
            observedHead = head.get();
            node.next = observedHead;
        } while (!head.compareAndSet(observedHead, node));
    }

    public Optional<E> pop() {
        Node<E> observedHead;
        do {
            observedHead = head.get();
            if (observedHead == null) {
                return Optional.empty();
            }
        } while (!head.compareAndSet(observedHead, observedHead.next));
        return Optional.of(observedHead.value);
    }
}
