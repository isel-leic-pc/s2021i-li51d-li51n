package pt.isel.pc.demos.li51n.synchronizers;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeQueue<E> {

    private static class Node<E> {
        final E value;
        final AtomicReference<Node<E>> next = new AtomicReference<>();

        Node(E value) {
            this.value = value;
        }
    }

    private final AtomicReference<Node<E>> tail = new AtomicReference<>();
    private final AtomicReference<Node<E>> head = new AtomicReference<>();

    public LockFreeQueue() {
        Node<E> dummy = new Node<>(null);
        tail.set(dummy);
        head.set(dummy);
    }

    public void enqueue(E value) {
        Node<E> node = new Node<>(value);
        Node<E> observedTail;
        Node<E> observedNext;
        while(true) {
            observedTail = tail.get();
            observedNext = observedTail.next.get();
            if (observedNext != null) {
                tail.compareAndSet(observedTail, observedNext);
                // retry
            } else {
                if(/*1*/observedTail.next.compareAndSet(null, node)) {

                    /*2*/tail.compareAndSet(observedTail, node);
                    return;
                }
                // retry
            }
        }

    }

}
