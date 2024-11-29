package com.sachinhandiekar.phantomheap.eviction;

/**
 * Defines the contract for implementing different eviction policies in PhantomHeap.
 * An eviction policy determines which entries should be removed from the heap when memory
 * constraints are reached. Different implementations can provide various strategies such as
 * LRU (Least Recently Used), LFU (Least Frequently Used), or custom algorithms.
 * 
 * <p>Implementations must be thread-safe as methods may be called concurrently from
 * different threads managing the PhantomHeap.
 * 
 * <p>Example usage with LRU policy:
 * <pre>{@code
 * EvictionPolicy policy = new LRUEvictionPolicy(0.75); // Evict at 75% capacity
 * policy.recordAccess(1L, 1024); // Record access to entry with ID 1
 * if (policy.shouldEvict(currentMemory, totalMemory)) {
 *     long idToEvict = policy.getNextEviction();
 *     // Evict the entry...
 *     policy.recordRemoval(idToEvict);
 * }
 * }</pre>
 */
public interface EvictionPolicy {
    /**
     * Records an access to an entry in the heap. This method should be called whenever
     * an entry is accessed (read or written) to update the eviction statistics.
     * 
     * <p>Implementations may use this information to maintain access patterns,
     * update timestamps, or adjust internal data structures used for eviction decisions.
     *
     * @param id The unique identifier of the accessed entry
     * @param size The size of the entry in bytes. This information may be used by
     *            implementations to make size-aware eviction decisions
     */
    void recordAccess(long id, int size);
    
    /**
     * Records the removal of an entry from the heap. This method should be called when
     * an entry is explicitly removed or after it has been evicted.
     * 
     * <p>Implementations should use this to clean up any internal state or statistics
     * maintained for the removed entry.
     *
     * @param id The unique identifier of the removed entry
     */
    void recordRemoval(long id);
    
    /**
     * Determines the next entry that should be evicted from the heap based on the
     * policy's algorithm and current state.
     * 
     * <p>This method should not modify any internal state. The actual eviction should
     * only be recorded when {@link #recordRemoval(long)} is called.
     *
     * @return The ID of the entry that should be evicted next, or -1 if no entry
     *         should be evicted (e.g., if the heap is empty)
     */
    long getNextEviction();
    
    /**
     * Determines whether eviction should occur based on current memory usage metrics.
     * This method helps implement memory pressure-based eviction strategies.
     * 
     * <p>Implementations can use the memory metrics to determine if the heap has reached
     * a threshold where eviction should begin. This might be based on a simple ratio or
     * more complex heuristics.
     *
     * @param usedMemory The current amount of memory used by entries in the heap, in bytes
     * @param totalMemory The total memory capacity allocated to the heap, in bytes
     * @return {@code true} if eviction should occur based on current memory usage,
     *         {@code false} otherwise
     */
    boolean shouldEvict(long usedMemory, long totalMemory);
    
    /**
     * Gets the memory threshold at which eviction should occur.
     * This is typically a value between 0 and 1, representing the
     * ratio of used memory to total memory that triggers eviction.
     *
     * @return The eviction threshold
     */
    double getThreshold();
}
