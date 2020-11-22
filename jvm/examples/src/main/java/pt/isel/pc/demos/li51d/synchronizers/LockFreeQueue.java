package pt.isel.pc.demos.li51d.synchronizers;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeQueue<E> {

    static class Node<E> {
        final E value;
        final AtomicReference<Node<E>> next;
        Node(E value) {
            this.value = value;
            next = new AtomicReference<>();
        }
    }

    final AtomicReference<Node<E>> head = new AtomicReference<>();
    final AtomicReference<Node<E>> tail = new AtomicReference<>();

    public LockFreeQueue() {
        Node<E> dummy = new Node<>(null);
        head.set(dummy);
        tail.set(dummy);
    }

    public void enqueue(E value) {
        Node<E> node = new Node<>(value);
        while(true) {
            Node<E> observedTail = tail.get();
            Node<E> observedNext = observedTail.next.get();
            // <----
            if(observedTail == tail.get()) { // <----
                // <----
                if (observedNext != null) {
                    tail.compareAndSet(observedTail, observedNext);
                } else {
                    if (observedTail.next.compareAndSet(null, node)) {
                        tail.compareAndSet(observedTail, node);
                        return;
                    }
                }
            }
        }

    }

}
