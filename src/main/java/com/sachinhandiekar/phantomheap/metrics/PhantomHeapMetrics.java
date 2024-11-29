package com.sachinhandiekar.phantomheap.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;

/**
 * Metrics collector for PhantomHeap operations.
 * This class encapsulates all metrics-related functionality, including counters
 * for operations and timers for latency measurements.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter(AccessLevel.PACKAGE)
public class PhantomHeapMetrics {
    private static final String METRIC_PREFIX = "phantomheap";
    private static final String OPERATIONS_METRIC = METRIC_PREFIX + ".operations";
    private static final String LATENCY_METRIC = METRIC_PREFIX + ".latency";

    private final Counter storeOperations;
    private final Counter retrieveOperations;
    private final Counter evictionCount;
    private final Timer storeLatency;
    private final Timer retrieveLatency;

    /**
     * Creates a new metrics collector using the provided registry.
     *
     * @param registry The meter registry to use for metrics collection
     * @return A new PhantomHeapMetrics instance
     */
    public static PhantomHeapMetrics create(MeterRegistry registry) {
        Counter storeOps = registry.counter(OPERATIONS_METRIC, "type", "store");
        Counter retrieveOps = registry.counter(OPERATIONS_METRIC, "type", "retrieve");
        Counter evictionCnt = registry.counter(METRIC_PREFIX + ".evictions");
        Timer storeLat = registry.timer(LATENCY_METRIC, "operation", "store");
        Timer retrieveLat = registry.timer(LATENCY_METRIC, "operation", "retrieve");

        return new PhantomHeapMetrics(
            storeOps,
            retrieveOps,
            evictionCnt,
            storeLat,
            retrieveLat
        );
    }

    /**
     * Records a store operation.
     */
    public void recordStore() {
        storeOperations.increment();
    }

    /**
     * Records a retrieve operation.
     */
    public void recordRetrieve() {
        retrieveOperations.increment();
    }

    /**
     * Records an eviction operation.
     */
    public void recordEviction() {
        evictionCount.increment();
    }

    /**
     * Records the latency of a store operation.
     *
     * @param nanos The duration of the operation in nanoseconds
     */
    public void recordStoreLatency(long nanos) {
        storeLatency.record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Records the latency of a retrieve operation.
     *
     * @param nanos The duration of the operation in nanoseconds
     */
    public void recordRetrieveLatency(long nanos) {
        retrieveLatency.record(nanos, TimeUnit.NANOSECONDS);
    }
}
