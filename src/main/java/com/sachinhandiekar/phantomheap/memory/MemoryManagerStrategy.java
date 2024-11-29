package com.sachinhandiekar.phantomheap.memory;

/**
 * Strategy interface defining memory management operations for off-heap storage.
 * This interface provides a consistent API for different memory management implementations,
 * such as Foreign Memory API or other off-heap solutions.
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *   <li>Allocating memory blocks of specified sizes</li>
 *   <li>Reading and writing data to allocated memory</li>
 *   <li>Freeing allocated memory when no longer needed</li>
 *   <li>Tracking memory usage and capacity</li>
 * </ul>
 * </p>
 *
 * @author Sachin Handiekar
 * @version 1.0
 * @see ForeignMemoryManager
 * @see HybridMemoryManager
 */
public interface MemoryManagerStrategy extends AutoCloseable {
    /**
     * Allocates a block of memory with the specified size.
     * The allocated memory block is tracked by the memory manager and must be freed
     * when no longer needed to prevent memory leaks.
     *
     * @param size Size in bytes to allocate
     * @return A {@link MemoryPointer} representing the allocated memory block
     * @throws OutOfMemoryError if the allocation fails due to insufficient memory
     */
    MemoryPointer allocate(int size);
    
    /**
     * Writes data to a specified memory location.
     * The data is written to the memory block referenced by the provided pointer.
     * The pointer must be valid and obtained from a previous call to {@link #allocate}.
     *
     * @param pointer Memory location to write to, must be a valid pointer
     * @param data Data to write to the memory location
     * @throws IllegalArgumentException if the pointer is invalid or the data size exceeds the allocated block
     */
    void write(MemoryPointer pointer, byte[] data);
    
    /**
     * Reads data from a specified memory location.
     * The data is read from the memory block referenced by the provided pointer.
     * The pointer must be valid and obtained from a previous call to {@link #allocate}.
     *
     * @param pointer Memory location to read from, must be a valid pointer
     * @return Data read from the memory location as a byte array
     * @throws IllegalArgumentException if the pointer is invalid
     */
    byte[] read(MemoryPointer pointer);
    
    /**
     * Frees an allocated memory block.
     * The memory block referenced by the pointer is released back to the system.
     * After calling this method, the pointer becomes invalid and must not be used.
     *
     * @param pointer Memory block to free, must be a valid pointer
     * @throws IllegalArgumentException if the pointer is invalid
     */
    void free(MemoryPointer pointer);
    
    /**
     * Gets the total memory capacity managed by this memory manager.
     * This represents the maximum amount of memory that can be allocated.
     *
     * @return Total capacity in bytes
     */
    long getCapacity();
    
    /**
     * Gets the current amount of memory in use.
     * This represents the total size of all allocated memory blocks that have not been freed.
     *
     * @return Currently used memory in bytes
     */
    long getUsedMemory();
}
