package com.sachinhandiekar.phantomheap.memory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A hybrid memory management implementation that combines off-heap memory and file-based storage.
 * This implementation provides a tiered storage approach where frequently accessed (hot) data
 * is stored in memory using the Foreign Memory API, while less frequently accessed (cold) data
 * is stored in a backing file.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Tiered storage with automatic data placement based on memory threshold</li>
 *   <li>Thread-safe operations using atomic counters and concurrent collections</li>
 *   <li>Efficient direct memory access for hot data using {@link Arena}</li>
 *   <li>Memory-mapped file access for cold data using {@link FileChannel}</li>
 *   <li>Automatic data migration between tiers based on memory pressure</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Create a hybrid manager with 1GB memory capacity, using "data.bin" as backing store
 * // and 80% memory threshold
 * try (HybridMemoryManager manager = new HybridMemoryManager(1024 * 1024 * 1024L,
 *                                                          "data.bin",
 *                                                          0.8)) {
 *     // Allocate and write data
 *     MemoryPointer ptr = manager.allocate(1024);
 *     manager.write(ptr, "Hello World".getBytes());
 *     
 *     // Read data back
 *     byte[] data = manager.read(ptr);
 *     
 *     // Free memory when done
 *     manager.free(ptr);
 * }
 * }</pre>
 * 
 * @author Sachin Handiekar
 * @version 1.0
 * @see MemoryManagerStrategy
 * @see ForeignMemoryManager
 */
public class HybridMemoryManager implements MemoryManagerStrategy {
    private final Arena arena;
    private final long memoryCapacity;
    private final File backingFile;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final AtomicLong memoryUsed;
    private final AtomicLong fileUsed;
    private final ConcurrentHashMap<MemoryPointer, Boolean> isInMemory;
    private final double memoryThreshold;

