package pt.isel.pc.demos.li51n.synchronizers;

import java.util.concurrent.atomic.AtomicReference;

public class Range {

    public static Range GLOBAL;

    static class Holder {
        int max;
        int min;
    }

    private final AtomicReference<Holder> holder;

    public Range(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("TODO");
        }
        Holder firstHolder = new Holder();
        firstHolder.max = max;
        firstHolder.min = min;
        holder = new AtomicReference<>(firstHolder);
    }

    // invariant: min <= max

    // no synchronizes-with
    void setMin(int newMin) {
        Holder observedHolder;
        Holder newHolder = new Holder();
        newHolder.min = newMin;
        do {
            observedHolder = holder.get();
            int observedMax = observedHolder.max;
            if (newMin > observedMax) {
                throw new IllegalArgumentException("TODO");
            }
            newHolder.max = observedMax;
        } while (!holder.compareAndSet(observedHolder, newHolder));
    }

    void setMax(int newMax) {

    }
}
