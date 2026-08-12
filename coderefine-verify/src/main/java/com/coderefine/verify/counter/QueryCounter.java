package com.coderefine.verify.counter;

import java.util.concurrent.atomic.AtomicInteger;

public class QueryCounter {

    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();
    }

    public int getCount() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }
}
