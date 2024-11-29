package com.sachinhandiekar.phantomheap.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemoryUnitPointerTest {

    @Test
    void constructor_ShouldInitializeFields() {
        // Arrange
        long address = 1234L;
        int size = 100;

        // Act
        MemoryPointer pointer = new MemoryPointer(address, size);

        // Assert
        assertEquals(address, pointer.getSegment());
        assertEquals(size, pointer.getSize());
    }

    @Test
    void equals_ShouldReturnTrue_WhenSameAddressAndSize() {
        // Arrange
        MemoryPointer pointer1 = new MemoryPointer(1234L, 100);
        MemoryPointer pointer2 = new MemoryPointer(1234L, 100);

        // Act & Assert
        assertEquals(pointer1, pointer2);
        assertEquals(pointer1.hashCode(), pointer2.hashCode());
    }

    @Test
    void equals_ShouldReturnFalse_WhenDifferentAddress() {
        // Arrange
        MemoryPointer pointer1 = new MemoryPointer(1234L, 100);
        MemoryPointer pointer2 = new MemoryPointer(5678L, 100);

        // Act & Assert
        assertNotEquals(pointer1, pointer2);
    }

    @Test
    void equals_ShouldReturnFalse_WhenDifferentSize() {
        // Arrange
        MemoryPointer pointer1 = new MemoryPointer(1234L, 100);
        MemoryPointer pointer2 = new MemoryPointer(1234L, 200);

        // Act & Assert
        assertNotEquals(pointer1, pointer2);
    }

    @Test
    void equals_ShouldReturnFalse_WhenComparedToNull() {
        // Arrange
        MemoryPointer pointer = new MemoryPointer(1234L, 100);

        // Act & Assert
        assertNotEquals(null, pointer);
    }

    @Test
    void equals_ShouldReturnFalse_WhenComparedToDifferentClass() {
        // Arrange
        MemoryPointer pointer = new MemoryPointer(1234L, 100);
        Object other = new Object();

        // Act & Assert
        assertNotEquals(other, pointer);
    }

    @Test
    void toString_ShouldContainAddressAndSize() {
        // Arrange
        MemoryPointer pointer = new MemoryPointer(1234L, 100);

        // Act
        String result = pointer.toString();

        // Assert
        assertTrue(result.contains("1234"));
        assertTrue(result.contains("100"));
    }

    @Test
    void constructor_ShouldAcceptZeroAddress() {
        // Arrange & Act
        MemoryPointer pointer = new MemoryPointer(0L, 100);

        // Assert
        assertEquals(0L, pointer.getSegment());
        assertEquals(100, pointer.getSize());
    }

    @Test
    void constructor_ShouldAcceptZeroSize() {
        // Arrange & Act
        MemoryPointer pointer = new MemoryPointer(1234L, 0);

        // Assert
        assertEquals(1234L, pointer.getSegment());
        assertEquals(0, pointer.getSize());
    }
}