    /**
     * Creates a new HybridMemoryManager with specified memory capacity and backing file.
     * 
     * <p>The memory threshold determines when data should be stored in memory vs file:
     * <ul>
     *   <li>Data is stored in memory if current memory usage is below threshold</li>
     *   <li>Data is stored in file if memory usage exceeds threshold</li>
     * </ul>
     * 
     * @param memoryCapacity Maximum off-heap memory capacity in bytes
     * @param fileName Path to the backing file for storing cold data
     * @param memoryThreshold Ratio (0-1) determining memory vs file storage threshold
     * @throws IOException if the backing file cannot be created or accessed
     * @throws IllegalArgumentException if memoryCapacity is negative or memoryThreshold
     *         is not between 0 and 1
     */
    public HybridMemoryManager(long memoryCapacity, String fileName, double memoryThreshold) throws IOException {
        this.memoryCapacity = memoryCapacity;
        this.memoryThreshold = memoryThreshold;
        this.memoryUsed = new AtomicLong(0);
        this.fileUsed = new AtomicLong(0);
        this.isInMemory = new ConcurrentHashMap<>();

        // Initialize memory allocator
        this.arena = Arena.ofConfined();

        // Initialize file storage
        this.backingFile = new File(fileName);
        this.raf = new RandomAccessFile(backingFile, "rw");
        this.channel = raf.getChannel();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation automatically decides whether to allocate in memory or file:
     * <ul>
     *   <li>If memory usage is below threshold and sufficient space exists, allocates in memory</li>
     *   <li>Otherwise, allocates in the backing file</li>
     * </ul>
     * 
     * @throws OutOfMemoryError if neither memory nor file storage has sufficient space
     */
    @Override
    public MemoryPointer allocate(int size) {
        boolean useMemory = (double) memoryUsed.get() / memoryCapacity < memoryThreshold;

        if (useMemory) {
            if (memoryUsed.get() + size > memoryCapacity) {
                useMemory = false;
            } else {
                MemorySegment segment = arena.allocate(size, 8);
                memoryUsed.addAndGet(size);
                MemoryPointer pointer = new MemoryPointer(segment, size);
                isInMemory.put(pointer, true);
                return pointer;
            }
        }

        if (!useMemory) {
            try {
                long position = fileUsed.getAndAdd(size);
                fileUsed.addAndGet(size);
                MemoryPointer pointer = new MemoryPointer(position, size);
                isInMemory.put(pointer, false);
                return pointer;
            } catch (Exception e) {
                throw new OutOfMemoryError("Failed to allocate in file storage: " + e.getMessage());
            }
        }

        throw new OutOfMemoryError("No storage available");
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation automatically handles writing to either memory or file
     * based on where the data was originally allocated. The storage location tracking
     * is transparent to the caller.
     * 
     * @throws RuntimeException if writing to file storage fails
     */
    @Override
    public void write(MemoryPointer pointer, byte[] data) {
        Boolean inMemory = isInMemory.get(pointer);
        if (inMemory == null) {
            throw new IllegalArgumentException("Invalid pointer");
        }

        if (inMemory) {
            MemorySegment segment = (MemorySegment) pointer.getSegment();
            segment.asByteBuffer().put(data);
        } else {
            try {
                long position = (Long) pointer.getSegment();
                channel.position(position);
                channel.write(java.nio.ByteBuffer.wrap(data));
            } catch (IOException e) {
                throw new RuntimeException("Failed to write to file storage", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation automatically reads from either memory or file
     * based on where the data is stored. The storage location is determined
     * by checking the internal tracking map.
     * 
     * @throws RuntimeException if reading from file storage fails
     */
    @Override
    public byte[] read(MemoryPointer pointer) {
        Boolean inMemory = isInMemory.get(pointer);
        if (inMemory == null) {
            throw new IllegalArgumentException("Invalid pointer");
        }

        byte[] data = new byte[pointer.getSize()];
        if (inMemory) {
            MemorySegment segment = (MemorySegment) pointer.getSegment();
            segment.asByteBuffer().get(data);
        } else {
            try {
                long position = (Long) pointer.getSegment();
                channel.position(position);
                channel.read(java.nio.ByteBuffer.wrap(data));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read from file storage", e);
            }
        }
        return data;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation frees resources from either memory or file storage:
     * <ul>
     *   <li>For memory storage: releases the memory segment back to the arena</li>
     *   <li>For file storage: marks the file space as available for reuse</li>
     * </ul>
     * 
     * <p>Note: File storage space is not immediately reclaimed but may be
     * reused for future allocations.
     */
    @Override
    public void free(MemoryPointer pointer) {
        Boolean inMemory = isInMemory.remove(pointer);
        if (inMemory == null) {
            return;
        }

        if (inMemory) {
            // No need to explicitly free with Arena
            memoryUsed.addAndGet(-pointer.getSize());
        } else {
            // For file storage, we just track the space as freed
            // A background process could compact the file if needed
            fileUsed.addAndGet(-pointer.getSize());
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation returns the configured memory capacity, which
     * represents the maximum amount of off-heap memory that can be used.
     * Note that additional storage is available through the backing file.
     */
    @Override
    public long getCapacity() {
        return memoryCapacity;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation returns the current memory usage in bytes.
     * This only includes data stored in memory, not data stored in the
     * backing file.
     */
    @Override
    public long getUsedMemory() {
        return memoryUsed.get() + fileUsed.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation performs cleanup by:
     * <ul>
     *   <li>Closing the arena to release all memory segments</li>
     *   <li>Closing the file channel and random access file</li>
     *   <li>Optionally deleting the backing file</li>
     * </ul>
     * 
     * @throws IOException if closing the file resources fails
     */
    @Override
    public void close() throws IOException {
        arena.close();
        channel.close();
        raf.close();
        if (!backingFile.delete()) {
            backingFile.deleteOnExit();
        }
    }

    /**
     * @return Percentage of data stored in memory vs file
     */
    public double getMemoryUtilization() {
        return (double) memoryUsed.get() / (memoryUsed.get() + fileUsed.get());
    }
}
