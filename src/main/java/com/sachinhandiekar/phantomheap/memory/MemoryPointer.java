package com.sachinhandiekar.phantomheap.memory;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Represents a pointer to an allocated memory block in off-heap storage.
 * This class encapsulates both the memory segment (which can be a {@link MemorySegment} for foreign memory
 * or other types for different memory implementations) and its size.
 * <p>
 * The class provides proper implementations of equals, hashCode, and toString to handle
 * both {@link MemorySegment} and other segment types consistently.
 * </p>
 *
 * @author Sachin Handiekar
 * @version 1.0
 */
public class MemoryPointer {
    private final Object segment;
    private final int size;
    
    /**
     * Creates a new memory pointer with the specified segment and size.
     *
     * @param segment The memory segment object (can be {@link MemorySegment} or other types)
     * @param size The size of the memory block in bytes
     */
    public MemoryPointer(Object segment, int size) {
        this.segment = segment;
        this.size = size;
    }
    
    /**
     * Gets the memory segment associated with this pointer.
     *
     * @return The memory segment object (can be {@link MemorySegment} or other types)
     */
    public Object getSegment() {
        return segment;
    }
    
    /**
     * Gets the size of the memory block.
     *
     * @return The size in bytes
     */
    public int getSize() {
        return size;
    }

    /**
     * Compares this memory pointer to another object for equality.
     * For {@link MemorySegment} types, compares their memory addresses.
     * For other types, uses standard object equality.
     *
     * @param o The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryPointer that = (MemoryPointer) o;
        
        // For MemorySegment, compare their addresses
        if (segment instanceof MemorySegment && that.segment instanceof MemorySegment) {
            return ((MemorySegment) segment).address() == ((MemorySegment) that.segment).address() 
                && size == that.size;
        }
        
        // For other types (like Long in tests), compare directly
        return Objects.equals(segment, that.segment) && size == that.size;
    }

    /**
     * Generates a hash code for this memory pointer.
     * For {@link MemorySegment} types, uses the memory address in the hash calculation.
     * For other types, uses standard object hash code.
     *
     * @return A hash code value for this object
     */
    @Override
    public int hashCode() {
        // For MemorySegment, use the address for hash code
        if (segment instanceof MemorySegment) {
            return Objects.hash(((MemorySegment) segment).address(), size);
        }
        return Objects.hash(segment, size);
    }

    /**
     * Returns a string representation of this memory pointer.
     * For {@link MemorySegment} types, formats the memory address as a hexadecimal string.
     * For other types, uses their standard string representation.
     *
     * @return A string representation of this memory pointer
     */
    @Override
    public String toString() {
        String segmentStr;
        if (segment instanceof MemorySegment) {
            segmentStr = "0x" + Long.toHexString(((MemorySegment) segment).address());
        } else {
            segmentStr = segment.toString();
        }
        return "MemoryPointer{segment=" + segmentStr + ", size=" + size + "}";
    }
}
