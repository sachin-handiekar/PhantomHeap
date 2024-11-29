package com.sachinhandiekar.phantomheap.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory manager implementation using Java 21+ Foreign Memory API.
 * This implementation provides direct access to off-heap memory using the modern
 * Foreign Memory API, which offers better safety and performance compared to
 * traditional off-heap memory access methods.
 * <p>
 * Key features:
 * <ul>
 *   <li>Uses {@link Arena} for memory management</li>
 *   <li>Thread-safe memory allocation tracking</li>
 *   <li>Automatic memory cleanup when closed</li>
 *   <li>Direct memory access without JNI overhead</li>
 * </ul>
 * </p>
 *
 * @author Sachin Handiekar
 * @version 1.0
 */
public class ForeignMemoryManager implements MemoryManagerStrategy {
    private final Arena arena;
    private final long capacity;
    private final AtomicLong usedMemory;

    /**
     * Creates a new ForeignMemoryManager with the specified capacity.
     * The manager uses a confined arena for better performance and safety.
     *
     * @param capacityBytes Maximum memory capacity in bytes
     */
    public ForeignMemoryManager(long capacityBytes) {
        this.capacity = capacityBytes;
        this.usedMemory = new AtomicLong(0);
        this.arena = Arena.ofConfined(); // Use confined scope for better performance
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses the Foreign Memory API to allocate memory segments.
     * The allocation is tracked to ensure it doesn't exceed the capacity.
     * </p>
     */
    @Override
    public MemoryPointer allocate(int size) {
        if (usedMemory.get() + size > capacity) {
            throw new OutOfMemoryError("Off-heap memory limit exceeded");
        }

        MemorySegment segment = arena.allocate(size); // Alignment is handled automatically
        usedMemory.addAndGet(size);

        return new MemoryPointer(segment, size);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation writes data directly to the memory segment using ByteBuffer.
     * </p>
     *
     * @throws IllegalArgumentException if the pointer's segment is not a {@link MemorySegment}
     */
    @Override
    public void write(MemoryPointer pointer, byte[] data) {
        if (!(pointer.getSegment() instanceof MemorySegment)) {
            throw new IllegalArgumentException("Invalid pointer type");
        }

        MemorySegment segment = (MemorySegment) pointer.getSegment();
        segment.asByteBuffer().put(data);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation reads data directly from the memory segment using ByteBuffer.
     * </p>
     *
     * @throws IllegalArgumentException if the pointer's segment is not a {@link MemorySegment}
     */
    @Override
    public byte[] read(MemoryPointer pointer) {
        if (!(pointer.getSegment() instanceof MemorySegment)) {
            throw new IllegalArgumentException("Invalid pointer type");
        }

        MemorySegment segment = (MemorySegment) pointer.getSegment();
        byte[] data = new byte[pointer.getSize()];
        segment.asByteBuffer().get(data);
        return data;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation updates the used memory counter but does not explicitly
     * free the memory segment, as it will be automatically freed when the arena is closed.
     * </p>
     */
    @Override
    public void free(MemoryPointer pointer) {
        usedMemory.addAndGet(-pointer.getSize());
        // Memory will be automatically freed when arena is closed
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCapacity() {
        return capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUsedMemory() {
        return usedMemory.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the arena, which automatically frees all allocated memory segments.
     * </p>
     */
    @Override
    public void close() {
        arena.close();
    }
}
