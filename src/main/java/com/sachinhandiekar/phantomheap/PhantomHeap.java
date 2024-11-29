package com.sachinhandiekar.phantomheap;

import com.sachinhandiekar.phantomheap.config.PhantomHeapSettings;
import com.sachinhandiekar.phantomheap.eviction.EvictionPolicy;
import com.sachinhandiekar.phantomheap.memory.MemoryUnit;
import com.sachinhandiekar.phantomheap.memory.MemoryManagerStrategy;
import com.sachinhandiekar.phantomheap.memory.MemoryPointer;
import com.sachinhandiekar.phantomheap.metrics.PhantomHeapMetrics;
import io.micrometer.core.instrument.Metrics;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance off-heap object storage for Java applications.
 * 
 * <p>PhantomHeap provides a way to store large objects outside the JVM heap,
 * reducing garbage collection pressure while maintaining fast access times. It supports
 * various memory management strategies and eviction policies to handle different use cases.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Off-heap storage using modern Java memory management APIs</li>
 *   <li>Configurable memory management strategies</li>
 *   <li>Optional automatic eviction of less-used objects</li>
 *   <li>Thread-safe operations</li>
 *   <li>Built-in performance monitoring</li>
 *   <li>Automatic resource cleanup</li>
 * </ul></p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Create with default settings (1GB)
 * try (PhantomHeap heap = PhantomHeap.create()) {
 *     // Store an object
 *     String myData = "Hello, World!";
 *     long id = heap.store(myData);
 *     
 *     // Retrieve it later
 *     String retrieved = heap.retrieve(id);
 *     
 *     // Free when no longer needed
 *     heap.free(id);
 * }
 * 
 * // Or create with custom memory size
 * try (PhantomHeap heap = PhantomHeap.create(Memory.GB(2))) {
 *     // Use the heap...
 * }
 * }</pre></p>
 * 
 * <p>PhantomHeap implements {@link AutoCloseable} to ensure proper cleanup of resources.
 * It's recommended to use try-with-resources or explicitly call {@link #close()} when
 * the heap is no longer needed.</p>
 * 
 * @author Sachin Handiekar
 * @version 1.0
 * @see PhantomHeapSettings
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PhantomHeap implements AutoCloseable {
    @Getter(AccessLevel.PACKAGE)
    private final MemoryManagerStrategy memoryManager;
    
    @Getter(AccessLevel.PACKAGE)
    private final EvictionPolicy evictionPolicy;
    
    private final ScheduledExecutorService cleanupExecutor;
    private final ConcurrentHashMap<Long, MemoryPointer> allocatedBlocks;
    private final AtomicLong nextId;
    private final PhantomHeapMetrics metrics;
    
    private static final Logger logger = LoggerFactory.getLogger(PhantomHeap.class);

    /**
     * Creates a new PhantomHeap instance with default settings.
     * Uses 1GB capacity with LRU eviction and default cleanup settings.
     *
     * @return A new PhantomHeap instance with default configuration
     */
    public static PhantomHeap create() {
        return create(PhantomHeapSettings.builder().build());
    }

    /**
     * Creates a new PhantomHeap instance with the specified memory capacity.
     * Uses default settings for eviction policy and cleanup behavior.
     *
     * @param memoryCapacity Memory capacity in bytes
     * @return A new PhantomHeap instance with the specified capacity
     * @throws IllegalArgumentException if memoryCapacity is less than or equal to 0
     */
    public static PhantomHeap create(long memoryCapacity) {
        return create(PhantomHeapSettings.builder()
                .memoryCapacity(memoryCapacity)
                .build());
    }

    /**
     * Creates a new PhantomHeap instance with custom settings.
     *
     * @param settings Custom settings for the heap
     * @return A new PhantomHeap instance with the specified settings
     * @throws NullPointerException if settings is null
     */
    public static PhantomHeap create(PhantomHeapSettings settings) {
        Objects.requireNonNull(settings, "Settings cannot be null");
        
        // Initialize metrics
        PhantomHeapMetrics metrics = PhantomHeapMetrics.create(Metrics.globalRegistry);
        
        // Initialize cleanup executor
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(settings.getThreadPoolSize());
        
        // Create the heap instance
        PhantomHeap heap = new PhantomHeap(
            settings.getMemoryManager(),
            settings.getEvictionPolicy(),
            executor,
            new ConcurrentHashMap<>(),
            new AtomicLong(1),
            metrics
        );
        
        // Schedule cleanup if needed
        if (settings.getCleanupIntervalMs() > 0) {
            executor.scheduleAtFixedRate(
                heap::cleanup,
                settings.getCleanupIntervalMs(),
                settings.getCleanupIntervalMs(),
                TimeUnit.MILLISECONDS
            );
        }
        
        logger.info("PhantomHeap initialized with capacity: {}", 
            MemoryUnit.formatSize(settings.getMemoryCapacity()));
            
        return heap;
    }

    /**
     * Stores an object in off-heap memory.
     * 
     * <p>The object must be serializable. The returned ID can be used later to
     * retrieve or free the stored object. If the heap is near capacity and an
     * eviction policy is configured, less recently used objects may be evicted
     * to make room for the new object.</p>
     *
     * @param object Object to store, must be serializable
     * @param <T> Type of the object
     * @return ID that can be used to retrieve or free the object
     * @throws IllegalArgumentException if object is null
     * @throws IOException if serialization fails
     * @throws OutOfMemoryError if there's not enough space and eviction cannot free sufficient memory
     */
    public <T extends Serializable> long store(T object) {
        Objects.requireNonNull(object, "Cannot store null object");
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            oos.flush();
            byte[] serialized = baos.toByteArray();
            
            synchronized (this) {
                // Check if we need to evict before allocating
                if (evictionPolicy != null) {
                    long requiredSpace = serialized.length;
                    long currentUsed = memoryManager.getUsedMemory();
                    long capacity = memoryManager.getCapacity();
                    
                    // Pre-emptively evict if adding this object would exceed threshold
                    while ((currentUsed + requiredSpace) > capacity * evictionPolicy.getThreshold()) {
                        if (!evict()) {
                            break;
                        }
                        currentUsed = memoryManager.getUsedMemory();
                    }
                }

                try {
                    MemoryPointer pointer = memoryManager.allocate(serialized.length);
                    memoryManager.write(pointer, serialized);

                    long id = nextId.getAndIncrement();
                    allocatedBlocks.put(id, pointer);

                    if (evictionPolicy != null) {
                        evictionPolicy.recordAccess(id, serialized.length);
                    }

                    metrics.recordStore();
                    return id;
                } catch (OutOfMemoryError e) {
                    // Try one last eviction if we hit OOM
                    if (evictionPolicy != null && evict()) {
                        try {
                            MemoryPointer pointer = memoryManager.allocate(serialized.length);
                            memoryManager.write(pointer, serialized);

                            long id = nextId.getAndIncrement();
                            allocatedBlocks.put(id, pointer);

                            if (evictionPolicy != null) {
                                evictionPolicy.recordAccess(id, serialized.length);
                            }

                            metrics.recordStore();
                            return id;
                        } catch (OutOfMemoryError retryError) {
                            throw e;
                        }
                    }
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * Retrieves a previously stored object.
     * 
     * <p>The object is deserialized and returned. If an eviction policy is configured,
     * accessing the object will update its usage statistics.</p>
     *
     * @param id ID returned by a previous call to {@link #store}
     * @param <T> Expected type of the object
     * @return The stored object, or null if not found
     * @throws ClassCastException if the stored object is not of type T
     * @throws RuntimeException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public <T> T retrieve(long id) {
        synchronized (this) {
            MemoryPointer pointer = allocatedBlocks.get(id);
            if (pointer == null) {
                return null;
            }

            try {
                byte[] data = memoryManager.read(pointer);
                if (evictionPolicy != null) {
                    evictionPolicy.recordAccess(id, data.length);
                }
                
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                    metrics.recordRetrieve();
                    return (T) ois.readObject();
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Failed to deserialize object", e);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to retrieve object", e);
            }
        }
    }

    /**
     * Frees the memory used by a stored object.
     * 
     * <p>After calling this method, the ID will no longer be valid and the object
     * cannot be retrieved. If an eviction policy is configured, the object will
     * also be removed from its tracking.</p>
     *
     * @param id ID of the object to free
     */
    public void free(long id) {
        MemoryPointer pointer = allocatedBlocks.remove(id);
        if (pointer != null) {
            memoryManager.free(pointer);
            if (evictionPolicy != null) {
                evictionPolicy.recordRemoval(id);
            }
        }
    }

    /**
     * Gets the total capacity of the heap in bytes.
     *
     * @return Total capacity in bytes
     */
    public long getCapacity() {
        return memoryManager.getCapacity();
    }

    /**
     * Gets the current amount of used memory in bytes.
     *
     * @return Used memory in bytes
     */
    public long getUsedMemory() {
        return memoryManager.getUsedMemory();
    }

    /**
     * Performs cleanup and eviction if necessary.
     * This method is called automatically by the cleanup scheduler if configured.
     */
    private void cleanup() {
        if (evictionPolicy == null) {
            return;
        }

        long usedMemory = memoryManager.getUsedMemory();
        long totalMemory = memoryManager.getCapacity();

        while (evictionPolicy.shouldEvict(usedMemory, totalMemory)) {
            long idToEvict = evictionPolicy.getNextEviction();
            if (idToEvict == -1) {
                break;
            }
            free(idToEvict);
            usedMemory = memoryManager.getUsedMemory();
        }
        metrics.recordEviction();
    }

    /**
     * Evicts objects according to the eviction policy.
     * This method is called automatically when storing new objects if needed.
     * 
     * @return Whether any objects were evicted
     */
    private boolean evict() {
        if (evictionPolicy == null) {
            return false;
        }

        int maxEvictions = 5; // Increased from 3 to handle rapid operations better
        int evictionCount = 0;
        boolean evictedAny = false;
        long currentUsed = memoryManager.getUsedMemory();
        long capacity = memoryManager.getCapacity();
        
        while (evictionPolicy.shouldEvict(currentUsed, capacity) && evictionCount < maxEvictions) {
            long idToEvict = evictionPolicy.getNextEviction();
            if (idToEvict == -1) {
                break;
            }
            
            MemoryPointer pointer = allocatedBlocks.get(idToEvict);
            if (pointer != null) {
                free(idToEvict);
                evictedAny = true;
                evictionCount++;
                currentUsed = memoryManager.getUsedMemory();
            } else {
                evictionPolicy.recordRemoval(idToEvict);
            }
        }
        
        return evictedAny;
    }

    /**
     * Closes this heap and releases all resources.
     * 
     * <p>This method will:
     * <ul>
     *   <li>Shut down the cleanup executor</li>
     *   <li>Wait for any ongoing cleanup tasks to complete</li>
     *   <li>Close the memory manager</li>
     * </ul></p>
     * 
     * <p>After calling this method, the heap cannot be used anymore.</p>
     *
     * @throws Exception if an error occurs during cleanup
     */
    @Override
    public void close() throws Exception {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        memoryManager.close();
    }
}
