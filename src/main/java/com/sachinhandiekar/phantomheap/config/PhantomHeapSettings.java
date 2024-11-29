package com.sachinhandiekar.phantomheap.config;

import com.sachinhandiekar.phantomheap.eviction.EvictionPolicy;
import com.sachinhandiekar.phantomheap.eviction.LRUEvictionPolicy;
import com.sachinhandiekar.phantomheap.memory.MemoryUnit;
import com.sachinhandiekar.phantomheap.memory.MemoryManagerStrategy;
import com.sachinhandiekar.phantomheap.memory.ForeignMemoryManager;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Configuration settings for PhantomHeap.
 * 
 * <p>This class provides convenient methods for configuring various aspects of a PhantomHeap
 * instance. All settings have reasonable defaults, so you only need to specify the settings
 * you want to customize.</p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * PhantomHeapSettings settings = PhantomHeapSettings.builder()
 *     .memoryCapacity(Memory.GB(2))
 *     .threadPoolSize(4)
 *     .cleanupIntervalSeconds(60)
 *     .build();
 * }</pre></p>
 */
@Getter
@Builder(access = AccessLevel.PUBLIC)
public class PhantomHeapSettings {
    /**
     * Default memory capacity (1GB)
     */
    public static final long DEFAULT_MEMORY_CAPACITY = MemoryUnit.GB(1);

    /**
     * Default thread pool size for background operations
     */
    public static final int DEFAULT_THREAD_POOL_SIZE = 1;

    /**
     * Default cleanup interval (60 seconds)
     */
    public static final long DEFAULT_CLEANUP_INTERVAL_MS = 60000;

    /**
     * Default eviction threshold (75%)
     */
    public static final double DEFAULT_EVICTION_THRESHOLD = 0.75;

    /**
     * Memory capacity in bytes
     */
    @Builder.Default
    private final long memoryCapacity = DEFAULT_MEMORY_CAPACITY;

    /**
     * Thread pool size for background operations
     */
    @Builder.Default
    private final int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;

    /**
     * Cleanup interval in milliseconds
     */
    @Builder.Default
    private final long cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;

    /**
     * Memory usage threshold for triggering eviction (0-1)
     */
    @Builder.Default
    private final double evictionThreshold = DEFAULT_EVICTION_THRESHOLD;

    /**
     * Eviction policy for memory management
     */
    @Builder.Default
    @NonNull
    private final EvictionPolicy evictionPolicy = new LRUEvictionPolicy(DEFAULT_EVICTION_THRESHOLD);

    /**
     * Memory manager strategy
     */
    @Builder.Default
    @NonNull
    private final MemoryManagerStrategy memoryManager = new ForeignMemoryManager(DEFAULT_MEMORY_CAPACITY);
}
