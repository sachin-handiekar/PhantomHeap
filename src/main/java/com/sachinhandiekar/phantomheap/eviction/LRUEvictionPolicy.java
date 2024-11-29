package com.sachinhandiekar.phantomheap.eviction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An implementation of {@link EvictionPolicy} that uses the Least Recently Used (LRU)
 * algorithm to determine which entries should be evicted from the heap.
 */
public class LRUEvictionPolicy implements EvictionPolicy {
    private final Map<Long, Integer> entrySizes;
    private final ReentrantReadWriteLock lock;
    private final LinkedHashMap<Long, Long> accessOrder;
    private final double evictionThreshold;
    
    public LRUEvictionPolicy(double evictionThreshold) {
        if (evictionThreshold <= 0 || evictionThreshold >= 1) {
            throw new IllegalArgumentException("Eviction threshold must be between 0 and 1");
        }
        this.evictionThreshold = evictionThreshold;
        this.entrySizes = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
    }
    
    @Override
    public void recordAccess(long id, int size) {
        lock.writeLock().lock();
        try {
            entrySizes.put(id, size);
            accessOrder.remove(id); // Remove and re-add to update position
            accessOrder.put(id, System.nanoTime());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void recordRemoval(long id) {
        lock.writeLock().lock();
        try {
            entrySizes.remove(id);
            accessOrder.remove(id);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public long getNextEviction() {
        lock.readLock().lock();
        try {
            if (accessOrder.isEmpty()) {
                return -1;
            }
            // Get the least recently used entry
            return accessOrder.keySet().iterator().next();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean shouldEvict(long usedMemory, long totalMemory) {
        if (totalMemory <= 0) {
            return false;
        }
        
        double usageRatio = (double) usedMemory / totalMemory;
        if (usageRatio < evictionThreshold) {
            return false;
        }

        // If we're over the threshold, we should evict regardless of whether
        // there are entries in the access order, as this indicates we need
        // to free up memory
        return true;
    }
    
    @Override
    public double getThreshold() {
        return evictionThreshold;
    }
}
